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

package uk.gov.hmrc.disareturnsbackend.controllers

import base.SpecBase
import org.mockito.ArgumentMatchers.eq as eqTo
import org.mockito.Mockito.{reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import play.api.Application
import play.api.http.HeaderNames.LOCATION
import play.api.inject.bind
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.disareturnsbackend.models.*
import uk.gov.hmrc.disareturnsbackend.services.CreateFileUploadResult
import uk.gov.hmrc.disareturnsbackend.services.DeclareMonthlyReturnResult
import uk.gov.hmrc.disareturnsbackend.services.CreateMonthlyReturnResult.{AlreadyExists, Created}
import uk.gov.hmrc.disareturnsbackend.services.MonthlyReturnService

import scala.concurrent.Future

class MonthlyReturnControllerSpec extends SpecBase with BeforeAndAfterEach {

  private val mockMonthlyReturnService = mock[MonthlyReturnService]

  override lazy val app: Application = applicationBuilder(
    Seq(
      bind[MonthlyReturnService].toInstance(mockMonthlyReturnService)
    )
  ).build()

  private lazy val controller = inject[MonthlyReturnController]

  private val path             = s"/monthly/$testZReference/$testTaxYear/$testRouteMonth"
  private val nilReturnPath    = s"$path/nilReturn"
  private val declarationsPath = s"$path/declarations"
  private val filesPath        = s"$path/files"
  private val filePath         = s"$filesPath/$testUploadReference"

  private val monthlyReturn = MonthlyReturn(
    zReference = testZReference,
    taxYear = testTaxYear,
    month = testMonth,
    createdOn = testExistingUpdatedOn,
    nilReturn = false,
    fileUploads = Nil,
    lastUpdated = testCreatedOn
  )

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockMonthlyReturnService)
  }

  "MonthlyReturnController.getMonthlyReturn" - {

    "must return OK when the MonthlyReturn exists" in {
      when(mockMonthlyReturnService.get(eqTo(testZReference), eqTo(testTaxYear), eqTo(testMonth)))
        .thenReturn(Future.successful(Some(monthlyReturn)))

      val result =
        controller.getMonthlyReturn(lowercaseTestZReference, testTaxYear, testRouteMonth)(FakeRequest("GET", path))

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(monthlyReturn)
      verify(mockMonthlyReturnService).get(eqTo(testZReference), eqTo(testTaxYear), eqTo(testMonth))
    }

    "must return NOT_FOUND when the MonthlyReturn does not exist" in {
      when(mockMonthlyReturnService.get(eqTo(testZReference), eqTo(testTaxYear), eqTo(testMonth)))
        .thenReturn(Future.successful(None))

      val result = controller.getMonthlyReturn(testZReference, testTaxYear, testRouteMonth)(FakeRequest("GET", path))

      status(result) mustBe NOT_FOUND
    }

    "must return SERVICE_UNAVAILABLE when the service fails" in {
      when(mockMonthlyReturnService.get(eqTo(testZReference), eqTo(testTaxYear), eqTo(testMonth)))
        .thenReturn(Future.failed(new RuntimeException(testMongoDownMessage)))

      val result = controller.getMonthlyReturn(testZReference, testTaxYear, testRouteMonth)(FakeRequest("GET", path))

      status(result) mustBe SERVICE_UNAVAILABLE
    }

    "must return BAD_REQUEST when path parameters are invalid" in {
      val result =
        controller.getMonthlyReturn(invalidTestZReference, testTaxYear, testRouteMonth)(FakeRequest("GET", path))

      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include(zReferenceFieldName)
    }
  }

  "MonthlyReturnController.createMonthlyReturn" - {

    "must return CREATED with Location header when the MonthlyReturn is created" in {
      when(mockMonthlyReturnService.create(eqTo(testZReference), eqTo(testTaxYear), eqTo(testMonth), eqTo(true)))
        .thenReturn(Future.successful(Created))

      val result = controller.createMonthlyReturn(testZReference, testTaxYear, testRouteMonth)(
        FakeRequest("POST", path).withBody(Json.toJson(CreateMonthlyReturnRequest(nilReturn = true)))
      )

      status(result) mustBe CREATED
      header(LOCATION, result).value mustBe path
    }

    "must return CONFLICT when the MonthlyReturn already exists" in {
      when(mockMonthlyReturnService.create(eqTo(testZReference), eqTo(testTaxYear), eqTo(testMonth), eqTo(false)))
        .thenReturn(Future.successful(AlreadyExists))

      val result = controller.createMonthlyReturn(testZReference, testTaxYear, testRouteMonth)(
        FakeRequest("POST", path).withBody(Json.toJson(CreateMonthlyReturnRequest(nilReturn = false)))
      )

      status(result) mustBe CONFLICT
    }

    "must return SERVICE_UNAVAILABLE when the service fails" in {
      when(mockMonthlyReturnService.create(eqTo(testZReference), eqTo(testTaxYear), eqTo(testMonth), eqTo(false)))
        .thenReturn(Future.failed(new RuntimeException(testMongoDownMessage)))

      val result = controller.createMonthlyReturn(testZReference, testTaxYear, testRouteMonth)(
        FakeRequest("POST", path).withBody(Json.toJson(CreateMonthlyReturnRequest(nilReturn = false)))
      )

      status(result) mustBe SERVICE_UNAVAILABLE
    }

    "must return BAD_REQUEST when nilReturn is not a boolean" in {
      val result = controller.createMonthlyReturn(testZReference, testTaxYear, testRouteMonth)(
        FakeRequest("POST", path).withBody(Json.obj(nilReturnFieldName -> "false"))
      )

      status(result) mustBe BAD_REQUEST
    }
  }

  "MonthlyReturnController.updateNilReturn" - {

    "must return OK with the updated MonthlyReturn" in {
      val updatedReturn = monthlyReturn.copy(nilReturn = true)

      when(
        mockMonthlyReturnService.updateNilReturn(eqTo(testZReference), eqTo(testTaxYear), eqTo(testMonth), eqTo(true))
      )
        .thenReturn(Future.successful(Some(updatedReturn)))

      val result = controller.updateNilReturn(testZReference, testTaxYear, testRouteMonth)(
        FakeRequest("PUT", nilReturnPath).withBody(Json.obj(valueFieldName -> true))
      )

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(updatedReturn)
    }

    "must return NOT_FOUND when the MonthlyReturn does not exist" in {
      when(
        mockMonthlyReturnService.updateNilReturn(eqTo(testZReference), eqTo(testTaxYear), eqTo(testMonth), eqTo(true))
      )
        .thenReturn(Future.successful(None))

      val result = controller.updateNilReturn(testZReference, testTaxYear, testRouteMonth)(
        FakeRequest("PUT", nilReturnPath).withBody(Json.obj(valueFieldName -> true))
      )

      status(result) mustBe NOT_FOUND
    }

    "must return SERVICE_UNAVAILABLE when the service fails" in {
      when(
        mockMonthlyReturnService.updateNilReturn(eqTo(testZReference), eqTo(testTaxYear), eqTo(testMonth), eqTo(false))
      )
        .thenReturn(Future.failed(new RuntimeException(testMongoDownMessage)))

      val result = controller.updateNilReturn(testZReference, testTaxYear, testRouteMonth)(
        FakeRequest("PUT", nilReturnPath).withBody(Json.obj(valueFieldName -> false))
      )

      status(result) mustBe SERVICE_UNAVAILABLE
    }

    "must return BAD_REQUEST when the update value is not a boolean" in {
      val result = controller.updateNilReturn(testZReference, testTaxYear, testRouteMonth)(
        FakeRequest("PUT", nilReturnPath).withBody(Json.obj(valueFieldName -> "true"))
      )

      status(result) mustBe BAD_REQUEST
    }

    "must return BAD_REQUEST when the update value is missing" in {
      val result = controller.updateNilReturn(testZReference, testTaxYear, testRouteMonth)(
        FakeRequest("PUT", nilReturnPath).withBody(Json.obj())
      )

      status(result) mustBe BAD_REQUEST
    }
  }

  "MonthlyReturnController.declareMonthlyReturn" - {

    "must return OK with the declared MonthlyReturn" in {
      val declaredReturn = monthlyReturn.copy(declaredOn = Some(testCreatedOn), lastUpdated = testCreatedOn)

      when(mockMonthlyReturnService.declare(eqTo(testZReference), eqTo(testTaxYear), eqTo(testMonth)))
        .thenReturn(Future.successful(DeclareMonthlyReturnResult.Declared(declaredReturn)))

      val result = controller.declareMonthlyReturn(lowercaseTestZReference, testTaxYear, testRouteMonth)(
        FakeRequest("POST", declarationsPath)
      )

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(declaredReturn)
      verify(mockMonthlyReturnService).declare(eqTo(testZReference), eqTo(testTaxYear), eqTo(testMonth))
    }

    "must return CONFLICT when the MonthlyReturn has already been declared" in {
      when(mockMonthlyReturnService.declare(eqTo(testZReference), eqTo(testTaxYear), eqTo(testMonth)))
        .thenReturn(Future.successful(DeclareMonthlyReturnResult.AlreadyDeclared))

      val result = controller.declareMonthlyReturn(testZReference, testTaxYear, testRouteMonth)(
        FakeRequest("POST", declarationsPath)
      )

      status(result) mustBe CONFLICT
    }

    "must return NOT_FOUND when the MonthlyReturn does not exist" in {
      when(mockMonthlyReturnService.declare(eqTo(testZReference), eqTo(testTaxYear), eqTo(testMonth)))
        .thenReturn(Future.successful(DeclareMonthlyReturnResult.MonthlyReturnNotFound))

      val result = controller.declareMonthlyReturn(testZReference, testTaxYear, testRouteMonth)(
        FakeRequest("POST", declarationsPath)
      )

      status(result) mustBe NOT_FOUND
    }

    "must return UNPROCESSABLE_ENTITY when the declaration period is closed" in {
      when(mockMonthlyReturnService.declare(eqTo(testZReference), eqTo(testTaxYear), eqTo(testMonth)))
        .thenReturn(Future.successful(DeclareMonthlyReturnResult.OutsideDeclarationPeriod))

      val result = controller.declareMonthlyReturn(testZReference, testTaxYear, testRouteMonth)(
        FakeRequest("POST", declarationsPath)
      )

      status(result) mustBe UNPROCESSABLE_ENTITY
    }

    "must return SERVICE_UNAVAILABLE when the service fails" in {
      when(mockMonthlyReturnService.declare(eqTo(testZReference), eqTo(testTaxYear), eqTo(testMonth)))
        .thenReturn(Future.failed(new RuntimeException(testMongoDownMessage)))

      val result = controller.declareMonthlyReturn(testZReference, testTaxYear, testRouteMonth)(
        FakeRequest("POST", declarationsPath)
      )

      status(result) mustBe SERVICE_UNAVAILABLE
    }

    "must return BAD_REQUEST when path parameters are invalid" in {
      val result = controller.declareMonthlyReturn(invalidTestZReference, testTaxYear, testRouteMonth)(
        FakeRequest("POST", declarationsPath)
      )

      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include(zReferenceFieldName)
    }
  }

  "MonthlyReturnController.createFileUpload" - {

    "must return CREATED with Location header when the file upload is created" in {
      val updatedReturn = monthlyReturn.copy(
        fileUploads = List(
          FileUpload(
            reference = testUploadReference,
            status = FileUploadStatus.Created,
            createdOn = testCreatedOn
          )
        )
      )

      when(
        mockMonthlyReturnService.createFileUpload(
          eqTo(testZReference),
          eqTo(testTaxYear),
          eqTo(testMonth),
          eqTo(testUploadReference)
        )
      )
        .thenReturn(Future.successful(CreateFileUploadResult.FileUploadCreated(updatedReturn)))

      val result = controller.createFileUpload(testZReference, testTaxYear, testRouteMonth)(
        FakeRequest("POST", filesPath).withBody(Json.toJson(CreateFileUploadRequest(testUploadReference)))
      )

      status(result) mustBe CREATED
      header(LOCATION, result).value mustBe filePath
    }

    "must return NOT_FOUND when the MonthlyReturn does not exist" in {
      when(
        mockMonthlyReturnService.createFileUpload(
          eqTo(testZReference),
          eqTo(testTaxYear),
          eqTo(testMonth),
          eqTo(testUploadReference)
        )
      )
        .thenReturn(Future.successful(CreateFileUploadResult.MonthlyReturnNotFound))

      val result = controller.createFileUpload(testZReference, testTaxYear, testRouteMonth)(
        FakeRequest("POST", filesPath).withBody(Json.toJson(CreateFileUploadRequest(testUploadReference)))
      )

      status(result) mustBe NOT_FOUND
    }

    "must return CONFLICT when the file upload already exists" in {
      when(
        mockMonthlyReturnService.createFileUpload(
          eqTo(testZReference),
          eqTo(testTaxYear),
          eqTo(testMonth),
          eqTo(testUploadReference)
        )
      )
        .thenReturn(Future.successful(CreateFileUploadResult.FileUploadAlreadyExists))

      val result = controller.createFileUpload(testZReference, testTaxYear, testRouteMonth)(
        FakeRequest("POST", filesPath).withBody(Json.toJson(CreateFileUploadRequest(testUploadReference)))
      )

      status(result) mustBe CONFLICT
    }

    "must return UNPROCESSABLE_ENTITY when the MonthlyReturn has already been declared" in {
      when(
        mockMonthlyReturnService.createFileUpload(
          eqTo(testZReference),
          eqTo(testTaxYear),
          eqTo(testMonth),
          eqTo(testUploadReference)
        )
      )
        .thenReturn(Future.successful(CreateFileUploadResult.MonthlyReturnAlreadyDeclared))

      val result = controller.createFileUpload(testZReference, testTaxYear, testRouteMonth)(
        FakeRequest("POST", filesPath).withBody(Json.toJson(CreateFileUploadRequest(testUploadReference)))
      )

      status(result) mustBe UNPROCESSABLE_ENTITY
    }

    "must return SERVICE_UNAVAILABLE when the service fails" in {
      when(
        mockMonthlyReturnService.createFileUpload(
          eqTo(testZReference),
          eqTo(testTaxYear),
          eqTo(testMonth),
          eqTo(testUploadReference)
        )
      )
        .thenReturn(Future.failed(new RuntimeException(testMongoDownMessage)))

      val result = controller.createFileUpload(testZReference, testTaxYear, testRouteMonth)(
        FakeRequest("POST", filesPath).withBody(Json.toJson(CreateFileUploadRequest(testUploadReference)))
      )

      status(result) mustBe SERVICE_UNAVAILABLE
    }
  }

  "MonthlyReturnController.getFileUpload" - {

    "must return OK with the FileUpload when it exists" in {
      val fileUpload = FileUpload(
        reference = testUploadReference,
        status = FileUploadStatus.Created,
        createdOn = testCreatedOn
      )

      when(
        mockMonthlyReturnService.getFileUpload(
          eqTo(testZReference),
          eqTo(testTaxYear),
          eqTo(testMonth),
          eqTo(testUploadReference)
        )
      )
        .thenReturn(Future.successful(Some(fileUpload)))

      val result = controller.getFileUpload(testZReference, testTaxYear, testRouteMonth, testUploadReference)(
        FakeRequest("GET", filePath)
      )

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(fileUpload)
    }

    "must return NOT_FOUND when the FileUpload does not exist" in {
      when(
        mockMonthlyReturnService.getFileUpload(
          eqTo(testZReference),
          eqTo(testTaxYear),
          eqTo(testMonth),
          eqTo(testUploadReference)
        )
      )
        .thenReturn(Future.successful(None))

      val result = controller.getFileUpload(testZReference, testTaxYear, testRouteMonth, testUploadReference)(
        FakeRequest("GET", filePath)
      )

      status(result) mustBe NOT_FOUND
    }

    "must return SERVICE_UNAVAILABLE when the service fails" in {
      when(
        mockMonthlyReturnService.getFileUpload(
          eqTo(testZReference),
          eqTo(testTaxYear),
          eqTo(testMonth),
          eqTo(testUploadReference)
        )
      )
        .thenReturn(Future.failed(new RuntimeException(testMongoDownMessage)))

      val result = controller.getFileUpload(testZReference, testTaxYear, testRouteMonth, testUploadReference)(
        FakeRequest("GET", filePath)
      )

      status(result) mustBe SERVICE_UNAVAILABLE
    }
  }

  "MonthlyReturnController.deleteFileUpload" - {

    "must return NO_CONTENT when the FileUpload is deleted" in {
      when(
        mockMonthlyReturnService.deleteFileUpload(
          eqTo(testZReference),
          eqTo(testTaxYear),
          eqTo(testMonth),
          eqTo(testUploadReference)
        )
      )
        .thenReturn(Future.successful(true))

      val result = controller.deleteFileUpload(testZReference, testTaxYear, testRouteMonth, testUploadReference)(
        FakeRequest("DELETE", filePath)
      )

      status(result) mustBe NO_CONTENT
    }

    "must return NOT_FOUND when the FileUpload does not exist" in {
      when(
        mockMonthlyReturnService.deleteFileUpload(
          eqTo(testZReference),
          eqTo(testTaxYear),
          eqTo(testMonth),
          eqTo(testUploadReference)
        )
      )
        .thenReturn(Future.successful(false))

      val result = controller.deleteFileUpload(testZReference, testTaxYear, testRouteMonth, testUploadReference)(
        FakeRequest("DELETE", filePath)
      )

      status(result) mustBe NOT_FOUND
    }

    "must return SERVICE_UNAVAILABLE when the service fails" in {
      when(
        mockMonthlyReturnService.deleteFileUpload(
          eqTo(testZReference),
          eqTo(testTaxYear),
          eqTo(testMonth),
          eqTo(testUploadReference)
        )
      )
        .thenReturn(Future.failed(new RuntimeException(testMongoDownMessage)))

      val result = controller.deleteFileUpload(testZReference, testTaxYear, testRouteMonth, testUploadReference)(
        FakeRequest("DELETE", filePath)
      )

      status(result) mustBe SERVICE_UNAVAILABLE
    }

    "must return BAD_REQUEST when path parameters are invalid" in {
      val result = controller.deleteFileUpload(invalidTestZReference, testTaxYear, testRouteMonth, testUploadReference)(
        FakeRequest("DELETE", filePath)
      )

      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include(zReferenceFieldName)
    }
  }
}
