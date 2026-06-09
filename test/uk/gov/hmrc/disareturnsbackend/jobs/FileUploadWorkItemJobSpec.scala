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
import org.mockito.Mockito.when
import org.bson.types.ObjectId
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.disareturnsbackend.config.AppConfig
import uk.gov.hmrc.disareturnsbackend.models.FileUploadWorkItem
import uk.gov.hmrc.disareturnsbackend.repositories.FileUploadWorkItemRepository
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem}

import java.time.{Clock, Instant, ZoneOffset}
import scala.concurrent.duration.DurationInt

class FileUploadWorkItemJobSpec extends SpecBase {

  private val now = Instant.parse("2026-06-08T12:00:00Z")

  "processWorkItem" - {

    "must return true when a file upload work item has been processed" in {
      val job      = new TestFileUploadWorkItemJob
      val workItem = WorkItem(
        id = new ObjectId(),
        receivedAt = now,
        updatedAt = now,
        availableAt = now,
        status = ProcessingStatus.ToDo,
        failureCount = 0,
        item = FileUploadWorkItem(
          zReference = testZReference,
          taxYear = yearOnlyTestTaxYear,
          month = testMonth,
          reference = testUploadReference
        )
      )

      job.process(workItem).futureValue mustBe true
    }
  }

  private class TestFileUploadWorkItemJob
      extends FileUploadWorkItemJob(
        actorSystem = inject[ActorSystem],
        clock = Clock.fixed(now, ZoneOffset.UTC),
        lifecycle = mock[ApplicationLifecycle],
        appConfig = {
          val appConfig = mock[AppConfig]
          when(appConfig.fileUploadJobPollInterval).thenReturn(10.seconds)
          appConfig
        },
        fileUploadValidationWorkItemRepository = mock[FileUploadWorkItemRepository]
      ) {

    def process(workItem: WorkItem[FileUploadWorkItem]) =
      processWorkItem(workerId = 1, workItem = workItem)
  }
}
