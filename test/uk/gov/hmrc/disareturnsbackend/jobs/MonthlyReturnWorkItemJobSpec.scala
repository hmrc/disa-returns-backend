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

package uk.gov.hmrc.disareturnsbackend.jobs

import base.SpecBase
import org.apache.pekko.actor.ActorSystem
import org.bson.types.ObjectId
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq as eqTo
import org.mockito.Mockito.{doReturn, verify, verifyNoInteractions, when}
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.disareturnsbackend.config.AppConfig
import uk.gov.hmrc.disareturnsbackend.models.*
import uk.gov.hmrc.disareturnsbackend.repositories.MonthlyReturnFileUploadWorkItemRepository
import uk.gov.hmrc.disareturnsbackend.services.{FileUploadProcessingResult, MonthlyReturnFileUploadProcessingService, MonthlyReturnService}
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem}

import java.time.{Clock, Instant, ZoneOffset}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Future, Promise}

class MonthlyReturnWorkItemJobSpec extends SpecBase {

  private val now        = Instant.parse("2026-06-08T12:00:00Z")
  private val validation = FileUploadValidationResult(1, 0, FileUploadValidationStatus.ValidationSuccess)

  "processWorkItem" - {

    "must mark PermanentlyFailed and return true when monthly return is missing" in {
      val fixture = new Fixture(monthlyReturn = None)

      fixture.job.process(fixture.workItem).futureValue mustBe true

      verify(fixture.repository)
        .markAs(eqTo(fixture.workItem.id), eqTo(ProcessingStatus.PermanentlyFailed), any[Option[Instant]])
      verifyNoInteractions(fixture.processingService)
    }

    Seq(
      FileUploadProcessingResult.Processed(validation, Some("original"), None) -> ProcessingStatus.Succeeded,
      FileUploadProcessingResult.FileUploadNotFound                            -> ProcessingStatus.PermanentlyFailed,
      FileUploadProcessingResult.FileUploadNotReady                            -> ProcessingStatus.PermanentlyFailed,
      FileUploadProcessingResult.MonthlyReturnUpdateFailed                     -> ProcessingStatus.PermanentlyFailed,
      FileUploadProcessingResult.UnsupportedMimeType("application/pdf")        -> ProcessingStatus.PermanentlyFailed
    ).foreach { case (processingResult, expectedStatus) =>
      s"must mark $expectedStatus and return true when processing returns $processingResult" in {
        val fixture = new Fixture(processingResult = Future.successful(processingResult))

        fixture.job.process(fixture.workItem).futureValue mustBe true

        verify(fixture.repository).markAs(eqTo(fixture.workItem.id), eqTo(expectedStatus), any[Option[Instant]])
      }
    }

    "must mark Failed and return true when processing future fails" in {
      val fixture = new Fixture(processingResult = Future.failed(new RuntimeException("boom")))

      fixture.job.process(fixture.workItem).futureValue mustBe true

      verify(fixture.repository).markAs(eqTo(fixture.workItem.id), eqTo(ProcessingStatus.Failed), any[Option[Instant]])
    }

    "must not complete until processing has completed" in {
      val promise = Promise[FileUploadProcessingResult]()
      val fixture = new Fixture(processingResult = promise.future)

      val result = fixture.job.process(fixture.workItem)

      result.isCompleted mustBe false

      promise.success(FileUploadProcessingResult.Processed(validation, Some("original"), None))
      result.futureValue mustBe true
    }

    "must still return true when markAs returns false" in {
      val fixture = new Fixture(markAsResult = false)

      fixture.job.process(fixture.workItem).futureValue mustBe true
      verify(fixture.repository)
        .markAs(eqTo(fixture.workItem.id), eqTo(ProcessingStatus.Succeeded), any[Option[Instant]])
    }
  }

  private class Fixture(
    monthlyReturn: Option[MonthlyReturn] = Some(buildMonthlyReturn()),
    processingResult: Future[FileUploadProcessingResult] = Future.successful(
      FileUploadProcessingResult.Processed(validation, Some("original"), None)
    ),
    markAsResult: Boolean = true
  ) {
    val workItem: WorkItem[MonthlyReturnFileUploadWorkItem] = WorkItem(
      id = new ObjectId(),
      receivedAt = now,
      updatedAt = now,
      availableAt = now,
      status = ProcessingStatus.ToDo,
      failureCount = 0,
      item = MonthlyReturnFileUploadWorkItem(
        zReference = testZReference,
        taxYear = yearOnlyTestTaxYear,
        month = testMonth,
        reference = testUploadReference
      )
    )

    val repository: MonthlyReturnFileUploadWorkItemRepository = mock[MonthlyReturnFileUploadWorkItemRepository]
    when(repository.markAs(any[ObjectId], any[ProcessingStatus], any[Option[Instant]]))
      .thenReturn(Future.successful(markAsResult))

    val processingService: MonthlyReturnFileUploadProcessingService = mock[MonthlyReturnFileUploadProcessingService]
    monthlyReturn.foreach { monthlyReturn =>
      doReturn(processingResult).when(processingService).process(eqTo(monthlyReturn), eqTo(testUploadReference))
    }

    private val monthlyReturnService = mock[MonthlyReturnService]
    when(monthlyReturnService.get(eqTo(testZReference), eqTo(yearOnlyTestTaxYear), eqTo(testMonth)))
      .thenReturn(Future.successful(monthlyReturn))

    private val appConfig = mock[AppConfig]
    when(appConfig.monthlyReturnFileUploadJobPollInterval).thenReturn(10.seconds)

    val job = new TestableMonthlyReturnWorkItemJob(repository, processingService, monthlyReturnService, appConfig)
  }

  private class TestableMonthlyReturnWorkItemJob(
    repository: MonthlyReturnFileUploadWorkItemRepository,
    processingService: MonthlyReturnFileUploadProcessingService,
    monthlyReturnService: MonthlyReturnService,
    appConfig: AppConfig
  ) extends MonthlyReturnWorkItemJob(
        actorSystem = inject[ActorSystem],
        clock = Clock.fixed(now, ZoneOffset.UTC),
        lifecycle = mock[ApplicationLifecycle],
        appConfig = appConfig,
        monthlyReturnFileUploadWorkItemRepository = repository,
        monthlyReturnFileUploadProcessingService = processingService,
        monthlyReturnService = monthlyReturnService
      ) {
    def process(workItem: WorkItem[MonthlyReturnFileUploadWorkItem]): Future[Boolean] =
      processWorkItem(workerId = 1, workItem = workItem)
  }

  private def buildMonthlyReturn(): MonthlyReturn =
    MonthlyReturn(
      zReference = testZReference,
      submissionId = testSubmissionId,
      taxYear = yearOnlyTestTaxYear,
      month = testMonth,
      createdOn = now,
      fileUploads = Nil,
      lastUpdated = now
    )
}
