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

import uk.gov.hmrc.disareturnsbackend.validators.fileupload.FileUploadRow
import uk.gov.hmrc.disareturnsbackend.validators.fileupload.FileUploadValidatorResult

import base.SpecBase
import org.apache.pekko.actor.ActorSystem
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{never, verify}
import uk.gov.hmrc.disareturnsbackend.config.AppConfig
import uk.gov.hmrc.disareturnsbackend.models.FileUploadValidationStatus

import java.nio.file.{Files, Path}
import scala.concurrent.Future
import scala.util.Using

class MonthlyXlsxFileUploadValidatorSpec extends SpecBase {

  private val context                                 = MonthlyReturnFileUploadValidationContext("2026-27", 5)
  private val expectedRowValidationWorkbookErrorCodes = Seq(
    accountNumberRequiredErrorCode,
    invalidNationalInsuranceErrorCode,
    invalidCurrentYearSubscriptionsErrorCode
  ).mkString(";")

  "MonthlyXlsxFileUploadValidator" - {

    "must validate a valid XLSX" in {
      val result = validateWorkbook(validDataRow).futureValue

      result.validation.status mustBe FileUploadValidationStatus.ValidationSuccess
      result.validation.rowsValidated mustBe 1
      result.validation.validationErrors mustBe 0
      result.errorFileWritten mustBe false
    }

    "must return InvalidFile for an invalid header and not process later rows" in {
      val rowValidator = mock[MonthlyFileUploadRowValidator]
      val validator    = new MonthlyXlsxFileUploadValidator(inject[ActorSystem], rowValidator, inject[AppConfig])

      val result = validateWorkbook(
        rows = Seq(validDataRow.updated(0, "")),
        headers = MonthlyFileUploadTemplate.headers.updated(0, "Wrong Header"),
        validator = validator
      ).futureValue

      result.validation.status mustBe FileUploadValidationStatus.InvalidFile
      result.errorFileWritten mustBe false
      verify(rowValidator, never()).validate(any[FileUploadRow], any[MonthlyReturnFileUploadValidationContext])
    }

    "must write an errors workbook for invalid data rows" in {
      val errorsFile = Files.createTempFile("disa-errors", ".xlsx")

      try {
        val result = validateWorkbook(
          Seq(validDataRow.updated(0, "").updated(1, "bad-nino").updated(12, "20A")),
          errorsFile = errorsFile,
          deleteErrorsFile = false
        ).futureValue

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

    "must cap sparse far-right cells without huge row allocation" in {
      val result = validateWorkbook(Seq(validDataRow), farRightCell = Some("XFD3" -> "unexpected")).futureValue

      result.validation.status mustBe FileUploadValidationStatus.ValidationFailed
      result.validation.rowsValidated mustBe 1
      result.validation.validationErrors mustBe 1
      result.errorFileWritten mustBe true
    }
  }

  private def validateWorkbook(row: Vector[String]): Future[FileUploadValidatorResult] =
    validateWorkbook(Seq(row))

  private def validateWorkbook(
    rows: Seq[Vector[String]],
    headers: Vector[String] = MonthlyFileUploadTemplate.headers,
    errorsFile: Path = Files.createTempFile("disa-errors", ".xlsx"),
    validator: MonthlyXlsxFileUploadValidator = inject[MonthlyXlsxFileUploadValidator],
    farRightCell: Option[(String, String)] = None,
    deleteErrorsFile: Boolean = true
  ): Future[FileUploadValidatorResult] = {
    val file = Files.createTempFile("disa-upload", ".xlsx")
    writeWorkbook(file, headers, rows, farRightCell)

    validator.validate(file, errorsFile, context).andThen { case _ =>
      Files.deleteIfExists(file)
      if (deleteErrorsFile) Files.deleteIfExists(errorsFile)
    }
  }

  private def writeWorkbook(
    file: Path,
    headers: Vector[String],
    rows: Seq[Vector[String]],
    farRightCell: Option[(String, String)]
  ): Unit =
    Using.resource(new XSSFWorkbook()) { workbook =>
      val sheet     = workbook.createSheet("Monthly return")
      sheet.createRow(MonthlyFileUploadTemplate.xlsxRuleRowIndexZeroBased).createCell(0).setCellValue("Rules")
      val headerRow = sheet.createRow(MonthlyFileUploadTemplate.xlsxHeaderRowIndexZeroBased)
      headers.zipWithIndex.foreach { case (header, index) => headerRow.createCell(index).setCellValue(header) }

      rows.zipWithIndex.foreach { case (values, rowIndex) =>
        val row = sheet.createRow(MonthlyFileUploadTemplate.xlsxDataStartRowIndexZeroBased + rowIndex)
        values.zipWithIndex.foreach { case (value, columnIndex) => row.createCell(columnIndex).setCellValue(value) }
        farRightCell.foreach { case (cellReference, value) =>
          row
            .createCell(
              org.apache.poi.ss.util.CellReference.convertColStringToIndex(cellReference.takeWhile(_.isLetter))
            )
            .setCellValue(value)
        }
      }

      Using.resource(Files.newOutputStream(file))(workbook.write)
    }

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
