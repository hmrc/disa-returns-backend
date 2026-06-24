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

import uk.gov.hmrc.disareturnsbackend.models.FileUploadValidationError

import scala.util.control.NoStackTrace

final class XlsxWorkbookValidationContext[C <: FileUploadValidationContext](
  rowValidator: FileUploadRowValidator[C],
  schema: FileUploadSchema,
  context: C,
  errorWriter: ValidationErrorsWorkbookWriter,
  maxInlineErrors: Int
) {

  private val inlineErrorLimit = math.max(0, maxInlineErrors)

  // SAX event-based XML parser callbacks need local state, so we keep it here so rows are still streamed.
  private var workbookHasSingleSheet = false
  private var headerSeen             = false
  private var headerIsValid          = false
  private var rowsValidated          = 0L
  private var validationErrors       = 0L
  private var inlineErrors           = Vector.empty[FileUploadValidationError]
  private var errorVolumes           = Map.empty[String, Long]

  def markWorkbookHasSingleSheet(): Unit =
    workbookHasSingleSheet = true

  def validateHeaderRow(header: Vector[String]): Unit = {
    headerSeen = true
    headerIsValid = schema.validHeader(header)

    if (!headerIsValid) {
      throw XlsxWorkbookValidationContext.InvalidHeaderException
    }
  }

  def validateDataRow(rowCells: Vector[String]): Unit =
    if (headerIsValid) {

      val rowNumber                       = rowsValidated + 1
      val rowCellsWithNullsAsEmptyStrings = rowCells.map(Option(_).getOrElse(""))
      val rowCellsByHeader                = schema.headers.zipAll(rowCellsWithNullsAsEmptyStrings, "", "").toMap
      val uploadRow                       = FileUploadRow(
        rowNumber = rowNumber,
        valuesByHeader = rowCellsByHeader,
        rawColumnCount = rowCells.length
      )
      val rowCellErrors                   = rowValidator.validate(uploadRow, context)

      rowsValidated = rowNumber

      if (rowCellErrors.nonEmpty) {
        validationErrors += rowCellErrors.length
        errorVolumes = rowCellErrors.foldLeft(errorVolumes) { case (volumes, errorCode) =>
          volumes.updatedWith(errorCode)(_.map(_ + 1).orElse(Some(1L)))
        }
        errorWriter.write(rowNumber, rowCellErrors)

        val inlineErrorLimitAvailable = inlineErrors.length < inlineErrorLimit

        if (inlineErrorLimitAvailable) {
          inlineErrors = inlineErrors :+ FileUploadValidationError(rowNumber, rowCellErrors.toList)
        }
      }
    }

  def summary: XlsxWorkbookValidationSummary =
    XlsxWorkbookValidationSummary(
      workbookHasSingleSheet = workbookHasSingleSheet,
      headerSeen = headerSeen,
      headerIsValid = headerIsValid,
      rowsValidated = rowsValidated,
      validationErrors = validationErrors,
      errorFileWritten = errorWriter.written,
      inlineErrors = inlineErrors.toList,
      errorVolumes = errorVolumes
    )
}

object XlsxWorkbookValidationContext {
  object InvalidHeaderException extends RuntimeException("Invalid XLSX header") with NoStackTrace
  object MultipleSheetsException
      extends RuntimeException("XLSX workbooks must contain exactly one sheet")
      with NoStackTrace
}
