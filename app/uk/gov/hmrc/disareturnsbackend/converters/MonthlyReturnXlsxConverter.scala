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

package uk.gov.hmrc.disareturnsbackend.converters

import org.apache.poi.openxml4j.opc.*
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.util.CellReference
import org.apache.poi.xssf.eventusermodel.*
import org.apache.poi.xssf.model.StylesTable
import org.apache.poi.xssf.usermodel.XSSFComment
import org.xml.sax.InputSource
import uk.gov.hmrc.disareturnsbackend.validators.fileupload.monthly.MonthlyFileUploadTemplate

import java.io.*
import java.nio.file.Path
import javax.inject.{Inject, Singleton}
import javax.xml.parsers.SAXParserFactory
import scala.collection.mutable
import scala.util.Using

trait MonthlyReturnXlsxConverter {
  def convert(input: Path, output: Path): Unit
}

@Singleton
class MonthlyReturnXlsxConverterImpl @Inject() (ndjsonWriter: MonthlyReturnNdjsonWriter)
    extends MonthlyReturnXlsxConverter {
  override def convert(input: Path, output: Path): Unit =
    ndjsonWriter.withOutput(output) { writer =>
      Using.resource(OPCPackage.open(input.toFile, PackageAccess.READ)) { pkg =>
        val reader = new XSSFReader(pkg)
        val sheets = reader.getSheetsData
        if (sheets.hasNext) {
          val sheet = sheets.next()
          try
            parseSheet(sheet, reader.getStylesTable, new ReadOnlySharedStringsTable(pkg), writer)
          finally
            sheet.close()
        }
      }
    }

  private def parseSheet(
    sheet: InputStream,
    styles: StylesTable,
    sharedStrings: ReadOnlySharedStringsTable,
    writer: BufferedWriter
  ): Unit = {
    val handler        = new MonthlyReturnXlsxRowHandler(writer)
    val contentHandler = new XSSFSheetXMLHandler(
      styles,
      null,
      sharedStrings,
      handler,
      new DataFormatter(),
      false
    )
    val factory        = SAXParserFactory.newInstance()
    factory.setNamespaceAware(true)
    val xmlReader      = factory.newSAXParser().getXMLReader
    xmlReader.setContentHandler(contentHandler)
    xmlReader.parse(new InputSource(sheet))
  }

  private final class MonthlyReturnXlsxRowHandler(writer: BufferedWriter)
      extends XSSFSheetXMLHandler.SheetContentsHandler {
    private val cells = mutable.Map.empty[Int, String]

    override def startRow(rowNum: Int): Unit = cells.clear()

    override def cell(ref: String, value: String, comment: XSSFComment): Unit =
      cells.update(new CellReference(ref).getCol, Option(value).getOrElse(""))

    override def endRow(rowNum: Int): Unit =
      if (rowNum >= MonthlyFileUploadTemplate.xlsxDataStartRowIndexZeroBased) {
        ndjsonWriter.writeRow(
          Vector.tabulate(MonthlyFileUploadTemplate.headers.size)(i => cells.getOrElse(i, "")),
          writer
        )
      }

    override def headerFooter(text: String, isHeader: Boolean, tagName: String): Unit = ()
  }
}
