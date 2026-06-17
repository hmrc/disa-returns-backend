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

import base.SpecBase
import org.mockito.Mockito.{verify, when}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.disareturnsbackend.repositories.MonthlyReturnFileUploadWorkItemRepository

import scala.concurrent.Future

class TestOnlyMonthlyReturnFileUploadWorkItemControllerSpec extends SpecBase {

  "TestOnlyMonthlyReturnFileUploadWorkItemController" - {

    "must return No Content and delete all work items" in {
      val repository = mock[MonthlyReturnFileUploadWorkItemRepository]
      when(repository.deleteAll()).thenReturn(Future.successful(2L))
      val controller = new TestOnlyMonthlyReturnFileUploadWorkItemController(stubControllerComponents(), repository)

      val result = controller.deleteAll()(FakeRequest("DELETE", "/test-only/monthly-return-file-upload-work-items"))

      status(result) mustBe NO_CONTENT
      verify(repository).deleteAll()
    }

    "must return Service Unavailable when delete fails" in {
      val repository = mock[MonthlyReturnFileUploadWorkItemRepository]
      when(repository.deleteAll()).thenReturn(Future.failed(new RuntimeException(testMongoDownMessage)))
      val controller = new TestOnlyMonthlyReturnFileUploadWorkItemController(stubControllerComponents(), repository)

      val result = controller.deleteAll()(FakeRequest("DELETE", "/test-only/monthly-return-file-upload-work-items"))

      status(result) mustBe SERVICE_UNAVAILABLE
      verify(repository).deleteAll()
    }
  }
}
