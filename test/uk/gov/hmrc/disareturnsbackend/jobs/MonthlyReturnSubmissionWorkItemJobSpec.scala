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
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.*
import play.api.http.Status.TOO_MANY_REQUESTS
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.disareturnsbackend.config.AppConfig
import uk.gov.hmrc.disareturnsbackend.models.MonthlyReturnSubmissionWorkItem
import uk.gov.hmrc.disareturnsbackend.repositories.MonthlyReturnSubmissionWorkItemRepository
import uk.gov.hmrc.disareturnsbackend.services.MonthlyReturnSubmissionProcessingService
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.mongo.workitem.*

import java.time.*
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class MonthlyReturnSubmissionWorkItemJobSpec extends SpecBase {
  private val now = testCreatedOn

  "MonthlyReturnSubmissionWorkItemJob" - {
    "must delay retryable upstream failures in the work-item queue" in {
      val fixture = new Fixture(UpstreamErrorResponse("server error", 500))

      fixture.job.process(fixture.workItem).futureValue mustBe true
      verify(fixture.repository)
        .markAs(eqTo(fixture.workItem.id), eqTo(ProcessingStatus.Failed), any[Option[Instant]])
    }

    "must permanently fail non-retryable upstream client errors" in {
      val fixture = new Fixture(UpstreamErrorResponse("bad request", 400))

      fixture.job.process(fixture.workItem).futureValue mustBe true
      verify(fixture.repository)
        .markAs(eqTo(fixture.workItem.id), eqTo(ProcessingStatus.PermanentlyFailed), any[Option[Instant]])
    }

    "must retry rate-limited submissions through the work-item queue" in {
      val fixture = new Fixture(UpstreamErrorResponse("rate limited", TOO_MANY_REQUESTS))

      fixture.job.process(fixture.workItem).futureValue mustBe true
      verify(fixture.repository)
        .markAs(eqTo(fixture.workItem.id), eqTo(ProcessingStatus.Failed), any[Option[Instant]])
    }

    "must mark Failed when conversion returns a failed Future" in {
      val fixture = new Fixture(new IllegalStateException("conversion failed"))

      fixture.job.process(fixture.workItem).futureValue mustBe true

      verify(fixture.repository)
        .markAs(eqTo(fixture.workItem.id), eqTo(ProcessingStatus.Failed), any[Option[Instant]])
    }

    "must mark Failed when processing throws before returning a Future" in {
      val fixture = new Fixture(UpstreamErrorResponse("server error", 500))
      when(fixture.processingService.process(eqTo(fixture.workItem.item)))
        .thenThrow(new IllegalStateException("conversion failed"))

      fixture.job.process(fixture.workItem).futureValue mustBe true

      verify(fixture.repository)
        .markAs(eqTo(fixture.workItem.id), eqTo(ProcessingStatus.Failed), any[Option[Instant]])
    }
  }

  private class Fixture(error: Throwable) {
    val workItem = WorkItem(
      id = new ObjectId(),
      receivedAt = now,
      updatedAt = now,
      availableAt = now,
      status = ProcessingStatus.ToDo,
      failureCount = 0,
      item = MonthlyReturnSubmissionWorkItem(testZReference, testTaxYear, testMonth, testUploadReference)
    )

    val repository = mock[MonthlyReturnSubmissionWorkItemRepository]
    when(repository.markAs(eqTo(workItem.id), any[ProcessingStatus], any[Option[Instant]]))
      .thenReturn(Future.successful(true))

    val processingService = mock[MonthlyReturnSubmissionProcessingService]
    when(processingService.process(eqTo(workItem.item))).thenReturn(Future.failed(error))

    private val appConfig = mock[AppConfig]
    when(appConfig.monthlyReturnSubmissionJobPollInterval).thenReturn(1.second)
    when(appConfig.monthlyReturnSubmissionJobFailedRetryAfter).thenReturn(Duration.ofMinutes(10))
    when(appConfig.monthlyReturnSubmissionJobInProgressRetryAfter).thenReturn(Duration.ofMinutes(15))
    when(appConfig.monthlyReturnSubmissionJobWorkerCount).thenReturn(2)

    val job = new TestableJob(
      inject[ActorSystem],
      Clock.fixed(now, ZoneOffset.UTC),
      mock[ApplicationLifecycle],
      appConfig,
      repository,
      processingService
    )
  }

  private class TestableJob(
    actorSystem: ActorSystem,
    clock: Clock,
    lifecycle: ApplicationLifecycle,
    appConfig: AppConfig,
    repository: MonthlyReturnSubmissionWorkItemRepository,
    processingService: MonthlyReturnSubmissionProcessingService
  ) extends MonthlyReturnSubmissionWorkItemJob(
        actorSystem,
        clock,
        lifecycle,
        appConfig,
        repository,
        processingService
      ) {
    def process(workItem: WorkItem[MonthlyReturnSubmissionWorkItem]): Future[Boolean] = processWorkItem(1, workItem)
  }
}
