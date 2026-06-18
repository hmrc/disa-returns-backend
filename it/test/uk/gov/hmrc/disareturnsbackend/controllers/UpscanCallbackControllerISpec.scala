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

import play.api.http.MimeTypes
import play.api.http.MimeTypes.JSON
import play.api.http.Status.*
import play.api.libs.json.*
import play.api.libs.ws.readableAsString
import uk.gov.hmrc.disareturnsbackend.BaseIntegrationSpec
import uk.gov.hmrc.disareturnsbackend.models.{
  UpscanDetails,
  UpscanFailure,
  UpscanFailureDetails,
  UpscanFailureReason,
  UpscanResult,
  UpscanSuccess
}

class UpscanCallbackControllerISpec extends BaseIntegrationSpec {

  private val callbackPath = s"/monthly/upscan/callback/$testZReference/$testTaxYear/$testRouteMonth"

  private val fullCallbackPath = testServicePath + callbackPath

  private val monthlyPath = s"$testServicePath/monthly/$testZReference/$testTaxYear/$testRouteMonth"
  private val filesPath   = s"$monthlyPath/files"

  private val upscanReference = testUploadReference
  private val duplicateUploadReference = s"$testUploadReference-duplicate"

  private val uploadDetails = UpscanDetails(
    fileName = testFileName,
    fileMimeType = testFileMimeType,
    uploadTimestamp = testCreatedOn,
    checksum = testChecksum,
    size = testFileSize
  )

  private val successfulUploadResult: UpscanResult = UpscanSuccess(
    reference = upscanReference,
    downloadUrl = testDownloadUrl,
    uploadDetails = uploadDetails
  )

  "POST to upscan callback" should {

    "return 202 Accepted for READY callback payload" in {
      val result = postUploadResult(successfulUploadResult)

      result.status shouldBe ACCEPTED
    }

    "set the file upload status to DUPLICATE when the checksum already exists" in {
      postJson(monthlyPath, nilReturnFalseRequest).status shouldBe CREATED
      postJson(filesPath, Json.obj(referenceFieldName -> upscanReference)).status shouldBe CREATED
      postJson(filesPath, Json.obj(referenceFieldName -> duplicateUploadReference)).status shouldBe CREATED

      postUploadResult(successfulUploadResult).status shouldBe ACCEPTED

      val result = postUploadResult(successfulUploadResultFor(duplicateUploadReference))

      result.status shouldBe ACCEPTED

      val monthlyReturn = get(monthlyPath)
      monthlyReturn.status shouldBe OK
      (fileUpload(monthlyReturn.json, duplicateUploadReference) \ statusFieldName).as[String] shouldBe duplicateStatusString
    }

    "return 202 Accepted for FAILED QUARANTINE callback payload" in {
      val result = postUploadResult(
        failedUploadResult(UpscanFailureReason.Quarantine, testEicarSignatureMessage)
      )

      result.status shouldBe ACCEPTED
    }

    "return 202 Accepted for FAILED REJECTED callback payload" in {
      val result = postUploadResult(
        failedUploadResult(
          UpscanFailureReason.Rejected,
          testMimeTypeFailureMessage
        )
      )

      result.status shouldBe ACCEPTED
    }

    "return 202 Accepted for FAILED UNKNOWN callback payload" in {
      val result = postUploadResult(
        failedUploadResult(UpscanFailureReason.Unknown, testUnableToParseFailureMessage)
      )

      result.status shouldBe ACCEPTED
    }

    "return 400 Bad Request when fileStatus is invalid" in {
      val result = postJson(
        fullCallbackPath,
        Json.toJson(InvalidFileStatusPayload(upscanReference, invalidFileStatusString))
      )

      result.status shouldBe BAD_REQUEST
      result.body   should include(invalidUpscanResultPayloadMessage)
    }

    "return 400 Bad Request when failureReason is invalid" in {
      val result = postJson(
        fullCallbackPath,
        Json.toJson(
          InvalidFailureReasonPayload(
            upscanReference,
            failedFileStatusString,
            InvalidFailureDetails(invalidFailureReasonString, testDuplicateFileMessage)
          )
        )
      )

      result.status shouldBe BAD_REQUEST
      result.body   should include(invalidUpscanResultPayloadMessage)
    }

    "return 400 Bad Request when the JSON body is malformed" in {
      val result = postString(fullCallbackPath, invalidJsonBody, JSON)

      result.status shouldBe BAD_REQUEST
    }

    "return 415 Unsupported Media Type when the request body is not JSON" in {
      val result = postString(fullCallbackPath, invalidJsonBody, MimeTypes.TEXT)

      result.status shouldBe UNSUPPORTED_MEDIA_TYPE
    }

    "return 404 Not Found when the service prefix is missing" in {
      val result = postJson(callbackPath, Json.toJson(successfulUploadResult))

      result.status shouldBe NOT_FOUND
    }
  }

  private def failedUploadResult(failureReason: UpscanFailureReason, message: String): UpscanResult =
    UpscanFailure(
      reference = upscanReference,
      failureDetails = UpscanFailureDetails(
        failureReason = failureReason,
        message = message
      )
    )

  private def successfulUploadResultFor(reference: String): UpscanResult =
    UpscanSuccess(
      reference = reference,
      downloadUrl = testDownloadUrl,
      uploadDetails = uploadDetails
    )

  private def fileUpload(monthlyReturn: JsValue, reference: String): JsObject =
    (monthlyReturn \ fileUploadsFieldName)
      .as[Seq[JsValue]]
      .find(upload => (upload \ referenceFieldName).as[String] == reference)
      .getOrElse(fail(s"File upload [$reference] was not found"))
      .as[JsObject]

  private def postUploadResult(body: UpscanResult) =
    postJson(fullCallbackPath, Json.toJson(body))

  private final case class InvalidFileStatusPayload(reference: String, fileStatus: String)

  private object InvalidFileStatusPayload {
    implicit val writes: OWrites[InvalidFileStatusPayload] = Json.writes[InvalidFileStatusPayload]
  }

  private final case class InvalidFailureReasonPayload(
    reference: String,
    fileStatus: String,
    failureDetails: InvalidFailureDetails
  )

  private object InvalidFailureReasonPayload {
    implicit val writes: OWrites[InvalidFailureReasonPayload] = Json.writes[InvalidFailureReasonPayload]
  }

  private final case class InvalidFailureDetails(failureReason: String, message: String)

  private object InvalidFailureDetails {
    implicit val writes: OWrites[InvalidFailureDetails] = Json.writes[InvalidFailureDetails]
  }
}
