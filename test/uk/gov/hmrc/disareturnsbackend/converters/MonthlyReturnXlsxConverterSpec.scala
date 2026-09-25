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

import base.SpecBase
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import play.api.libs.json.Json

import java.nio.charset.StandardCharsets
import java.nio.file.Files

class MonthlyReturnXlsxConverterSpec extends SpecBase {
  "MonthlyReturnXlsxConverter" - {
    "must convert data rows after its rule and header rows" in {
      val input   = Files.createTempFile("monthly-return-converter-", ".xlsx")
      val output  = Files.createTempFile("monthly-return-converter-", ".ndjson")
      val headers = Vector(
        "Account Number",
        "National Insurance Number",
        "First Name",
        "Middle Name",
        "Surname",
        "Date of Birth",
        "ISA Type being reported",
        "Flexible ISA",
        "Total current year subscriptions transferred in",
        "Total current year subscriptions transferred out",
        "Date of first subscription event",
        "Date of last subscription event",
        "Total current year to date subscriptions",
        "LISA qualifying addition",
        "LISA bonus claim",
        "Market value of account",
        "Closure Date",
        "ISA Reason for closure",
        "LISA Reason for closure"
      )
      try {
        val workbook = new XSSFWorkbook()
        try {
          val sheet     = workbook.createSheet()
          val headerRow = sheet.createRow(1)
          headers.zipWithIndex.foreach { case (header, index) => headerRow.createCell(index).setCellValue(header) }
          val row       = sheet.createRow(2)
          Vector(
            "ACC123",
            "AB123456C",
            "Ada",
            "",
            "Lovelace",
            "1980-01-02",
            "CASH",
            "Yes",
            "10.00",
            "0.00",
            "",
            "2026-05-01",
            "20.00",
            "",
            "",
            "100.00",
            "",
            "",
            ""
          ).zipWithIndex.foreach { case (value, index) => row.createCell(index).setCellValue(value) }
          val stream    = Files.newOutputStream(input)
          try workbook.write(stream)
          finally stream.close()
        } finally workbook.close()

        inject[MonthlyReturnXlsxConverter].convert(input, output)

        val lines = Files.readAllLines(output, StandardCharsets.UTF_8)
        lines.size() mustBe 1
        (Json.parse(lines.get(0)) \ "accountNumber").as[String] mustBe "ACC123"
      } finally {
        Files.deleteIfExists(input)
        Files.deleteIfExists(output)
      }
    }
  }
}
