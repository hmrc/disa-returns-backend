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

import play.api.Application
import play.api.http.Status.NO_CONTENT
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.disareturnsbackend.BaseIntegrationSpec
import uk.gov.hmrc.disareturnsbackend.repositories.MonthlyReturnFileUploadWorkItemRepository
import uk.gov.hmrc.play.audit.http.connector.DatastreamMetrics

class TestOnlyMonthlyReturnFileUploadWorkItemControllerISpec extends BaseIntegrationSpec {

  override lazy val app: Application = new GuiceApplicationBuilder()
    .configure(config + ("application.router" -> "testOnlyDoNotUseInAppConf.Routes"))
    .overrides(bind[DatastreamMetrics].toInstance(DatastreamMetrics.disabled))
    .build()

  private val repository = inject[MonthlyReturnFileUploadWorkItemRepository]
  private val path       = s"$testServicePath/test-only/monthly-return-file-upload-work-items"

  "test-only monthly return file-upload work-item routes" should {

    "delete all work items and leave the collection usable" in {
      repository
        .enqueue(
          zReference = testZReference,
          taxYear = yearOnlyTestTaxYear,
          month = testMonth,
          fileReference = testUploadReference
        )
        .futureValue

      delete(path).status shouldBe NO_CONTENT
      repository.pullOutstanding(testCreatedOn, testCreatedOn).futureValue shouldBe None

      val workItem = repository
        .enqueue(
          zReference = testZReference,
          taxYear = yearOnlyTestTaxYear,
          month = testMonth,
          fileReference = "after-delete-reference"
        )
        .futureValue

      workItem.item.reference shouldBe "after-delete-reference"
    }
  }
}
