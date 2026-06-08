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

import play.api.http.HeaderNames.LOCATION
import play.api.http.Status.{BAD_REQUEST, CONFLICT, CREATED, NO_CONTENT, NOT_FOUND, OK, UNPROCESSABLE_ENTITY}
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.readableAsString
import uk.gov.hmrc.disareturnsbackend.BaseIntegrationSpec

class MonthlyReturnControllerISpec extends BaseIntegrationSpec {

  private val monthlyPath = s"$testServicePath/monthly/$testZReference/$testTaxYear/$testMonth"
  private val invalidMonthlyPath =
    s"$testServicePath/monthly/$invalidTestZReference/$invalidTestTaxYear/$invalidTestMonth"
  private val nilReturnPath = s"$monthlyPath/nilReturn"
  private val isoInstantPattern = "\\d{4}-\\d{2}-\\d{2}T.*Z"
  private val declarationsPath = s"$monthlyPath/declarations"
  private val filesPath = s"$monthlyPath/files"
  private val filePath = s"$filesPath/$testUploadReference"

  private val nilReturnTrueRequest = Json.obj(nilReturnFieldName -> true)
  private val invalidNilReturnRequest = Json.obj(nilReturnFieldName -> "false")
  private val nilReturnValueTrueRequest = Json.obj(valueFieldName -> true)
  private val nilReturnValueFalseRequest = Json.obj(valueFieldName -> false)
  private val invalidNilReturnValueRequest = Json.obj(valueFieldName -> "yes")
  private val createFileUploadRequest = Json.obj(referenceFieldName -> testUploadReference)

  "GET to monthly return" should {

    "return 200 OK when the MonthlyReturn exists" in {
      val createResult = postJson(monthlyPath, nilReturnFalseRequest)
      val submissionId = (createResult.json \ submissionIdFieldName).as[String]

      val result = get(monthlyPath)

      result.status                 shouldBe OK
      (result.json \ zReferenceFieldName).as[String] shouldBe testZReference
      (result.json \ submissionIdFieldName).as[String] shouldBe submissionId
      (result.json \ taxYearFieldName).as[String]    shouldBe testTaxYear
      (result.json \ monthFieldName).as[Int]         shouldBe testMonth
      (result.json \ nilReturnFieldName).as[Boolean] shouldBe false
      (result.json \ createdOnFieldName).as[String] should fullyMatch regex isoInstantPattern
      (result.json \ lastUpdatedFieldName).as[String] should fullyMatch regex isoInstantPattern
    }

    "return 404 Not Found when the MonthlyReturn does not exist" in {
      val result = get(monthlyPath)

      result.status shouldBe NOT_FOUND
    }

    "return 400 Bad Request when path parameters are invalid" in {
      val result = get(invalidMonthlyPath)

      result.status shouldBe BAD_REQUEST
      result.body   should include(zReferenceFieldName)
      result.body   should include(taxYearFieldName)
    }
  }

  "POST to monthly return" should {

    "return 201 Created with the resource Location when the MonthlyReturn is created" in {
      val result = postJson(monthlyPath, nilReturnTrueRequest)

      result.status        shouldBe CREATED
      result.header(LOCATION) shouldBe Some(monthlyPath)
      (result.json \ submissionIdFieldName).as[String] should not be empty
    }

    "return 409 Conflict when the MonthlyReturn already exists" in {
      postJson(monthlyPath, nilReturnFalseRequest)

      val result = postJson(monthlyPath, nilReturnTrueRequest)

      result.status shouldBe CONFLICT
    }

    "return 400 Bad Request when nilReturn is not a boolean" in {
      val result = postJson(monthlyPath, invalidNilReturnRequest)

      result.status shouldBe BAD_REQUEST
    }

  }

  "PUT to monthly return nilReturn" should {

    "return 200 OK and remove file uploads when setting nilReturn to true" in {
      val createResult = postJson(monthlyPath, nilReturnFalseRequest)
      val submissionId = (createResult.json \ submissionIdFieldName).as[String]
      postJson(filesPath, createFileUploadRequest).status shouldBe CREATED

      val result = putJson(nilReturnPath, nilReturnValueTrueRequest)

      result.status                 shouldBe OK
      (result.json \ submissionIdFieldName).as[String] shouldBe submissionId
      (result.json \ nilReturnFieldName).as[Boolean] shouldBe true
      (result.json \ fileUploadsFieldName).as[Seq[JsValue]] shouldBe empty
      (result.json \ createdOnFieldName).as[String] should fullyMatch regex isoInstantPattern
    }

    "return 200 OK when setting nilReturn to false" in {
      val createResult = postJson(monthlyPath, nilReturnTrueRequest)
      val submissionId = (createResult.json \ submissionIdFieldName).as[String]

      val result = putJson(nilReturnPath, nilReturnValueFalseRequest)

      result.status                 shouldBe OK
      (result.json \ submissionIdFieldName).as[String] shouldBe submissionId
      (result.json \ nilReturnFieldName).as[Boolean] shouldBe false
      (result.json \ fileUploadsFieldName).as[Seq[JsValue]] shouldBe empty
      (result.json \ createdOnFieldName).as[String] should fullyMatch regex isoInstantPattern
    }

    "return 404 Not Found when the MonthlyReturn does not exist" in {
      val result = putJson(nilReturnPath, nilReturnValueTrueRequest)

      result.status shouldBe NOT_FOUND
    }

    "return 400 Bad Request when the nilReturn update value is invalid" in {
      val result = putJson(nilReturnPath, invalidNilReturnValueRequest)

      result.status shouldBe BAD_REQUEST
    }

    "return 400 Bad Request when the nilReturn update value is missing" in {
      val result = putJson(nilReturnPath, Json.obj())

      result.status shouldBe BAD_REQUEST
    }
  }

  "POST to monthly return declarations" should {

    "return 204 No Content when the MonthlyReturn exists" in {
      postJson(monthlyPath, nilReturnFalseRequest)

      val result = postJson(declarationsPath, Json.obj())

      result.status shouldBe NO_CONTENT

      val getResult = get(monthlyPath)
      (getResult.json \ declaredOnFieldName).as[String]   shouldBe testCreatedOnString
      (getResult.json \ lastUpdatedFieldName).as[String] shouldBe testCreatedOnString
    }

    "return 409 Conflict when the MonthlyReturn has already been declared" in {
      postJson(monthlyPath, nilReturnFalseRequest)
      postJson(declarationsPath, Json.obj()).status shouldBe NO_CONTENT

      val result = postJson(declarationsPath, Json.obj())

      result.status shouldBe CONFLICT
    }

    "return 404 Not Found when the MonthlyReturn does not exist" in {
      val result = postJson(declarationsPath, Json.obj())

      result.status shouldBe NOT_FOUND
    }

    "return 400 Bad Request when path parameters are invalid" in {
      val result = postJson(s"$invalidMonthlyPath/declarations", Json.obj())

      result.status shouldBe BAD_REQUEST
      result.body   should include(zReferenceFieldName)
      result.body   should include(taxYearFieldName)
    }
  }

  "POST to monthly return files" should {

    "return 201 Created with the file Location when the file upload is created" in {
      postJson(monthlyPath, nilReturnFalseRequest)

      val result = postJson(filesPath, createFileUploadRequest)

      result.status           shouldBe CREATED
      result.header(LOCATION) shouldBe Some(filePath)
    }

    "return 404 Not Found when the MonthlyReturn does not exist" in {
      val result = postJson(filesPath, createFileUploadRequest)

      result.status shouldBe NOT_FOUND
    }

    "return 409 Conflict when the file upload reference already exists" in {
      postJson(monthlyPath, nilReturnFalseRequest)
      postJson(filesPath, createFileUploadRequest).status shouldBe CREATED

      val result = postJson(filesPath, createFileUploadRequest)

      result.status shouldBe CONFLICT
    }

    "return 422 Unprocessable Entity when the MonthlyReturn has already been declared" in {
      postJson(monthlyPath, nilReturnFalseRequest)
      postJson(declarationsPath, Json.obj()).status shouldBe NO_CONTENT

      val result = postJson(filesPath, createFileUploadRequest)

      result.status shouldBe UNPROCESSABLE_ENTITY
    }
  }

  "GET to monthly return files" should {

    "return 200 OK when the file upload exists" in {
      postJson(monthlyPath, nilReturnFalseRequest)
      val createResult = postJson(filesPath, createFileUploadRequest)

      val result = get(createResult.header(LOCATION).get)

      result.status shouldBe OK
      (result.json \ referenceFieldName).as[String] shouldBe testUploadReference
      (result.json \ statusFieldName).as[String]    shouldBe createdStatusString
    }

    "return 404 Not Found when the MonthlyReturn does not exist" in {
      val result = get(filePath)

      result.status shouldBe NOT_FOUND
    }

    "return 404 Not Found when the file upload does not exist" in {
      postJson(monthlyPath, nilReturnFalseRequest)

      val result = get(filePath)

      result.status shouldBe NOT_FOUND
    }
  }

  "DELETE to monthly return files" should {

    "return 204 No Content and delete the file upload when it exists" in {
      postJson(monthlyPath, nilReturnFalseRequest)
      postJson(filesPath, createFileUploadRequest).status shouldBe CREATED

      val deleteResult = delete(filePath)

      deleteResult.status shouldBe NO_CONTENT

      val getResult = get(filePath)
      getResult.status shouldBe NOT_FOUND
    }

    "return 404 Not Found when the file upload does not exist" in {
      postJson(monthlyPath, nilReturnFalseRequest)

      val result = delete(filePath)

      result.status shouldBe NOT_FOUND
    }
  }
}
