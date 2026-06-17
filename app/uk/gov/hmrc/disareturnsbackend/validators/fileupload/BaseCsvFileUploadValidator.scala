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

import com.univocity.parsers.csv.{CsvParser, CsvParserSettings}
import org.apache.pekko.actor.ActorSystem
import uk.gov.hmrc.disareturnsbackend.models.FileUploadValidationError

import java.io.{BufferedReader, InputStreamReader}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.concurrent.{ExecutionContextExecutor, Future, blocking}
import scala.annotation.tailrec
import scala.util.Using

abstract class BaseCsvFileUploadValidator[C <: FileUploadValidationContext](
  actorSystem: ActorSystem,
  rowValidator: FileUploadRowValidator[C],
  schema: FileUploadSchema,
  maxInlineErrors: Int
) extends FileUploadValidator[C] {

  private val blockingExecutionContext: ExecutionContextExecutor =
    actorSystem.dispatchers.lookup("contexts.file-upload-blocking")

  private val inlineErrorLimit: Int = math.max(0, maxInlineErrors)

  override final def validate(file: Path, errorsFile: Path, context: C): Future[FileUploadValidatorResult] =
    Future {
      blocking {
        val parser = new CsvParser(createCsvParserSettings)

        Using.resource(new BufferedReader(new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8))) {
          reader =>
            parser.beginParsing(reader)

            try
              validateParsedRows(parser, errorsFile, context)
            finally
              parser.stopParsing()
        }
      }
    }(blockingExecutionContext)

  private def createCsvParserSettings: CsvParserSettings = {

    val settings = new CsvParserSettings()
    settings.setHeaderExtractionEnabled(false)
    settings.setLineSeparatorDetectionEnabled(true)
    settings.setIgnoreLeadingWhitespaces(false)
    settings.setIgnoreTrailingWhitespaces(false)
    settings
  }

  private def validateParsedRows(parser: CsvParser, errorsFile: Path, context: C): FileUploadValidatorResult = {

    val (headerRowIndex, parsedHeaderRow) =
      skipRowsBeforeHeader(parser, currentRowIndex = 0, parsedRow = parser.parseNext())
    val headerRow: Option[Vector[String]] = parsedHeaderRow.map(_.toVector.map(schema.normaliseHeader))
    val headerRowIsInvalid: Boolean       = !headerRow.exists(schema.validHeader)

    if (headerRowIsInvalid) {
      FileUploadValidationResults.invalidFile
    } else {
      skipRowsBetweenHeaderAndData(parser, currentRowIndex = headerRowIndex + 1)
      validateDataRows(parser, errorsFile, context)
    }
  }

  @tailrec
  private def skipRowsBeforeHeader(
    parser: CsvParser,
    currentRowIndex: Int,
    parsedRow: Array[String]
  ): (Int, Option[Array[String]]) = {

    val parsedRowExists: Boolean                 = parsedRow != null
    val headerRowHasNotBeenReached: Boolean      = currentRowIndex < schema.csvHeaderRowIndexZeroBased
    val shouldSkipParsedRowBeforeHeader: Boolean = parsedRowExists && headerRowHasNotBeenReached

    if (shouldSkipParsedRowBeforeHeader) {
      skipRowsBeforeHeader(parser, currentRowIndex + 1, parser.parseNext())
    } else {
      currentRowIndex -> Option(parsedRow)
    }
  }

  @tailrec
  private def skipRowsBetweenHeaderAndData(parser: CsvParser, currentRowIndex: Int): Unit = {

    val dataRowHasNotBeenReached: Boolean = currentRowIndex < schema.csvDataStartRowIndexZeroBased

    if (dataRowHasNotBeenReached) {
      parser.parseNext()
      skipRowsBetweenHeaderAndData(parser, currentRowIndex + 1)
    }
  }

  private def validateDataRows(parser: CsvParser, errorsFile: Path, context: C): FileUploadValidatorResult = {

    val errorWriter = new ValidationErrorsWorkbookWriter(errorsFile)
    val accumulator =
      try
        validateRows(parser, errorWriter, context, CsvValidationAccumulator.empty)
      finally
        errorWriter.close()

    if (accumulator.rowsValidated == 0) {
      FileUploadValidationResults.invalidFile
    } else if (accumulator.validationErrors == 0) {
      FileUploadValidationResults.success(accumulator.rowsValidated)
    } else {
      FileUploadValidationResults.failed(
        rowsValidated = accumulator.rowsValidated,
        validationErrors = accumulator.validationErrors,
        errorFileWritten = errorWriter.written,
        inlineErrors = accumulator.inlineErrors.toList
      )
    }
  }

  private final case class CsvValidationAccumulator(
    rowsValidated: Long,
    validationErrors: Long,
    inlineErrors: Vector[FileUploadValidationError]
  )

  private object CsvValidationAccumulator {
    val empty: CsvValidationAccumulator = CsvValidationAccumulator(0L, 0L, Vector.empty)
  }

  @tailrec
  private def validateRows(
    parser: CsvParser,
    errorWriter: ValidationErrorsWorkbookWriter,
    context: C,
    validationAccumulator: CsvValidationAccumulator
  ): CsvValidationAccumulator = {

    val row: Array[String] = parser.parseNext()

    if (row == null) {
      validationAccumulator
    } else {

      val rowNumber: Long            = validationAccumulator.rowsValidated + 1
      val rowCells: Seq[String]      = row.toVector.map(value => Option(value).getOrElse(""))
      val uploadRow                  = FileUploadRow(
        rowNumber = rowNumber,
        valuesByHeader = schema.headers.zipAll(rowCells, "", "").toMap, // maps expected headers to row cell values
        rawColumnCount = rowCells.length
      )
      val rowCellErrors: Seq[String] = rowValidator.validate(uploadRow, context)

      if (rowCellErrors.nonEmpty) {
        errorWriter.write(rowNumber, rowCellErrors)
      }

      val inlineErrorLimitAvailable = validationAccumulator.inlineErrors.length < inlineErrorLimit

      val updatedInlineErrors =
        if (rowCellErrors.nonEmpty && inlineErrorLimitAvailable) {
          validationAccumulator.inlineErrors :+ FileUploadValidationError(rowNumber, rowCellErrors.toList)
        } else {
          validationAccumulator.inlineErrors
        }

      validateRows(
        parser,
        errorWriter,
        context,
        validationAccumulator.copy(
          rowsValidated = rowNumber,
          validationErrors = validationAccumulator.validationErrors + rowCellErrors.length,
          inlineErrors = updatedInlineErrors
        )
      )
    }
  }
}
