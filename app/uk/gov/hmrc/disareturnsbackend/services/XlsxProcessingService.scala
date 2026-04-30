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

package uk.gov.hmrc.disareturnsbackend.services

import org.apache.poi.openxml4j.opc.{OPCPackage, PackageAccess}
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.xssf.eventusermodel.{ReadOnlySharedStringsTable, XSSFReader, XSSFSheetXMLHandler}
import play.api.Logging
import uk.gov.hmrc.disareturnsbackend.models.{XlsxValidationError, XlsxValidationReport}

import java.io.InputStream
import java.nio.file.Path
import javax.xml.parsers.SAXParserFactory
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContextExecutor, Future, blocking}
import scala.util.{Try, Using}

@Singleton
class XlsxProcessingService @Inject() (
    actorSystem: org.apache.pekko.actor.ActorSystem
)
    extends Logging {

  private val maxReportedErrors = 100

  private val blockingExecutionContext: ExecutionContextExecutor =
    actorSystem.dispatchers.lookup("contexts.blocking")

  def validate(file: Path): Future[XlsxValidationReport] =
    Future {
      blocking {
        buildValidationReport(file)
      }
    }(blockingExecutionContext)

  private def buildValidationReport(file: Path): XlsxValidationReport = {
    var rowsProcessed    = 0L
    var validRows        = 0L
    var invalidRows      = 0L
    val reportedErrors   = Vector.newBuilder[XlsxValidationError]
    var errorsReported   = 0
    var maxErrorsReached = false

    val parser = new XlsxEventParser((rowNumber, data) => {
      rowsProcessed += 1

      val errors = validateRow(rowNumber, data)

      if (errors.isEmpty) {
        validRows += 1
      } else {
        invalidRows += 1
        errors.foreach { errorMessage =>
          if (errorsReported < maxReportedErrors) {
            reportedErrors += XlsxValidationError(rowNumber, None, errorMessage)
            errorsReported += 1
          } else {
            maxErrorsReached = true
          }
        }
      }
    })
    var workbookHasSheet = false

    val emptyWorkbook = XlsxValidationReport(
      rowsProcessed = 0,
      validRows = 0,
      invalidRows = 1,
      errors = Seq(XlsxValidationError(1, None, "XLSX file is empty")),
      maxErrorsReached = false
    )

    val headerOnlyWorkbook = XlsxValidationReport(
      rowsProcessed = 0,
      validRows = 0,
      invalidRows = 1,
      errors = Seq(XlsxValidationError(2, None, "XLSX file has no data rows")),
      maxErrorsReached = false
    )

    val parseResult = Try {
      Using.resource(OPCPackage.open(file.toFile, PackageAccess.READ)) { pkg =>
        val reader = new XSSFReader(pkg)
        val styles = reader.getStylesTable
        val sharedStrings = new ReadOnlySharedStringsTable(pkg)
        val sheets = reader.getSheetsData

        if (!sheets.hasNext) {
          workbookHasSheet = false
        } else {
          workbookHasSheet = true
          val sheet = sheets.next()
          try {
            parseSheet(sheet, styles, sharedStrings, parser)
          } finally {
            sheet.close()
          }
        }
      }
    }

    parseResult.fold(
      exception =>
        XlsxValidationReport(
          rowsProcessed = 0,
          validRows = 0,
          invalidRows = 1,
          errors = Seq(
            XlsxValidationError(
              1,
              None,
              s"Failed to read XLSX file: ${exception.getMessage}"
            )
          ),
          maxErrorsReached = false
        ),
      _ =>
        if (!workbookHasSheet) {
          emptyWorkbook
        } else if (!parser.hasHeaders) {
          emptyWorkbook
        } else if (rowsProcessed == 0) {
          headerOnlyWorkbook
        } else {
          XlsxValidationReport(
            rowsProcessed = rowsProcessed,
            validRows = validRows,
            invalidRows = invalidRows,
            errors = reportedErrors.result(),
            maxErrorsReached = maxErrorsReached
          )
        }
    )
  }

  private def parseSheet(
      sheet: InputStream,
      styles: org.apache.poi.xssf.model.StylesTable,
      sharedStrings: ReadOnlySharedStringsTable,
      parser: XlsxEventParser
  ): Unit = {
    val contentHandler = new XSSFSheetXMLHandler(
      styles,
      null,
      sharedStrings,
      parser,
      new DataFormatter(),
      false
    )

    val saxParserFactory = SAXParserFactory.newInstance()
    saxParserFactory.setNamespaceAware(true)

    val xmlReader = saxParserFactory.newSAXParser().getXMLReader
    xmlReader.setContentHandler(contentHandler)
    xmlReader.parse(new org.xml.sax.InputSource(sheet))
  }

  private def validateRow(
      rowNum: Int,
      data: Map[String, String]
  ): List[String] = {
    val errors = List.newBuilder[String]

    if (rowNum <= 0) {
      errors += "Row number is invalid"
    }

    if (data.isEmpty || data.values.forall(_.trim.isEmpty)) {
      errors += "Row is empty"
    }

    errors.result()
  }
}
