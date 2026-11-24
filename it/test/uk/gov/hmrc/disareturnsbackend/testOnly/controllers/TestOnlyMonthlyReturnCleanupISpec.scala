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

package uk.gov.hmrc.disareturnsbackend.testOnly.controllers

import org.mongodb.scala.ObservableFuture
import play.api.http.Status.{BAD_REQUEST, NO_CONTENT}
import play.api.libs.json.Json
import uk.gov.hmrc.disareturnsbackend.BaseIntegrationSpec
import uk.gov.hmrc.disareturnsbackend.models.MonthlyReturnSubmissionWorkItem
import uk.gov.hmrc.disareturnsbackend.repositories.*

class TestOnlyMonthlyReturnCleanupISpec extends BaseIntegrationSpec {

  override def config: Map[String, Any] = super.config ++ Map(
    "application.router"                               -> "testOnlyDoNotUseInAppConf.Routes",
    "mongodb.uri"                                      -> "mongodb://localhost:27017/disa-returns-backend-cleanup-it",
    "monthly-return-file-upload-work-item-job.enabled" -> false,
    "monthly-return-submission-work-item-job.enabled"  -> false
  )

  private val returnsPath      = s"$testServicePath/test-only/monthly-returns"
  private val workItemsPath    = s"$returnsPath/work-items"
  private val otherZReference  = "Z5678"
  private val orphanZReference = "Z9012"

  private lazy val returns   = inject[MonthlyReturnRepository]
  private lazy val uploads   = inject[MonthlyReturnFileUploadWorkItemRepository]
  private lazy val transfers = inject[MonthlyReturnSubmissionWorkItemRepository]

  "test-only monthly return cleanup" should {

    "delete only matching returns and work items, including orphaned items" in {
      seed(testZReference)
      seed(otherZReference)
      seed(orphanZReference, withReturn = false)

      val lowerReference = s" ${testZReference.toLowerCase} "
      val body           = Json.obj("zReferences" -> Seq(lowerReference, lowerReference, orphanZReference))
      postJson(s"$returnsPath/cleanup", body).status shouldBe NO_CONTENT
      postJson(s"$returnsPath/cleanup", body).status shouldBe NO_CONTENT
      assertState(Set(otherZReference), Set(otherZReference))
    }

    "delete only matching work items without deleting returns" in {
      seed(testZReference)
      seed(otherZReference)

      postJson(s"$workItemsPath/cleanup", Json.obj("zReferences" -> Seq(testZReference))).status shouldBe NO_CONTENT
      assertState(Set(testZReference, otherZReference), Set(otherZReference))
    }

    "reject missing, empty or invalid references without deleting any records" in {
      seed(testZReference)

      val invalidBodies = Seq(
        Json.obj(),
        Json.obj("zReferences" -> Json.arr()),
        Json.obj("zReferences" -> Json.arr(testZReference, "invalid"))
      )
      for {
        path <- Seq(returnsPath, workItemsPath)
        body <- invalidBodies
      } postJson(s"$path/cleanup", body).status shouldBe BAD_REQUEST
      assertState(Set(testZReference), Set(testZReference))
    }

    "delete all work items without deleting returns" in {
      seed(testZReference)
      seed(orphanZReference, withReturn = false)

      delete(s"$workItemsPath/").status shouldBe NO_CONTENT
      assertState(Set(testZReference), Set.empty)

      seed(testZReference, withReturn = false)
      assertState(Set(testZReference), Set(testZReference))
    }

    "delete all returns and work items" in {
      seed(testZReference)
      seed(orphanZReference, withReturn = false)

      delete(s"$returnsPath/cleanup").status shouldBe NO_CONTENT
      assertState(Set.empty, Set.empty)
    }
  }

  private def seed(zReference: String, withReturn: Boolean = true): Unit = {
    if (withReturn) {
      returns.create(zReference, testTaxYear, testMonth, testSubmissionId, nilReturn = false).futureValue
    }
    uploads.enqueue(zReference, testTaxYear, testMonth, testUploadReference).futureValue
    transfers
      .enqueue(MonthlyReturnSubmissionWorkItem(zReference, testTaxYear, testMonth, testUploadReference))
      .futureValue
  }

  private def assertState(expectedReturns: Set[String], expectedWorkItems: Set[String]): Unit = {
    returns.collection.find().toFuture().futureValue.map(_.zReference).toSet        shouldBe expectedReturns
    uploads.collection.find().toFuture().futureValue.map(_.item.zReference).toSet   shouldBe expectedWorkItems
    transfers.collection.find().toFuture().futureValue.map(_.item.zReference).toSet shouldBe expectedWorkItems
  }
}
