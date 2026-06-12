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

import org.apache.poi.ss.util.CellReference
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.*
import org.apache.poi.xssf.usermodel.XSSFComment

import scala.collection.mutable

final class XlsxFileUploadEventParser(
  schema: FileUploadSchema,
  onHeader: Vector[String] => Unit,
  onDataRow: Vector[String] => Unit
) extends SheetContentsHandler {

  private val rowCells                 = mutable.Map.empty[Int, String]
  private val maximumUsefulColumnCount = schema.headers.length + 1

  override def startRow(rowNum: Int): Unit =
    rowCells.clear()

  override def cell(cellReference: String, formattedValue: String, comment: XSSFComment): Unit = {
    val columnIndex = new CellReference(cellReference).getCol
    rowCells.update(columnIndex, Option(formattedValue).getOrElse(""))
  }

  override def endRow(rowNum: Int): Unit = {
    val maxObservedColumnIndex: Int  = if (rowCells.isEmpty) -1 else rowCells.keys.max
    val cappedColumnCount: Int       = math.min(maxObservedColumnIndex + 1, maximumUsefulColumnCount)
    val orderedCells: Vector[String] =
      Vector.tabulate(cappedColumnCount)(columnIndex => rowCells.getOrElse(columnIndex, ""))
    val rowIsHeaderRow: Boolean      = rowNum == schema.xlsxHeaderRowIndexZeroBased
    val rowIsDataRow: Boolean        = rowNum >= schema.xlsxDataStartRowIndexZeroBased

    if (rowIsHeaderRow) {
      onHeader(orderedCells.map(schema.normaliseHeader))
    } else if (rowIsDataRow) {
      onDataRow(orderedCells)
    }
  }

  override def headerFooter(text: String, isHeader: Boolean, tagName: String): Unit = ()
}
