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

import org.apache.poi.ss.util.CellReference
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler
import org.apache.poi.xssf.usermodel.XSSFComment

import scala.collection.mutable

final class XlsxEventParser(onRow: (Int, Map[String, String]) => Unit)
    extends XSSFSheetXMLHandler.SheetContentsHandler {

  private val rowCells = mutable.Map.empty[Int, String]
  private var headers  = Vector.empty[String]

  def hasHeaders: Boolean = headers.exists(_.nonEmpty)

  override def startRow(rowNum: Int): Unit =
    rowCells.clear()

  override def cell(cellReference: String, formattedValue: String, comment: XSSFComment): Unit = {
    val columnIndex = new CellReference(cellReference).getCol
    rowCells.update(columnIndex, formattedValue)
  }

  override def endRow(rowNum: Int): Unit = {
    val orderedCells =
      if (rowCells.isEmpty) {
        Vector.empty[String]
      } else {
        val maxColumnIndex = rowCells.keys.max
        Vector.tabulate(maxColumnIndex + 1)(columnIndex => rowCells.getOrElse(columnIndex, ""))
      }

    if (rowNum == 0) {
      headers = orderedCells.toVector
    } else {
      if (!hasHeaders) {
        throw new IllegalStateException("XLSX file is missing headers")
      }

      val rowData = headers.zipAll(orderedCells, "", "").collect {
        case (header, value) if header.nonEmpty => header -> value
      }.toMap

      onRow(rowNum + 1, rowData)
    }
  }

  override def headerFooter(text: String, isHeader: Boolean, tagName: String): Unit = ()
}
