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

package uk.gov.hmrc.disareturnsbackend.validators.fileupload.monthly

import base.SpecBase
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import uk.gov.hmrc.disareturnsbackend.models.FileUploadValidationStatus
import uk.gov.hmrc.disareturnsbackend.validators.fileupload.FileUploadValidatorResult

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.concurrent.Future
import scala.util.Using

class MonthlyCsvFileUploadValidatorSpec extends SpecBase {

  private val validator                               = inject[MonthlyCsvFileUploadValidator]
  private val context                                 = MonthlyReturnFileUploadValidationContext("2026-27", 5)
  private val expectedRowValidationWorkbookErrorCodes = Seq(
    accountNumberRequiredErrorCode,
    invalidNationalInsuranceErrorCode,
    invalidCurrentYearSubscriptionsErrorCode
  ).mkString(";")

  "MonthlyCsvFileUploadValidator" - {

    "must validate a valid CSV" in {
      val result = validateCsv(csv(validDataRow)).futureValue

      result.validation.status mustBe FileUploadValidationStatus.ValidationSuccess
      result.validation.rowsValidated mustBe 1
      result.validation.validationErrors mustBe 0
      result.errorFileWritten mustBe false
    }

    "must return InvalidFile for missing header column" in {
      val result =
        validateCsv(csv(Seq(validDataRow), headers = MonthlyFileUploadTemplate.headers.dropRight(1))).futureValue

      result.validation.status mustBe FileUploadValidationStatus.InvalidFile
      result.errorFileWritten mustBe false
    }

    "must return InvalidFile for header only CSV" in {
      val result = validateCsv(MonthlyFileUploadTemplate.headers.mkString(",")).futureValue

      result.validation.status mustBe FileUploadValidationStatus.InvalidFile
      result.validation.rowsValidated mustBe 0
    }

    "must write an errors workbook for row validation errors" in {
      val invalidRow = validDataRow.updated(0, "").updated(1, "bad-nino").updated(12, "20A")
      val errorsFile = Files.createTempFile("disa-errors", ".xlsx")

      try {
        val result = validateCsv(csv(invalidRow), errorsFile).futureValue

        result.validation.status mustBe FileUploadValidationStatus.ValidationFailed
        result.validation.rowsValidated mustBe 1
        result.validation.validationErrors mustBe 3
        result.errorFileWritten mustBe true

        Using.resource(new XSSFWorkbook(Files.newInputStream(errorsFile))) { workbook =>
          val sheet = workbook.getSheet("Errors")
          sheet.getRow(0).getCell(0).getStringCellValue mustBe "RowNumber"
          sheet.getRow(0).getCell(1).getStringCellValue mustBe "ErrorCodes"
          sheet.getRow(1).getCell(0).getNumericCellValue mustBe 1.0
          sheet.getRow(1).getCell(1).getStringCellValue mustBe expectedRowValidationWorkbookErrorCodes
        }
      } finally Files.deleteIfExists(errorsFile)
    }

    "must validate generated rows without accumulating row errors" in {
      val rows   = Vector.fill(500)(validDataRow)
      val result = validateCsv(csv(rows)).futureValue

      result.validation.status mustBe FileUploadValidationStatus.ValidationSuccess
      result.validation.rowsValidated mustBe 500
    }
  }

  private def validateCsv(content: String): scala.concurrent.Future[FileUploadValidatorResult] =
    validateCsv(content, Files.createTempFile("disa-errors", ".xlsx"), deleteErrorsFile = true)

  private def validateCsv(content: String, errorsFile: Path): Future[FileUploadValidatorResult] =
    validateCsv(content, errorsFile, deleteErrorsFile = false)

  private def validateCsv(
    content: String,
    errorsFile: Path,
    deleteErrorsFile: Boolean
  ): Future[FileUploadValidatorResult] = {
    val file = Files.createTempFile("disa-upload", ".csv")
    Files.writeString(file, content, StandardCharsets.UTF_8)
    validator.validate(file, errorsFile, context).andThen { case _ =>
      Files.deleteIfExists(file)
      if (deleteErrorsFile) Files.deleteIfExists(errorsFile)
    }
  }

  private def csv(rows: Vector[String]*): String =
    csv(rows, MonthlyFileUploadTemplate.headers)

  private def csv(rows: Seq[Vector[String]], headers: Vector[String] = MonthlyFileUploadTemplate.headers): String =
    (headers.mkString(",") +: rows.map(_.mkString(","))).mkString("\n")

  private val validDataRow: Vector[String] = Vector(
    "ACC123",
    "AA123456A",
    "First",
    "Middle",
    "Surname",
    "1990-01-01",
    "CASH",
    "Yes",
    "10.00",
    "0.00",
    "",
    "2026-05-12",
    "20.00",
    "",
    "",
    "100.00",
    "",
    "",
    ""
  )
}
