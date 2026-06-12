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

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import play.api.http.Status.{ACCEPTED, CREATED, OK}
import play.api.libs.json.{JsObject, JsValue, Json}
import uk.gov.hmrc.disareturnsbackend.WireMockIntegrationSpec
import uk.gov.hmrc.disareturnsbackend.models.{UpscanDetails, UpscanResult, UpscanSuccess}
import uk.gov.hmrc.disareturnsbackend.utils.Constants.{CSV_MIME_TYPE, XLSX_MIME_TYPE}
import uk.gov.hmrc.disareturnsbackend.utils.{ObjectStoreWireMockStubs, UpscanWireMockStubs}

import java.io.ByteArrayInputStream
import scala.util.Using

class MonthlyReturnFileUploadProcessingISpec
    extends WireMockIntegrationSpec
    with ObjectStoreWireMockStubs
    with UpscanWireMockStubs {

  private val taxYear = "2026-27"
  private val month   = 5

  private val validationFieldName                    = "validation"
  private val rowsValidatedFieldName                 = "rowsValidated"
  private val validationErrorsFieldName              = "validationErrors"
  private val inlineErrorsFieldName                  = "inlineErrors"
  private val rowNumberFieldName                     = "rowNumber"
  private val errorCodesFieldName                    = "errorCodes"
  private val objectStoreFileLocationFieldName       = "objectStoreFileLocation"
  private val objectStoreFileErrorsLocationFieldName = "objectStoreFileErrorsLocation"

  private val validationSuccessValue = "ValidationSuccess"
  private val validationFailedValue  = "ValidationFailed"
  private val invalidFileValue       = "InvalidFile"

  private val expectedRowsValidated         = 1L
  private val expectedNoValidationErrors    = 0L
  private val expectedRowValidationErrors   = 3L
  private val expectedEmptyRowErrors        = 11L
  private val expectedErrorWorkbookRow      = 1.0
  private val expectedInlineErrorRow        = 1L
  private val configuredInlineErrorLimit    = 2
  private val expectedRowsValidatedForLimit = 3L
  private val expectedValidationErrorsLimit = 9L

  private val expectedInlineErrorCodes = Seq(
    invalidNationalInsuranceErrorCode,
    invalidFirstNameErrorCode,
    invalidCurrentYearSubscriptionsErrorCode
  )
  private val expectedRowLevelErrorCodes = expectedInlineErrorCodes.mkString(";")
  private val expectedEmptyRowInlineErrors = Seq(
    accountNumberRequiredErrorCode,
    nationalInsuranceRequiredErrorCode,
    firstNameRequiredErrorCode,
    surnameRequiredErrorCode,
    dateOfBirthRequiredErrorCode,
    isaTypeRequiredErrorCode,
    transferInRequiredErrorCode,
    transferOutRequiredErrorCode,
    lastSubscriptionRequiredErrorCode,
    currentYearSubscriptionsRequiredErrorCode,
    marketValueRequiredErrorCode
  )
  private val expectedEmptyRowErrorCodes = expectedEmptyRowInlineErrors.mkString(";")
  private val errorsWorkbookSheetName       = "Errors"
  private val rowNumberHeader               = "RowNumber"
  private val errorCodesHeader              = "ErrorCodes"
  private val uploadedFileSize              = 123L

  private val validFixtures = Seq(
    csvFixture("ISA open valid", "isa-open-valid"),
    xlsxFixture("ISA open valid", "isa-open-valid"),
    csvFixture("ISA closed valid", "isa-closed-valid"),
    xlsxFixture("ISA closed valid", "isa-closed-valid"),
    csvFixture("LISA open valid", "lisa-open-valid"),
    xlsxFixture("LISA open valid", "lisa-open-valid"),
    csvFixture("LISA closed valid", "lisa-closed-valid"),
    xlsxFixture("LISA closed valid", "lisa-closed-valid")
  )

  private val rowErrorsInvalidFixtures = Seq(
    csvFixture("row-level errors invalid", "row-errors-invalid"),
    xlsxFixture("row-level errors invalid", "row-errors-invalid")
  )

  private val emptyRowInvalidFixtures = Seq(
    csvFixture("empty row invalid", "empty-row-invalid"),
    xlsxFixture("empty row invalid", "empty-row-invalid")
  )

  private val inlineErrorLimitExceededFixtures = Seq(
    csvFixture("inline error limit exceeded invalid", "inline-error-limit-exceeded-invalid"),
    xlsxFixture("inline error limit exceeded invalid", "inline-error-limit-exceeded-invalid")
  )

  private val invalidFileFixtures = Seq(
    csvFixture("invalid header", "invalid-header"),
    xlsxFixture("invalid header", "invalid-header"),
    csvFixture("blank invalid", "blank-invalid"),
    xlsxFixture("blank invalid", "blank-invalid"),
    csvFixture("garbage invalid", "garbage-invalid"),
    xlsxFixture("garbage invalid", "garbage-invalid")
  )

  "monthly return file-upload processing" should {

    validFixtures.foreach { fixture =>
      s"process ${fixture.scenarioName} successfully" in {
        assertValidFileProcessed(fixture)
      }
    }

    rowErrorsInvalidFixtures.foreach { fixture =>
      s"process ${fixture.scenarioName} with row-level validation errors and upload an errors workbook" in {
        assertRowErrorsFileProcessed(fixture)
      }
    }

    emptyRowInvalidFixtures.foreach { fixture =>
      s"process ${fixture.scenarioName} as row-level validation failure" in {
        assertEmptyRowInvalidFileProcessed(fixture)
      }
    }

    inlineErrorLimitExceededFixtures.foreach { fixture =>
      s"process ${fixture.scenarioName} with inline errors capped and full errors workbook uploaded" in {
        assertInlineErrorLimitExceededFileProcessed(fixture)
      }
    }

    invalidFileFixtures.foreach { fixture =>
      s"process ${fixture.scenarioName} as InvalidFile without uploading an errors workbook" in {
        assertInvalidFileProcessed(fixture)
      }
    }
  }

  private def assertValidFileProcessed(fixture: UploadFixture): Unit = {
    val scenario = createScenario(fixture)

    createReturnUploadAndSendReadyCallback(scenario)

    val fileUpload = eventuallyProcessedFileUpload(scenario)
    val details    = (fileUpload \ fileUploadDetailsFieldName).as[JsObject]
    val validation = (details \ validationFieldName).as[JsObject]

    (fileUpload \ statusFieldName).as[String] shouldBe validationSuccessStatusString
    (validation \ statusFieldName).as[String] shouldBe validationSuccessValue
    (validation \ rowsValidatedFieldName).as[Long] shouldBe expectedRowsValidated
    (validation \ validationErrorsFieldName).as[Long] shouldBe expectedNoValidationErrors
    (details \ objectStoreFileLocationFieldName).as[String] shouldBe scenario.reference
    (details \ objectStoreFileErrorsLocationFieldName).toOption shouldBe None
    verifyObjectStorePut(scenario.reference)
    verifyObjectStorePutNotMade(scenario.errorsReference)
    if (fixture.mimeType == CSV_MIME_TYPE) {
      objectStorePutRequestBody(scenario.reference) shouldBe readResourceBytes(fixture.resource)
    }
  }

  private def assertRowErrorsFileProcessed(fixture: UploadFixture): Unit = {
    val scenario = createScenario(fixture)

    createReturnUploadAndSendReadyCallback(scenario, expectErrorsUpload = true)

    assertValidationFailedWithErrorsWorkbook(scenario)
  }

  private def assertEmptyRowInvalidFileProcessed(fixture: UploadFixture): Unit = {
    val scenario = createScenario(fixture)

    createReturnUploadAndSendReadyCallback(scenario, expectErrorsUpload = true)

    val fileUpload = eventuallyProcessedFileUpload(scenario)
    val details    = (fileUpload \ fileUploadDetailsFieldName).as[JsObject]
    val validation = (details \ validationFieldName).as[JsObject]

    (fileUpload \ statusFieldName).as[String] shouldBe validationFailureStatusString
    (validation \ statusFieldName).as[String] shouldBe validationFailedValue
    (validation \ rowsValidatedFieldName).as[Long] shouldBe expectedRowsValidated
    (validation \ validationErrorsFieldName).as[Long] shouldBe expectedEmptyRowErrors

    val inlineErrors = (validation \ inlineErrorsFieldName).as[Seq[JsObject]]
    inlineErrors.size shouldBe 1
    (inlineErrors.head \ rowNumberFieldName).as[Long] shouldBe expectedInlineErrorRow
    (inlineErrors.head \ errorCodesFieldName).as[Seq[String]] shouldBe expectedEmptyRowInlineErrors

    (details \ objectStoreFileLocationFieldName).as[String] shouldBe scenario.reference
    (details \ objectStoreFileErrorsLocationFieldName).as[String] shouldBe scenario.errorsReference
    verifyObjectStorePut(scenario.reference)
    verifyObjectStorePut(scenario.errorsReference)
    assertErrorsWorkbookContainsRows(scenario, Seq(expectedErrorWorkbookRow -> expectedEmptyRowErrorCodes))
  }

  private def assertInlineErrorLimitExceededFileProcessed(fixture: UploadFixture): Unit = {
    val scenario = createScenario(fixture)

    createReturnUploadAndSendReadyCallback(scenario, expectErrorsUpload = true)

    val fileUpload = eventuallyProcessedFileUpload(scenario)
    val details    = (fileUpload \ fileUploadDetailsFieldName).as[JsObject]
    val validation = (details \ validationFieldName).as[JsObject]

    (fileUpload \ statusFieldName).as[String] shouldBe validationFailureStatusString
    (validation \ statusFieldName).as[String] shouldBe validationFailedValue
    (validation \ rowsValidatedFieldName).as[Long] shouldBe expectedRowsValidatedForLimit
    (validation \ validationErrorsFieldName).as[Long] shouldBe expectedValidationErrorsLimit

    val inlineErrors = (validation \ inlineErrorsFieldName).as[Seq[JsObject]]
    inlineErrors.size shouldBe configuredInlineErrorLimit
    (inlineErrors.head \ rowNumberFieldName).as[Long] shouldBe 1L
    (inlineErrors.head \ errorCodesFieldName).as[Seq[String]] shouldBe expectedInlineErrorCodes
    (inlineErrors(1) \ rowNumberFieldName).as[Long] shouldBe 2L
    (inlineErrors(1) \ errorCodesFieldName).as[Seq[String]] shouldBe expectedInlineErrorCodes

    (details \ objectStoreFileLocationFieldName).as[String] shouldBe scenario.reference
    (details \ objectStoreFileErrorsLocationFieldName).as[String] shouldBe scenario.errorsReference
    verifyObjectStorePut(scenario.reference)
    verifyObjectStorePut(scenario.errorsReference)
    assertErrorsWorkbookContainsRows(
      scenario,
      Seq(
        1.0 -> expectedRowLevelErrorCodes,
        2.0 -> expectedRowLevelErrorCodes,
        3.0 -> expectedRowLevelErrorCodes
      )
    )
  }

  private def assertInvalidFileProcessed(fixture: UploadFixture): Unit = {
    val scenario = createScenario(fixture)

    createReturnUploadAndSendReadyCallback(scenario)

    assertInvalidFileWithoutErrorsWorkbook(scenario)
  }

  private def createReturnUploadAndSendReadyCallback(
    scenario: FileScenario,
    expectErrorsUpload: Boolean = false
  ): Unit = {
    postJson(scenario.monthlyPath, nilReturnFalseRequest).status shouldBe CREATED
    postJson(scenario.filesPath, Json.obj(referenceFieldName -> scenario.reference)).status shouldBe CREATED

    stubObjectStorePut(scenario.reference)
    if (expectErrorsUpload) {
      stubObjectStorePut(scenario.errorsReference)
    }

    postJson(scenario.callbackPath, Json.toJson(createSuccessfulUploadResult(scenario))).status shouldBe ACCEPTED
  }

  private def assertValidationFailedWithErrorsWorkbook(scenario: FileScenario): Unit = {
    val fileUpload = eventuallyProcessedFileUpload(scenario)
    val details    = (fileUpload \ fileUploadDetailsFieldName).as[JsObject]
    val validation = (details \ validationFieldName).as[JsObject]

    (fileUpload \ statusFieldName).as[String] shouldBe validationFailureStatusString
    (validation \ statusFieldName).as[String] shouldBe validationFailedValue
    (validation \ rowsValidatedFieldName).as[Long] shouldBe expectedRowsValidated
    (validation \ validationErrorsFieldName).as[Long] shouldBe expectedRowValidationErrors
    val inlineError = (validation \ inlineErrorsFieldName).as[Seq[JsObject]].head
    (inlineError \ rowNumberFieldName).as[Long] shouldBe expectedInlineErrorRow
    (inlineError \ errorCodesFieldName).as[Seq[String]] shouldBe expectedInlineErrorCodes
    (details \ objectStoreFileLocationFieldName).as[String] shouldBe scenario.reference
    (details \ objectStoreFileErrorsLocationFieldName).as[String] shouldBe scenario.errorsReference
    verifyObjectStorePut(scenario.reference)
    verifyObjectStorePut(scenario.errorsReference)
    assertErrorsWorkbookContainsRows(scenario, Seq(expectedErrorWorkbookRow -> expectedRowLevelErrorCodes))
  }

  private def assertInvalidFileWithoutErrorsWorkbook(scenario: FileScenario): Unit = {
    val fileUpload = eventuallyProcessedFileUpload(scenario)
    val details    = (fileUpload \ fileUploadDetailsFieldName).as[JsObject]
    val validation = (details \ validationFieldName).as[JsObject]

    (fileUpload \ statusFieldName).as[String] shouldBe validationFailureStatusString
    (validation \ statusFieldName).as[String] shouldBe invalidFileValue
    (validation \ rowsValidatedFieldName).as[Long] shouldBe expectedNoValidationErrors
    (validation \ validationErrorsFieldName).as[Long] shouldBe expectedNoValidationErrors
    (validation \ inlineErrorsFieldName).asOpt[Seq[JsObject]].getOrElse(Nil) shouldBe Nil
    (details \ objectStoreFileLocationFieldName).as[String] shouldBe scenario.reference
    (details \ objectStoreFileErrorsLocationFieldName).toOption shouldBe None
    verifyObjectStorePut(scenario.reference)
    verifyObjectStorePutNotMade(scenario.errorsReference)
  }

  private def eventuallyProcessedFileUpload(scenario: FileScenario): JsObject =
    eventually {
      val result = get(scenario.monthlyPath)
      result.status shouldBe OK

      val fileUpload = (result.json \ fileUploadsFieldName)
        .as[Seq[JsValue]]
        .find(upload => (upload \ referenceFieldName).as[String] == scenario.reference)
        .getOrElse(fail(s"File upload [${scenario.reference}] was not found"))
        .as[JsObject]

      withClue(
        s"fileUpload [$fileUpload], original PUTs [${objectStorePutRequests(scenario.reference).size}], errors PUTs [${objectStorePutRequests(scenario.errorsReference).size}]"
      ) {
        (fileUpload \ fileUploadDetailsFieldName \ validationFieldName).toOption should not be empty
      }
      fileUpload
    }

  private def createSuccessfulUploadResult(scenario: FileScenario): UpscanResult =
    UpscanSuccess(
      reference = scenario.reference,
      downloadUrl = scenario.downloadUrl,
      uploadDetails = UpscanDetails(
        fileName = scenario.fileName,
        fileMimeType = scenario.fileMimeType,
        uploadTimestamp = testCreatedOn,
        checksum = testChecksum,
        size = uploadedFileSize
      )
    )

  private def assertErrorsWorkbookContainsRows(scenario: FileScenario, expectedRows: Seq[(Double, String)]): Unit = {
    val errorsWorkbookBytes = objectStorePutRequestBody(scenario.errorsReference)

    Using.resource(new XSSFWorkbook(new ByteArrayInputStream(errorsWorkbookBytes))) { workbook =>
      val sheet = workbook.getSheet(errorsWorkbookSheetName)
      sheet.getRow(0).getCell(0).getStringCellValue shouldBe rowNumberHeader
      sheet.getRow(0).getCell(1).getStringCellValue shouldBe errorCodesHeader

      expectedRows.zipWithIndex.foreach { case ((expectedRowNumber, expectedErrorCodes), index) =>
        val workbookRow = sheet.getRow(index + 1)
        workbookRow.getCell(0).getNumericCellValue shouldBe expectedRowNumber
        workbookRow.getCell(1).getStringCellValue shouldBe expectedErrorCodes
      }
    }
  }

  private def createScenario(fixture: UploadFixture): FileScenario =
    FileScenario(
      reference = fixture.reference,
      downloadPath = fixture.downloadPath,
      fileName = fixture.fileName,
      fileMimeType = fixture.mimeType,
      downloadUrl = stubUpscanDownload(fixture.downloadPath, fixture.resource, fixture.mimeType)
    )

  private def csvFixture(scenarioName: String, baseName: String): UploadFixture =
    uploadFixture(scenarioName, baseName, "csv", CSV_MIME_TYPE)

  private def xlsxFixture(scenarioName: String, baseName: String): UploadFixture =
    uploadFixture(scenarioName, baseName, "xlsx", XLSX_MIME_TYPE)

  private def uploadFixture(scenarioName: String, baseName: String, extension: String, mimeType: String): UploadFixture = {
    val fileName = s"$baseName.$extension"

    UploadFixture(
      scenarioName = s"$scenarioName $extension",
      reference = s"$baseName-$extension-123",
      resource = s"file-upload/monthly/$fileName",
      fileName = fileName,
      mimeType = mimeType,
      downloadPath = s"/upscan/monthly/$fileName"
    )
  }

  private final case class UploadFixture(
    scenarioName: String,
    reference: String,
    resource: String,
    fileName: String,
    mimeType: String,
    downloadPath: String
  )

  private final case class FileScenario(
    reference: String,
    downloadPath: String,
    fileName: String,
    fileMimeType: String,
    downloadUrl: String
  ) {
    val zReference: String      = testZReference
    val monthlyPath: String     = s"$testServicePath/monthly/$zReference/$taxYear/$month"
    val filesPath: String       = s"$monthlyPath/files"
    val callbackPath: String    = s"$testServicePath/monthly/upscan/callback/$zReference/$taxYear/$month"
    val errorsReference: String = s"$reference-errors"
  }
}
