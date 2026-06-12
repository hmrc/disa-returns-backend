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

import org.apache.pekko.actor.ActorSystem
import org.apache.poi.ooxml.POIXMLException
import org.apache.poi.openxml4j.exceptions.{InvalidFormatException, NotOfficeXmlFileException, OLE2NotOfficeXmlFileException}
import org.apache.poi.openxml4j.opc.{OPCPackage, PackageAccess}
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.xssf.eventusermodel.{ReadOnlySharedStringsTable, XSSFReader, XSSFSheetXMLHandler}
import org.apache.poi.xssf.model.StylesTable
import org.xml.sax.{InputSource, SAXException}

import java.io.InputStream
import java.nio.file.Path
import javax.xml.parsers.{ParserConfigurationException, SAXParserFactory}
import scala.concurrent.{ExecutionContextExecutor, Future, blocking}
import scala.util.{Try, Using}

abstract class BaseXlsxFileUploadValidator[C <: FileUploadValidationContext](
  actorSystem: ActorSystem,
  rowValidator: FileUploadRowValidator[C],
  schema: FileUploadSchema,
  maxInlineErrors: Int
) extends FileUploadValidator[C] {

  import XlsxWorkbookValidationContext.{InvalidHeaderException, MultipleSheetsException}

  private val blockingExecutionContext: ExecutionContextExecutor =
    actorSystem.dispatchers.lookup("contexts.file-upload-blocking")

  private val inlineErrorLimit: Int = math.max(0, maxInlineErrors)

  override final def validate(file: Path, errorsFile: Path, context: C): Future[FileUploadValidatorResult] =
    Future(blocking(validateWorkbook(file, errorsFile, context)))(blockingExecutionContext)

  private def validateWorkbook(file: Path, errorsFile: Path, context: C): FileUploadValidatorResult = {

    val errorWriter = new ValidationErrorsWorkbookWriter(errorsFile)
    val tracker     = new XlsxWorkbookValidationContext(rowValidator, schema, context, errorWriter, inlineErrorLimit)

    try {
      val parseResult: Try[Unit] = Try(parseWorkbook(file, tracker))

      parseResult.fold(
        exception => parseFailureResult(exception, tracker.summary),
        _ => createValidationResult(tracker.summary)
      )
    } finally errorWriter.close()
  }

  private def parseWorkbook(file: Path, tracker: XlsxWorkbookValidationContext[C]): Unit =
    Using.resource(OPCPackage.open(file.toFile, PackageAccess.READ)) { pkg =>
      val reader = new XSSFReader(pkg)
      parseOnlySheetIfPresent(pkg, reader, tracker)
    }

  private def parseOnlySheetIfPresent(
    pkg: OPCPackage,
    reader: XSSFReader,
    tracker: XlsxWorkbookValidationContext[C]
  ): Unit = {

    val sheets = reader.getSheetsData

    if (sheets.hasNext) {
      val firstSheet = sheets.next()

      if (sheets.hasNext) {
        firstSheet.close()
        throw MultipleSheetsException
      }

      parseSheet(pkg, reader, firstSheet, tracker)
    }
  }

  private def parseSheet(
    pkg: OPCPackage,
    reader: XSSFReader,
    sheet: InputStream,
    tracker: XlsxWorkbookValidationContext[C]
  ): Unit = {

    tracker.markWorkbookHasSingleSheet()
    val workbookSharedStringsTable = new ReadOnlySharedStringsTable(pkg)

    try {
      val parser = createEventParser(tracker)
      parseSheetXml(sheet, reader.getStylesTable, workbookSharedStringsTable, parser)
    } finally sheet.close()
  }

  private def createEventParser(tracker: XlsxWorkbookValidationContext[C]): XlsxFileUploadEventParser =
    new XlsxFileUploadEventParser(
      schema = schema,
      onHeader = tracker.validateHeaderRow,
      onDataRow = tracker.validateDataRow
    )

  private def parseFailureResult(
    exception: Throwable,
    summary: XlsxWorkbookValidationSummary
  ): FileUploadValidatorResult = {

    val noRowsWereValidated: Boolean   = summary.rowsValidated == 0
    val noErrorFileWasWritten: Boolean = !summary.errorFileWritten
    val isInvalidFile: Boolean         = isInvalidWorkbookException(exception) && noRowsWereValidated && noErrorFileWasWritten

    if (isInvalidFile) {
      FileUploadValidationResults.invalidFile
    } else {
      throw exception
    }
  }

  private def createValidationResult(summary: XlsxWorkbookValidationSummary): FileUploadValidatorResult = {

    val workbookDoesNotHaveSingleSheet = !summary.workbookHasSingleSheet
    val headerWasNotSeen               = !summary.headerSeen
    val headerIsInvalid                = !summary.headerIsValid
    val noRowsWereValidated            = summary.rowsValidated == 0
    val workbookIsInvalid              = workbookDoesNotHaveSingleSheet || headerWasNotSeen || headerIsInvalid || noRowsWereValidated
    val hasValidationErrors            = summary.validationErrors > 0

    if (workbookIsInvalid) {
      FileUploadValidationResults.invalidFile
    } else if (!hasValidationErrors) {
      FileUploadValidationResults.success(summary.rowsValidated)
    } else {
      FileUploadValidationResults.failed(
        rowsValidated = summary.rowsValidated,
        validationErrors = summary.validationErrors,
        errorFileWritten = summary.errorFileWritten,
        inlineErrors = summary.inlineErrors
      )
    }
  }

  private def isInvalidWorkbookException(exception: Throwable): Boolean =
    exception match {
      case InvalidHeaderException           => true
      case MultipleSheetsException          => true
      case _: InvalidFormatException        => true
      case _: OLE2NotOfficeXmlFileException => true
      case _: NotOfficeXmlFileException     => true
      case _: ParserConfigurationException  => true
      case _: POIXMLException               => true
      case _: SAXException                  => true
      case _                                => false
    }

  private def parseSheetXml(
    sheet: InputStream,
    styles: StylesTable,
    workbookSharedStringsTable: ReadOnlySharedStringsTable,
    parser: XlsxFileUploadEventParser
  ): Unit = {

    val contentHandler   =
      new XSSFSheetXMLHandler(
        styles,
        null, // comments are not needed for validation
        workbookSharedStringsTable,
        parser,
        new DataFormatter(),
        false // use formula results rather than formula text
      )
    val saxParserFactory = SAXParserFactory.newInstance()
    saxParserFactory.setNamespaceAware(true)
    val xmlReader        = saxParserFactory.newSAXParser().getXMLReader
    xmlReader.setContentHandler(contentHandler)
    xmlReader.parse(new InputSource(sheet))
  }
}
