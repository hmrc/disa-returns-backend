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

import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.streaming.SXSSFWorkbook

import java.nio.file.{Files, Path}
import scala.util.Using

final class ValidationErrorsWorkbookWriter(path: Path) {

  private var workbook: Option[SXSSFWorkbook] = None
  private var sheet: Option[Sheet]            = None
  private var nextRowIndex                    = 0
  private var hasWritten                      = false

  def write(rowNumber: Long, errorCodes: Seq[String]): Unit = {
    ensureWorkbook()

    val row = sheet.get.createRow(nextRowIndex)
    row.createCell(0).setCellValue(rowNumber.toDouble)
    row.createCell(1).setCellValue(errorCodes.distinct.mkString(";"))

    nextRowIndex += 1
    hasWritten = true
  }

  def written: Boolean = hasWritten

  def close(): Unit =
    workbook.foreach { wb =>
      try
        Using.resource(Files.newOutputStream(path)) { output =>
          wb.write(output)
        }
      finally {
        wb.close()
        workbook = None
        sheet = None
      }
    }

  private def ensureWorkbook(): Unit =
    if (workbook.isEmpty) {
      val wb     = new SXSSFWorkbook(100)
      val sh     = wb.createSheet("Errors")
      val header = sh.createRow(0)

      header.createCell(0).setCellValue("RowNumber")
      header.createCell(1).setCellValue("ErrorCodes")

      workbook = Some(wb)
      sheet = Some(sh)
      nextRowIndex = 1
    }
}
