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

package uk.gov.hmrc.disareturnsbackend.validators.fileupload

import base.SpecBase
import org.apache.pekko.actor.ActorSystem
import org.apache.poi.ss.util.CellReference
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import uk.gov.hmrc.disareturnsbackend.models.{FileUploadValidationError, FileUploadValidationStatus}

import java.nio.file.{Files, Path}
import scala.concurrent.Future
import scala.util.Using

class BaseXlsxFileUploadValidatorSpec extends SpecBase {

  private val context                       = TestFileUploadValidationContext()
  private val columnARequiredErrorCode      = "E001"
  private val columnBRequiredErrorCode      = "E002"
  private val incorrectColumnCountErrorCode = "E999"

  "BaseXlsxFileUploadValidator" - {

    "must validate a valid XLSX" in {
      val result = validateWorkbook(rows = Seq(Vector("value-a", "value-b"))).futureValue

      result.validation.status mustBe FileUploadValidationStatus.ValidationSuccess
      result.validation.rowsValidated mustBe 1
      result.validation.validationErrors mustBe 0
      result.validation.inlineErrors mustBe Nil
      result.errorFileWritten mustBe false
    }

    "must return InvalidFile for a missing header" in {
      val result = validateWorkbook(
        headers = TestFileUploadSchema.headers.dropRight(1),
        rows = Seq(Vector("value-a", "value-b"))
      ).futureValue

      result.validation.status mustBe FileUploadValidationStatus.InvalidFile
      result.validation.inlineErrors mustBe Nil
      result.errorFileWritten mustBe false
    }

    "must return InvalidFile for an invalid header" in {
      val result = validateWorkbook(
        headers = TestFileUploadSchema.headers.updated(0, "Wrong"),
        rows = Seq(Vector("value-a", "value-b"))
      ).futureValue

      result.validation.status mustBe FileUploadValidationStatus.InvalidFile
      result.errorFileWritten mustBe false
    }

    "must not write an errors workbook for an invalid header" in {
      val errorsFile = Files.createTempDirectory("disa-errors-dir").resolve("errors.xlsx")

      val result = validateWorkbook(
        headers = TestFileUploadSchema.headers.updated(0, "Wrong"),
        rows = Seq(Vector("value-a", "value-b")),
        errorsFile = errorsFile
      ).futureValue

      result.validation.status mustBe FileUploadValidationStatus.InvalidFile
      Files.exists(errorsFile) mustBe false
      Files.deleteIfExists(errorsFile.getParent)
    }

    "must return InvalidFile for corrupt non-XLSX input" in {
      val validator  = new TestXlsxFileUploadValidator(inject[ActorSystem], new TestRowValidator)
      val file       = Files.createTempFile("disa-upload", ".xlsx")
      val errorsFile = Files.createTempDirectory("disa-errors-dir").resolve("errors.xlsx")
      Files.writeString(file, "not an xlsx file")

      try {
        val result = validator.validate(file, errorsFile, context).futureValue

        result.validation.status mustBe FileUploadValidationStatus.InvalidFile
        result.errorFileWritten mustBe false
        Files.exists(errorsFile) mustBe false
      } finally {
        Files.deleteIfExists(file)
        Files.deleteIfExists(errorsFile)
        Files.deleteIfExists(errorsFile.getParent)
      }
    }

    "must fail the Future when row validation throws unexpectedly" in {
      val validator = new TestXlsxFileUploadValidator(inject[ActorSystem], new ThrowingRowValidator)

      validateWorkbook(
        rows = Seq(Vector("value-a", "value-b")),
        validator = validator
      ).failed.futureValue.getMessage mustBe "validator failed"
    }

    "must short-circuit invalid headers before validating rows" in {
      val rowValidator = new CountingRowValidator
      val validator    = new TestXlsxFileUploadValidator(inject[ActorSystem], rowValidator)

      val result = validateWorkbook(
        headers = TestFileUploadSchema.headers.updated(0, "Wrong"),
        rows = Seq(Vector("", "value-b")),
        validator = validator
      ).futureValue

      result.validation.status mustBe FileUploadValidationStatus.InvalidFile
      rowValidator.calls mustBe 0
    }

    "must return InvalidFile for a header-only workbook" in {
      val result = validateWorkbook(rows = Nil).futureValue

      result.validation.status mustBe FileUploadValidationStatus.InvalidFile
      result.validation.rowsValidated mustBe 0
    }

    "must return InvalidFile without writing an errors workbook for a workbook with no sheets" in {
      val errorsFile = Files.createTempDirectory("disa-errors-dir").resolve("errors.xlsx")

      val result = validateWorkbook(rows = Nil, errorsFile = errorsFile, createPrimarySheet = false).futureValue

      result.validation.status mustBe FileUploadValidationStatus.InvalidFile
      result.errorFileWritten mustBe false
      Files.exists(errorsFile) mustBe false
      Files.deleteIfExists(errorsFile.getParent)
    }

    "must return InvalidFile without writing an errors workbook for a workbook with more than one sheet" in {
      val errorsFile = Files.createTempDirectory("disa-errors-dir").resolve("errors.xlsx")

      val result = validateWorkbook(
        rows = Seq(Vector("value-a", "value-b")),
        errorsFile = errorsFile,
        addExtraSheet = true
      ).futureValue

      result.validation.status mustBe FileUploadValidationStatus.InvalidFile
      result.errorFileWritten mustBe false
      Files.exists(errorsFile) mustBe false
      Files.deleteIfExists(errorsFile.getParent)
    }

    "must return InvalidFile without writing an errors workbook for a header-only workbook" in {
      val errorsFile = Files.createTempDirectory("disa-errors-dir").resolve("errors.xlsx")

      val result = validateWorkbook(rows = Nil, errorsFile = errorsFile).futureValue

      result.validation.status mustBe FileUploadValidationStatus.InvalidFile
      result.errorFileWritten mustBe false
      Files.exists(errorsFile) mustBe false
      Files.deleteIfExists(errorsFile.getParent)
    }

    "must write an errors workbook for an invalid data row" in {
      val errorsFile = Files.createTempFile("disa-errors", ".xlsx")

      try {
        val result = validateWorkbook(
          rows = Seq(Vector("", "value-b")),
          errorsFile = errorsFile,
          deleteErrorsFile = false
        ).futureValue

        result.validation.status mustBe FileUploadValidationStatus.ValidationFailed
        result.validation.rowsValidated mustBe 1
        result.validation.validationErrors mustBe 1
        result.validation.inlineErrors mustBe List(FileUploadValidationError(1, List(columnARequiredErrorCode)))
        result.errorFileWritten mustBe true

        Using.resource(new XSSFWorkbook(Files.newInputStream(errorsFile))) { workbook =>
          val sheet = workbook.getSheet("Errors")
          sheet.getRow(0).getCell(0).getStringCellValue mustBe "RowNumber"
          sheet.getRow(0).getCell(1).getStringCellValue mustBe "ErrorCodes"
          sheet.getRow(1).getCell(0).getNumericCellValue mustBe 1.0
          sheet.getRow(1).getCell(1).getStringCellValue mustBe columnARequiredErrorCode
        }
      } finally Files.deleteIfExists(errorsFile)
    }

    "must fail the Future when writing the errors workbook fails" in {
      val errorsFile = Files.createTempDirectory("disa-errors-directory")

      try
        validateWorkbook(rows = Seq(Vector("", "value-b")), errorsFile = errorsFile).failed.futureValue mustBe a[
          java.io.IOException
        ]
      finally
        Files.deleteIfExists(errorsFile)
    }

    "must store all error codes for an inline invalid row" in {
      val result = validateWorkbook(rows = Seq(Vector("", ""))).futureValue

      result.validation.inlineErrors mustBe List(
        FileUploadValidationError(1, List(columnARequiredErrorCode, columnBRequiredErrorCode))
      )
      result.validation.validationErrors mustBe 2
    }

    "must respect configured max inline error rows and still write all workbook rows" in {
      val errorsFile = Files.createTempFile("disa-errors", ".xlsx")
      val validator  = new TestXlsxFileUploadValidator(inject[ActorSystem], new TestRowValidator, maxInlineErrors = 2)

      try {
        val result = validateWorkbook(
          rows = Seq(Vector("", "value-b"), Vector("", "value-b"), Vector("", "")),
          errorsFile = errorsFile,
          validator = validator,
          deleteErrorsFile = false
        ).futureValue

        result.validation.inlineErrors mustBe List(
          FileUploadValidationError(1, List(columnARequiredErrorCode)),
          FileUploadValidationError(2, List(columnARequiredErrorCode))
        )
        result.validation.validationErrors mustBe 4
        result.errorVolumes mustBe Map(columnARequiredErrorCode -> 3L, columnBRequiredErrorCode -> 1L)
        workbookErrorRows(errorsFile) mustBe 3
      } finally Files.deleteIfExists(errorsFile)
    }

    "must store no inline errors when maxInlineErrors is zero" in {
      val validator = new TestXlsxFileUploadValidator(inject[ActorSystem], new TestRowValidator, maxInlineErrors = 0)
      val result    = validateWorkbook(rows = Seq(Vector("", "value-b")), validator = validator).futureValue

      result.validation.inlineErrors mustBe Nil
      result.validation.validationErrors mustBe 1
    }

    "must fail the Future when row validation throws after an errors workbook row was written" in {
      val validator = new TestXlsxFileUploadValidator(inject[ActorSystem], new ErrorThenThrowingRowValidator)

      validateWorkbook(
        rows = Seq(Vector("", "value-b"), Vector("value-a", "value-b")),
        validator = validator
      ).failed.futureValue.getMessage mustBe
        "validator failed after error row"
    }

    "must cap sparse far-right cells without huge row allocation" in {
      val result = validateWorkbook(
        rows = Seq(Vector("value-a", "value-b")),
        farRightCell = Some("XFD3" -> "unexpected")
      ).futureValue

      result.validation.status mustBe FileUploadValidationStatus.ValidationFailed
      result.validation.rowsValidated mustBe 1
      result.validation.validationErrors mustBe 1
      result.errorFileWritten mustBe true
    }

    "must validate a large workbook through the SAX path" in {
      val rows   = Vector.fill(1000)(Vector("value-a", "value-b"))
      val result = validateWorkbook(rows = rows).futureValue

      result.validation.status mustBe FileUploadValidationStatus.ValidationSuccess
      result.validation.rowsValidated mustBe 1000
      result.validation.inlineErrors mustBe Nil
    }
  }

  private def workbookErrorRows(errorsFile: Path): Int =
    Using.resource(new XSSFWorkbook(Files.newInputStream(errorsFile))) { workbook =>
      workbook.getSheet("Errors").getLastRowNum
    }

  private def validateWorkbook(
    headers: Vector[String] = TestFileUploadSchema.headers,
    rows: Seq[Vector[String]],
    errorsFile: Path = Files.createTempFile("disa-errors", ".xlsx"),
    validator: TestXlsxFileUploadValidator = new TestXlsxFileUploadValidator(inject[ActorSystem], new TestRowValidator),
    farRightCell: Option[(String, String)] = None,
    addExtraSheet: Boolean = false,
    createPrimarySheet: Boolean = true,
    deleteErrorsFile: Boolean = true
  ): Future[FileUploadValidatorResult] = {
    val file = Files.createTempFile("disa-upload", ".xlsx")
    writeWorkbook(file, headers, rows, farRightCell, addExtraSheet, createPrimarySheet)

    validator.validate(file, errorsFile, context).andThen { case _ =>
      Files.deleteIfExists(file)
      if (deleteErrorsFile) Files.deleteIfExists(errorsFile)
    }
  }

  private def writeWorkbook(
    file: Path,
    headers: Vector[String],
    rows: Seq[Vector[String]],
    farRightCell: Option[(String, String)],
    addExtraSheet: Boolean,
    createPrimarySheet: Boolean
  ): Unit =
    Using.resource(new XSSFWorkbook()) { workbook =>
      if (createPrimarySheet) {
        val sheet     = workbook.createSheet("Test upload")
        sheet.createRow(TestFileUploadSchema.xlsxRuleRowIndexZeroBased).createCell(0).setCellValue("Rules")
        val headerRow = sheet.createRow(TestFileUploadSchema.xlsxHeaderRowIndexZeroBased)
        headers.zipWithIndex.foreach { case (header, index) => headerRow.createCell(index).setCellValue(header) }

        rows.zipWithIndex.foreach { case (values, rowIndex) =>
          val row = sheet.createRow(TestFileUploadSchema.xlsxDataStartRowIndexZeroBased + rowIndex)
          values.zipWithIndex.foreach { case (value, columnIndex) => row.createCell(columnIndex).setCellValue(value) }
          farRightCell.foreach { case (cellReference, value) =>
            row.createCell(new CellReference(cellReference).getCol).setCellValue(value)
          }
        }
      }

      if (addExtraSheet) {
        workbook.createSheet("Extra sheet")
      }

      Using.resource(Files.newOutputStream(file))(workbook.write)
    }

  private final case class TestFileUploadValidationContext() extends FileUploadValidationContext

  private object TestFileUploadSchema extends FileUploadSchema {
    override val headers: Vector[String]             = Vector("Column A", "Column B")
    override val csvHeaderRowIndexZeroBased: Int     = 0
    override val csvDataStartRowIndexZeroBased: Int  = 1
    override val xlsxRuleRowIndexZeroBased: Int      = 0
    override val xlsxHeaderRowIndexZeroBased: Int    = 1
    override val xlsxDataStartRowIndexZeroBased: Int = 2
  }

  private class TestRowValidator extends FileUploadRowValidator[TestFileUploadValidationContext] {
    override def validate(row: FileUploadRow, context: TestFileUploadValidationContext): Seq[String] =
      Seq(
        Option.when(row.valuesByHeader.getOrElse("Column A", "").isEmpty)(columnARequiredErrorCode),
        Option.when(row.valuesByHeader.getOrElse("Column B", "").isEmpty)(columnBRequiredErrorCode),
        Option.when(row.rawColumnCount != TestFileUploadSchema.headers.length)(incorrectColumnCountErrorCode)
      ).flatten
  }

  private final class CountingRowValidator extends TestRowValidator {
    var calls: Int = 0

    override def validate(row: FileUploadRow, context: TestFileUploadValidationContext): Seq[String] = {
      calls += 1
      super.validate(row, context)
    }
  }

  private final class ThrowingRowValidator extends TestRowValidator {
    override def validate(row: FileUploadRow, context: TestFileUploadValidationContext): Seq[String] =
      throw new RuntimeException("validator failed")
  }

  private final class ErrorThenThrowingRowValidator extends TestRowValidator {
    override def validate(row: FileUploadRow, context: TestFileUploadValidationContext): Seq[String] =
      if (row.rowNumber == 1) {
        Seq(columnARequiredErrorCode)
      } else {
        throw new RuntimeException("validator failed after error row")
      }
  }

  private final class TestXlsxFileUploadValidator(
    actorSystem: ActorSystem,
    rowValidator: FileUploadRowValidator[TestFileUploadValidationContext],
    maxInlineErrors: Int = 25
  ) extends BaseXlsxFileUploadValidator[TestFileUploadValidationContext](
        actorSystem,
        rowValidator,
        TestFileUploadSchema,
        maxInlineErrors
      )
}
