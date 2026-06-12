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

package uk.gov.hmrc.disareturnsbackend.repositories

import base.SpecBase
import uk.gov.hmrc.disareturnsbackend.config.AppConfig
import uk.gov.hmrc.disareturnsbackend.models.MonthlyReturnFileUploadWorkItem
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem}

import java.time.{Clock, Instant, ZoneOffset}

class MonthlyReturnFileUploadWorkItemRepositorySpec
    extends SpecBase
    with DefaultPlayMongoRepositorySupport[WorkItem[MonthlyReturnFileUploadWorkItem]] {

  override protected def databaseName: String = "disa-returns-backend-file-upload-work-item-repository-test"

  override protected def checkTtlIndex: Boolean = false

  private val fixedNow: Instant = testCreatedOn
  private val fixedClock: Clock = Clock.fixed(fixedNow, ZoneOffset.UTC)

  private lazy val appConfig: AppConfig = inject[AppConfig]

  override protected val repository: MonthlyReturnFileUploadWorkItemRepository =
    new MonthlyReturnFileUploadWorkItemRepository(fixedClock, appConfig, mongoComponent)

  override protected def afterAll(): Unit =
    try dropDatabase()
    finally super.afterAll()

  "MonthlyReturnFileUploadWorkItemRepository" - {

    "enqueue" - {

      "must create a file upload work item" in {
        val expectedItem = MonthlyReturnFileUploadWorkItem(
          zReference = testZReference,
          taxYear = yearOnlyTestTaxYear,
          month = testMonth,
          reference = testUploadReference
        )

        val workItem = repository
          .enqueue(
            zReference = testZReference,
            taxYear = yearOnlyTestTaxYear,
            month = testMonth,
            fileReference = testUploadReference
          )
          .futureValue

        workItem.item mustBe expectedItem
        workItem.receivedAt mustBe fixedNow
        workItem.updatedAt mustBe fixedNow
        workItem.availableAt mustBe fixedNow
        workItem.status mustBe ProcessingStatus.ToDo
        workItem.failureCount mustBe 0
      }
    }

    "deleteAll" - {

      "must delete all work items and leave the collection usable" in {
        repository
          .enqueue(
            zReference = testZReference,
            taxYear = yearOnlyTestTaxYear,
            month = testMonth,
            fileReference = testUploadReference
          )
          .futureValue
        repository
          .enqueue(
            zReference = testZReference,
            taxYear = yearOnlyTestTaxYear,
            month = testMonth,
            fileReference = "second-reference"
          )
          .futureValue

        repository.deleteAll().futureValue mustBe 2
        repository.collection.countDocuments().head().futureValue mustBe 0

        val workItem = repository
          .enqueue(
            zReference = testZReference,
            taxYear = yearOnlyTestTaxYear,
            month = testMonth,
            fileReference = "after-delete-reference"
          )
          .futureValue

        workItem.item.reference mustBe "after-delete-reference"
        repository.collection.countDocuments().head().futureValue mustBe 1
      }
    }
  }
}
