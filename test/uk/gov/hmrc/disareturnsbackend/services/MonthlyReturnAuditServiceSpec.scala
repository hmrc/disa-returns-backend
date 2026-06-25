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
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{verify, when}
import play.api.libs.json.{JsArray, JsObject, Json}
import uk.gov.hmrc.disareturnsbackend.config.AppConfig
import uk.gov.hmrc.disareturnsbackend.models.*
import uk.gov.hmrc.disareturnsbackend.validators.fileupload.FileUploadValidatorResult
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import scala.concurrent.Future

class MonthlyReturnAuditServiceSpec extends SpecBase {

  private val fileUploadDetails = FileUploadDetails(
    fileName = testFileName,
    fileMimeType = testFileMimeType,
    uploadTimestamp = testCreatedOn,
    checksum = testChecksum,
    size = testFileSize,
    upscanDownloadUrl = testDownloadUrl,
    upscanCompletedOn = Some(testUpscanCompletedOn)
  )

  private val monthlyReturn = MonthlyReturn(
    zReference = testZReference,
    submissionId = testSubmissionId,
    taxYear = "2025-26",
    month = 2,
    createdOn = testCreatedOn,
    fileUploads = Nil,
    lastUpdated = testCreatedOn
  )

  "MonthlyReturnAuditService" - {

    "must send a success event after validation" in {
      val fixture           = new Fixture
      val validationOutcome = FileUploadValidatorResult(
        validation = FileUploadValidationResult(299, 0, FileUploadValidationStatus.ValidationSuccess),
        errorFileWritten = false,
        validationTimeMillis = 60
      )

      fixture.service
        .audit(monthlyReturn, testUploadReference, fileUploadDetails, validationOutcome)
        .futureValue mustBe ()

      val event  = fixture.capturedEvent()
      val detail = event.detail.as[JsObject]

      event.auditSource mustBe fixture.appName
      event.auditType mustBe MonthlyReturnAuditService.fileUploadValidation
      (detail \ "internalReturnId").as[String] mustBe testSubmissionId.toString
      (detail \ "fileUploadStatus").as[String] mustBe "Success"
      (detail \ "downloadTime").as[String] mustBe testUpscanCompletedOn.toEpochMilli.toString
      (detail \ "fileSize").as[String] mustBe testFileSize.toString
      (detail \ "fileValidationTime").as[String] mustBe "60"
      (detail \ "numberOfEntries").as[String] mustBe "299"
      (detail \ "fileReference").as[String] mustBe testUploadReference
      (detail \ "fileName").as[String] mustBe testFileName
      (detail \ "period").as[String] mustBe "January 2026"
      (detail \ "fileMimeType").toOption mustBe None
      (detail \ "errorDetails").toOption mustBe None
    }

    "must send aggregated validation error codes for a failed validation" in {
      val fixture           = new Fixture
      val validationOutcome = FileUploadValidatorResult(
        validation = FileUploadValidationResult(299, 8, FileUploadValidationStatus.ValidationFailed),
        errorFileWritten = true,
        errorVolumes = Map("E020" -> 6L, "E030" -> 2L),
        validationTimeMillis = 60
      )

      fixture.service
        .audit(monthlyReturn, testUploadReference, fileUploadDetails, validationOutcome)
        .futureValue mustBe ()

      val detail       = fixture.capturedEvent().detail.as[JsObject]
      val errorDetails = (detail \ "errorDetails").as[JsArray].value.map(_.as[JsObject])

      (detail \ "fileUploadStatus").as[String] mustBe "Failure"
      errorDetails mustBe Seq(
        Json.obj("errorType" -> "E020", "volume" -> 6),
        Json.obj("errorType" -> "E030", "volume" -> 2)
      )
    }

    "must send InvalidFile as the sole error detail for an invalid file" in {
      val fixture           = new Fixture
      val validationOutcome = FileUploadValidatorResult(
        validation = FileUploadValidationResult(0, 0, FileUploadValidationStatus.InvalidFile),
        errorFileWritten = false
      )

      fixture.service
        .audit(monthlyReturn, testUploadReference, fileUploadDetails, validationOutcome)
        .futureValue mustBe ()

      val detail       = fixture.capturedEvent().detail.as[JsObject]
      val errorDetails = (detail \ "errorDetails").as[JsArray].value.map(_.as[JsObject])

      (detail \ "fileUploadStatus").as[String] mustBe "Failure"
      errorDetails mustBe Seq(Json.obj("errorType" -> "InvalidFile", "volume" -> 1))
    }
  }

  private class Fixture {
    val appName = "disa-returns-backend"

    private val auditConnector = mock[AuditConnector]
    private val appConfig      = mock[AppConfig]
    val service                = new MonthlyReturnAuditService(auditConnector, appConfig)

    when(appConfig.appName).thenReturn(appName)
    when(auditConnector.sendExtendedEvent(any[ExtendedDataEvent])(any, any))
      .thenReturn(Future.successful(Success))

    def capturedEvent(): ExtendedDataEvent = {
      val captor = ArgumentCaptor.forClass(classOf[ExtendedDataEvent])
      verify(auditConnector).sendExtendedEvent(captor.capture())(any, any)
      captor.getValue
    }
  }
}
