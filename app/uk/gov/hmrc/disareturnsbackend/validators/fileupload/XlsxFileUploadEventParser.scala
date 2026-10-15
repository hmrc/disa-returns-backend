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
  onNonEmptyCell: () => Unit,
  onHeader: Vector[String] => Unit,
  onDataRow: Vector[String] => Unit
) extends SheetContentsHandler {

  private val rowCells             = mutable.Map.empty[Int, String]
  private val expectedColumnCount  = schema.headers.length
  private var hasNonEmptyExtraCell = false

  override def startRow(rowNum: Int): Unit = {
    rowCells.clear()
    hasNonEmptyExtraCell = false
  }

  override def cell(cellReference: String, formattedValue: String, comment: XSSFComment): Unit = {
    val columnIndex = new CellReference(cellReference).getCol
    val cellValue   = Option(formattedValue).getOrElse("").trim

    if (cellValue.nonEmpty) {
      onNonEmptyCell()
    }

    if (columnIndex < expectedColumnCount) {
      rowCells.update(columnIndex, cellValue)
    } else if (cellValue.nonEmpty) {
      hasNonEmptyExtraCell = true
    }
  }

  override def endRow(rowNum: Int): Unit = {
    val orderedCells: Vector[String]         =
      Vector.tabulate(expectedColumnCount)(columnIndex => rowCells.getOrElse(columnIndex, ""))
    val cellsWithColumnCount: Vector[String] =
      if (hasNonEmptyExtraCell) orderedCells :+ "Unexpected column" else orderedCells
    val rowIsHeaderRow: Boolean              = rowNum == schema.xlsxHeaderRowIndexZeroBased
    val rowIsDataRow: Boolean                = rowNum >= schema.xlsxDataStartRowIndexZeroBased

    if (rowIsHeaderRow) {
      onHeader(cellsWithColumnCount.map(schema.normaliseHeader))
    } else if (rowIsDataRow) {
      onDataRow(cellsWithColumnCount)
    }
  }

  override def headerFooter(text: String, isHeader: Boolean, tagName: String): Unit = ()
}
