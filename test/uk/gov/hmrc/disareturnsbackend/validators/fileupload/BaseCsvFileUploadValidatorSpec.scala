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
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import uk.gov.hmrc.disareturnsbackend.models.{FileUploadValidationError, FileUploadValidationStatus}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.concurrent.Future
import scala.util.Using

class BaseCsvFileUploadValidatorSpec extends SpecBase {

  private val context                  = TestFileUploadValidationContext()
  private val validator                = new TestCsvFileUploadValidator(inject[ActorSystem], new TestRowValidator)
  private val columnARequiredErrorCode = "E001"
  private val columnBRequiredErrorCode = "E002"

  "BaseCsvFileUploadValidator" - {

    "must validate a valid CSV" in {
      val result = validateCsv(csv(Vector("value-a", "value-b"))).futureValue

      result.validation.status mustBe FileUploadValidationStatus.ValidationSuccess
      result.validation.rowsValidated mustBe 1
      result.validation.validationErrors mustBe 0
      result.validation.inlineErrors mustBe Nil
      result.errorFileWritten mustBe false
    }

    "must skip rows before the header and between the header and data rows" in {
      val validator =
        new TestCsvFileUploadValidator(inject[ActorSystem], new TestRowValidator, OffsetRowsTestFileUploadSchema)
      val content   = Seq(
        "ignored before header",
        "also ignored before header",
        OffsetRowsTestFileUploadSchema.headers.mkString(","),
        "ignored before data",
        "value-a,value-b"
      ).mkString("\n")

      val result = validateCsv(content, validator = validator).futureValue

      result.validation.status mustBe FileUploadValidationStatus.ValidationSuccess
      result.validation.rowsValidated mustBe 1
      result.errorFileWritten mustBe false
    }

    "must return InvalidFile for a missing header" in {
      val result = validateCsv(
        csv(Seq(Vector("value-a", "value-b")), headers = TestFileUploadSchema.headers.dropRight(1))
      ).futureValue

      result.validation.status mustBe FileUploadValidationStatus.InvalidFile
      result.validation.inlineErrors mustBe Nil
      result.errorFileWritten mustBe false
    }

    "must return InvalidFile for an extra non-empty header column" in {
      val result = validateCsv(
        csv(Seq(Vector("value-a", "value-b")), headers = TestFileUploadSchema.headers :+ "Unexpected")
      ).futureValue

      result.validation.status mustBe FileUploadValidationStatus.InvalidFile
      result.errorFileWritten mustBe false
    }

    "must return InvalidFile for header-only CSV" in {
      val result = validateCsv(TestFileUploadSchema.headers.mkString(",")).futureValue

      result.validation.status mustBe FileUploadValidationStatus.InvalidFile
      result.validation.rowsValidated mustBe 0
    }

    "must write an errors workbook for an invalid data row" in {
      val errorsFile = Files.createTempFile("disa-errors", ".xlsx")

      try {
        val result = validateCsv(csv(Vector("", "value-b")), errorsFile).futureValue

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

    "must count multiple invalid rows" in {
      val result =
        validateCsv(csv(Seq(Vector("", "value-b"), Vector("", "value-b"), Vector("value-a", "value-b")))).futureValue

      result.validation.status mustBe FileUploadValidationStatus.ValidationFailed
      result.validation.rowsValidated mustBe 3
      result.validation.validationErrors mustBe 2
      result.errorFileWritten mustBe true
    }

    "must store all error codes for an inline invalid row" in {
      val result = validateCsv(csv(Vector("", ""))).futureValue

      result.validation.inlineErrors mustBe List(
        FileUploadValidationError(1, List(columnARequiredErrorCode, columnBRequiredErrorCode))
      )
      result.validation.validationErrors mustBe 2
    }

    "must respect configured max inline error rows and still write all workbook rows" in {
      val errorsFile = Files.createTempFile("disa-errors", ".xlsx")
      val validator  = new TestCsvFileUploadValidator(inject[ActorSystem], new TestRowValidator, maxInlineErrors = 2)

      try {
        val result = validateCsv(
          csv(Seq(Vector("", "value-b"), Vector("", "value-b"), Vector("", ""))),
          errorsFile,
          deleteErrorsFile = false,
          validator = validator
        ).futureValue

        result.validation.inlineErrors mustBe List(
          FileUploadValidationError(1, List(columnARequiredErrorCode)),
          FileUploadValidationError(2, List(columnARequiredErrorCode))
        )
        result.validation.validationErrors mustBe 4
        workbookErrorRows(errorsFile) mustBe 3
      } finally Files.deleteIfExists(errorsFile)
    }

    "must store no inline errors when maxInlineErrors is zero" in {
      val validator = new TestCsvFileUploadValidator(inject[ActorSystem], new TestRowValidator, maxInlineErrors = 0)
      val result    = validateCsv(csv(Vector("", "value-b")), validator = validator).futureValue

      result.validation.inlineErrors mustBe Nil
      result.validation.validationErrors mustBe 1
    }

    "must validate a large CSV without accumulating rows" in {
      val rows   = Vector.fill(1000)(Vector("value-a", "value-b"))
      val result = validateCsv(csv(rows)).futureValue

      result.validation.status mustBe FileUploadValidationStatus.ValidationSuccess
      result.validation.rowsValidated mustBe 1000
    }
  }

  private def validateCsv(
    content: String,
    validator: TestCsvFileUploadValidator = validator
  ): Future[FileUploadValidatorResult] =
    validateCsv(content, Files.createTempFile("disa-errors", ".xlsx"), deleteErrorsFile = true, validator = validator)

  private def validateCsv(content: String, errorsFile: Path): Future[FileUploadValidatorResult] =
    validateCsv(content, errorsFile, deleteErrorsFile = false, validator = validator)

  private def validateCsv(
    content: String,
    errorsFile: Path,
    deleteErrorsFile: Boolean,
    validator: TestCsvFileUploadValidator
  ): Future[FileUploadValidatorResult] = {
    val file = Files.createTempFile("disa-upload", ".csv")
    Files.writeString(file, content, StandardCharsets.UTF_8)
    validator.validate(file, errorsFile, context).andThen { case _ =>
      Files.deleteIfExists(file)
      if (deleteErrorsFile) Files.deleteIfExists(errorsFile)
    }
  }

  private def csv(rows: Vector[String]*): String =
    csv(rows, TestFileUploadSchema.headers)

  private def csv(rows: Seq[Vector[String]], headers: Vector[String] = TestFileUploadSchema.headers): String =
    (headers.mkString(",") +: rows.map(_.mkString(","))).mkString("\n")

  private def workbookErrorRows(errorsFile: Path): Int =
    Using.resource(new XSSFWorkbook(Files.newInputStream(errorsFile))) { workbook =>
      workbook.getSheet("Errors").getLastRowNum
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

  private object OffsetRowsTestFileUploadSchema extends FileUploadSchema {
    override val headers: Vector[String]             = Vector("Column A", "Column B")
    override val csvHeaderRowIndexZeroBased: Int     = 2
    override val csvDataStartRowIndexZeroBased: Int  = 4
    override val xlsxRuleRowIndexZeroBased: Int      = 0
    override val xlsxHeaderRowIndexZeroBased: Int    = 1
    override val xlsxDataStartRowIndexZeroBased: Int = 2
  }

  private final class TestRowValidator extends FileUploadRowValidator[TestFileUploadValidationContext] {
    override def validate(row: FileUploadRow, context: TestFileUploadValidationContext): Seq[String] =
      Seq(
        Option.when(row.valuesByHeader.getOrElse("Column A", "").isEmpty)(columnARequiredErrorCode),
        Option.when(row.valuesByHeader.getOrElse("Column B", "").isEmpty)(columnBRequiredErrorCode)
      ).flatten
  }

  private final class TestCsvFileUploadValidator(
    actorSystem: ActorSystem,
    rowValidator: FileUploadRowValidator[TestFileUploadValidationContext],
    schema: FileUploadSchema = TestFileUploadSchema,
    maxInlineErrors: Int = 25
  ) extends BaseCsvFileUploadValidator[TestFileUploadValidationContext](
        actorSystem,
        rowValidator,
        schema,
        maxInlineErrors
      )
}
