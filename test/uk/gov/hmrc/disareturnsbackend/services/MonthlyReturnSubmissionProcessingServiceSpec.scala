/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.disareturnsbackend.services

import base.SpecBase
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{doAnswer, never, verify, verifyNoInteractions, when}
import org.mockito.invocation.InvocationOnMock
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.json.Json
import uk.gov.hmrc.disareturnsbackend.connectors.{ObjectStoreConnector, ReturnsSubmissionConnector}
import uk.gov.hmrc.disareturnsbackend.converters.{MonthlyReturnCsvConverter, MonthlyReturnCsvConverterImpl, MonthlyReturnNdjsonWriterImpl, MonthlyReturnXlsxConverter}
import uk.gov.hmrc.disareturnsbackend.models.*
import uk.gov.hmrc.disareturnsbackend.utils.Constants.XLSX_MIME_TYPE

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.concurrent.{Future, Promise}

class MonthlyReturnSubmissionProcessingServiceSpec extends SpecBase {

  private val item = MonthlyReturnSubmissionWorkItem(testZReference, testTaxYear, testMonth, testUploadReference)

  private val csv =
    "Account Number,National Insurance Number,First Name,Middle Name,Surname,Date of Birth,ISA Type being reported,Flexible ISA,Total current year subscriptions transferred in,Total current year subscriptions transferred out,Date of first subscription event,Date of last subscription event,Total current year to date subscriptions,LISA qualifying addition,LISA bonus claim,Market value of account,Closure Date,ISA Reason for closure,LISA Reason for closure\n" +
      "ACC123,AB123456C,Ada,,Lovelace,1980-01-02,CASH,Yes,10.00,0.00,,2026-05-01,20.00,,,100.00,,,\n"

  private val details = FileUploadDetails(
    fileName = testFileName,
    fileMimeType = testFileMimeType,
    uploadTimestamp = testCreatedOn,
    checksum = testChecksum,
    size = testFileSize,
    upscanDownloadUrl = testDownloadUrl,
    objectStoreFileLocation = Some("stored-location")
  )

  private def monthlyReturn(
    status: FileUploadStatus,
    fileDetails: Option[FileUploadDetails] = Some(details)
  ): MonthlyReturn =
    MonthlyReturn(
      zReference = testZReference,
      submissionId = testSubmissionId,
      taxYear = testTaxYear,
      month = testMonth,
      createdOn = testCreatedOn,
      fileUploads = List(FileUpload(testUploadReference, status, testCreatedOn, fileDetails)),
      lastUpdated = testCreatedOn
    )

  "MonthlyReturnSubmissionProcessingService.process" - {
    "must fail when the monthly return is missing" in {
      val fixture = new Fixture(None)

      fixture.service.process(item).failed.futureValue mustBe a[NoSuchElementException]
      verifyNoInteractions(fixture.objectStoreConnector, fixture.returnsSubmissionConnector)
    }

    "must not transfer an already submitted upload" in {
      val fixture = new Fixture(Some(monthlyReturn(FileUploadStatus.Submitted)))

      fixture.service.process(item).futureValue mustBe (())
      verifyNoInteractions(fixture.objectStoreConnector, fixture.returnsSubmissionConnector)
    }

    "must reject an upload that has not validated successfully or has no object-store location" in
      Seq(
        monthlyReturn(FileUploadStatus.ValidationFailure),
        monthlyReturn(FileUploadStatus.ValidationSuccess, Some(details.copy(objectStoreFileLocation = None)))
      ).foreach { returnRecord =>
        val fixture = new Fixture(Some(returnRecord))

        fixture.service.process(item).failed.futureValue mustBe a[IllegalStateException]
        verifyNoInteractions(fixture.objectStoreConnector, fixture.returnsSubmissionConnector)
      }

    "must download, convert, submit NDJSON and mark the upload submitted" in {
      val fixture = new Fixture(Some(monthlyReturn(FileUploadStatus.ValidationSuccess)))
      val payload = Promise[ByteString]()
      when(
        fixture.returnsSubmissionConnector.sendSubmission(
          eqTo(item.zReference),
          eqTo(item.taxYear),
          eqTo(item.month),
          eqTo(item.reference),
          any[Source[ByteString, ?]]
        )(any, any)
      ).thenAnswer { (invocation: InvocationOnMock) =>
        invocation
          .getArgument[Source[ByteString, ?]](4)
          .runWith(Sink.fold(ByteString.empty)(_ ++ _))(inject[Materializer])
          .map { bytes =>
            payload.trySuccess(bytes)
            ()
          }
      }
      when(
        fixture.monthlyReturnService.markFileUploadSubmitted(item.zReference, item.taxYear, item.month, item.reference)
      )
        .thenReturn(Future.successful(true))

      fixture.service.process(item).futureValue mustBe (())

      val lines = payload.future.futureValue.utf8String.linesIterator.toList
      lines.size mustBe 1
      (Json.parse(lines.head) \ "accountNumber").as[String] mustBe "ACC123"
      verify(fixture.objectStoreConnector).getFile(eqTo(item.reference))(any)
      verify(fixture.monthlyReturnService)
        .markFileUploadSubmitted(item.zReference, item.taxYear, item.month, item.reference)
    }

    "must fail and remove temporary files when conversion fails without submitting" in {
      val converter = mock[MonthlyReturnCsvConverter]
      val fixture   = new Fixture(Some(monthlyReturn(FileUploadStatus.ValidationSuccess)), converter)
      val paths     = Promise[(Path, Path)]()
      doAnswer { (invocation: InvocationOnMock) =>
        paths.trySuccess(invocation.getArgument[Path](0) -> invocation.getArgument[Path](1))
        throw new IllegalStateException("conversion failed")
      }.when(converter).convert(any[Path], any[Path])

      fixture.service.process(item).failed.futureValue.getMessage mustBe "conversion failed"

      val (input, output) = paths.future.futureValue
      Files.exists(input) mustBe false
      Files.exists(output) mustBe false
      verifyNoInteractions(fixture.returnsSubmissionConnector)
      verify(fixture.monthlyReturnService, never())
        .markFileUploadSubmitted(any[String], any[String], any[Int], any[String])
    }

    "must select the XLSX converter for validated workbooks" in {
      val record       = monthlyReturn(
        FileUploadStatus.ValidationSuccess,
        Some(details.copy(fileMimeType = XLSX_MIME_TYPE))
      )
      val csvConverter = mock[MonthlyReturnCsvConverter]
      val fixture      = new Fixture(Some(record), csvConverter)
      doAnswer { (invocation: InvocationOnMock) =>
        Files.writeString(invocation.getArgument[Path](1), "{}\n", StandardCharsets.UTF_8)
        ()
      }.when(fixture.xlsxConverter).convert(any[Path], any[Path])
      when(
        fixture.returnsSubmissionConnector.sendSubmission(
          eqTo(item.zReference),
          eqTo(item.taxYear),
          eqTo(item.month),
          eqTo(item.reference),
          any[Source[ByteString, ?]]
        )(any, any)
      ).thenReturn(Future.successful(()))
      when(
        fixture.monthlyReturnService.markFileUploadSubmitted(item.zReference, item.taxYear, item.month, item.reference)
      )
        .thenReturn(Future.successful(true))

      fixture.service.process(item).futureValue mustBe (())

      verify(fixture.xlsxConverter).convert(any[Path], any[Path])
      verifyNoInteractions(csvConverter)
    }

    "must not mark submitted when the PUT fails" in {
      val fixture = new Fixture(Some(monthlyReturn(FileUploadStatus.ValidationSuccess)))
      when(
        fixture.returnsSubmissionConnector.sendSubmission(
          eqTo(item.zReference),
          eqTo(item.taxYear),
          eqTo(item.month),
          eqTo(item.reference),
          any[Source[ByteString, ?]]
        )(any, any)
      ).thenReturn(Future.failed(new RuntimeException("submission failed")))

      fixture.service.process(item).failed.futureValue.getMessage mustBe "submission failed"
      verify(fixture.monthlyReturnService, never())
        .markFileUploadSubmitted(any[String], any[String], any[Int], any[String])
    }

    "must fail when the submission was uploaded but cannot be marked submitted" in {
      val record  = monthlyReturn(FileUploadStatus.ValidationSuccess)
      val fixture = new Fixture(Some(record))
      when(
        fixture.returnsSubmissionConnector.sendSubmission(
          eqTo(item.zReference),
          eqTo(item.taxYear),
          eqTo(item.month),
          eqTo(item.reference),
          any[Source[ByteString, ?]]
        )(any, any)
      ).thenReturn(Future.successful(()))
      when(
        fixture.monthlyReturnService.markFileUploadSubmitted(item.zReference, item.taxYear, item.month, item.reference)
      )
        .thenReturn(Future.successful(false))

      fixture.service.process(item).failed.futureValue.getMessage must include("Could not mark upload")
    }
  }

  private class Fixture(
    record: Option[MonthlyReturn],
    csvConverter: MonthlyReturnCsvConverter = new MonthlyReturnCsvConverterImpl(new MonthlyReturnNdjsonWriterImpl())
  ) {
    val monthlyReturnService       = mock[MonthlyReturnService]
    val objectStoreConnector       = mock[ObjectStoreConnector]
    val returnsSubmissionConnector = mock[ReturnsSubmissionConnector]
    val xlsxConverter              = mock[MonthlyReturnXlsxConverter]

    when(monthlyReturnService.get(item.zReference, item.taxYear, item.month)).thenReturn(Future.successful(record))
    when(objectStoreConnector.getFile(eqTo(item.reference))(any))
      .thenReturn(Future.successful(Source.single(ByteString(csv, StandardCharsets.UTF_8))))

    val service = new MonthlyReturnSubmissionProcessingService(
      inject[ActorSystem],
      inject[TemporaryFileCreator],
      csvConverter,
      xlsxConverter,
      objectStoreConnector,
      returnsSubmissionConnector,
      monthlyReturnService
    )(ec, inject[Materializer])
  }
}
