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

class XlsxFileUploadEventParserSpec extends SpecBase {

  "XlsxFileUploadEventParser" - {

    "must use schema header and data row indexes" in {
      var headers  = Vector.empty[String]
      var dataRows = Vector.empty[Vector[String]]
      val parser   = new XlsxFileUploadEventParser(
        TestFileUploadSchema,
        header => headers = header,
        row => dataRows = dataRows :+ row
      )

      parser.startRow(1)
      parser.cell("A2", "ignored", null)
      parser.endRow(1)

      parser.startRow(2)
      parser.cell("A3", "Column A", null)
      parser.cell("B3", "Column B", null)
      parser.endRow(2)

      parser.startRow(3)
      parser.cell("A4", "still ignored", null)
      parser.endRow(3)

      parser.startRow(4)
      parser.cell("A5", "value-a", null)
      parser.cell("B5", "value-b", null)
      parser.endRow(4)

      headers mustBe TestFileUploadSchema.headers
      dataRows mustBe Vector(Vector("value-a", "value-b"))
    }

    "must normalise headers through the schema" in {
      var headers = Vector.empty[String]
      val parser  = new XlsxFileUploadEventParser(TestFileUploadSchema, header => headers = header, _ => ())

      parser.startRow(2)
      parser.cell("A3", "  Column\u00a0 A  ", null)
      parser.cell("B3", "Column    B", null)
      parser.endRow(2)

      headers mustBe TestFileUploadSchema.headers
    }

    "must cap materialised cells at schema headers length plus one" in {
      var dataRows = Vector.empty[Vector[String]]
      val parser   = new XlsxFileUploadEventParser(TestFileUploadSchema, _ => (), row => dataRows = dataRows :+ row)

      parser.startRow(4)
      parser.cell("A5", "value-a", null)
      parser.cell("XFD5", "far-right", null)
      parser.endRow(4)

      dataRows mustBe Vector(Vector("value-a", "", ""))
    }
  }

  private object TestFileUploadSchema extends FileUploadSchema {
    override val headers: Vector[String]             = Vector("Column A", "Column B")
    override val csvHeaderRowIndexZeroBased: Int     = 0
    override val csvDataStartRowIndexZeroBased: Int  = 1
    override val xlsxRuleRowIndexZeroBased: Int      = 0
    override val xlsxHeaderRowIndexZeroBased: Int    = 2
    override val xlsxDataStartRowIndexZeroBased: Int = 4
  }
}
