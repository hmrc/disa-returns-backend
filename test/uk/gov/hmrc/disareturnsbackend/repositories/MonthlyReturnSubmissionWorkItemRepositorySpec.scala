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
import uk.gov.hmrc.disareturnsbackend.models.MonthlyReturnSubmissionWorkItem
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.mongo.workitem.WorkItem

import java.time.{Clock, ZoneOffset}
import scala.concurrent.Future

class MonthlyReturnSubmissionWorkItemRepositorySpec
    extends SpecBase
    with DefaultPlayMongoRepositorySupport[WorkItem[MonthlyReturnSubmissionWorkItem]] {

  override protected def databaseName: String   = "disa-returns-backend-monthly-return-submission-work-item-test"
  override protected def checkTtlIndex: Boolean = false

  override protected val repository: MonthlyReturnSubmissionWorkItemRepository =
    new MonthlyReturnSubmissionWorkItemRepository(
      Clock.fixed(testCreatedOn, ZoneOffset.UTC),
      inject[AppConfig],
      mongoComponent
    )

  override protected def afterAll(): Unit =
    try dropDatabase()
    finally super.afterAll()

  "MonthlyReturnSubmissionWorkItemRepository" - {
    "must not enqueue the same monthly return upload twice" in {
      val item = MonthlyReturnSubmissionWorkItem(testZReference, testTaxYear, testMonth, testUploadReference)

      repository.enqueue(item).futureValue
      repository.enqueue(item).futureValue

      repository.collection.countDocuments().head().futureValue mustBe 1
    }

    "must accept concurrent enqueue requests for the same upload" in {
      val item = MonthlyReturnSubmissionWorkItem(testZReference, testTaxYear, testMonth, testUploadReference)

      Future.sequence(Seq.fill(10)(repository.enqueue(item))).futureValue

      repository.collection.countDocuments().head().futureValue mustBe 1
    }

    "must enqueue different uploads separately" in {
      val first  = MonthlyReturnSubmissionWorkItem(testZReference, testTaxYear, testMonth, testUploadReference)
      val second = first.copy(reference = "another-upload")

      Future.sequence(Seq(repository.enqueue(first), repository.enqueue(second))).futureValue

      repository.collection.countDocuments().head().futureValue mustBe 2
    }
  }
}
