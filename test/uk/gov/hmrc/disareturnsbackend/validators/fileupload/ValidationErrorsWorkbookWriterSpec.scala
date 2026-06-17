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
import org.apache.poi.xssf.usermodel.XSSFWorkbook

import java.nio.file.Files
import scala.util.Using

class ValidationErrorsWorkbookWriterSpec extends SpecBase {

  "ValidationErrorsWorkbookWriter" - {

    "must not create a workbook before the first error row" in {
      val path   = Files.createTempFile("disa-errors", ".xlsx")
      val writer = new ValidationErrorsWorkbookWriter(path)

      try {
        writer.written mustBe false
        writer.close()
        Files.size(path) mustBe 0
      } finally Files.deleteIfExists(path)
    }

    "must keep written false after close when no rows were written" in {
      val path   = Files.createTempFile("disa-errors", ".xlsx")
      val writer = new ValidationErrorsWorkbookWriter(path)

      try {
        writer.close()

        writer.written mustBe false
        Files.size(path) mustBe 0
      } finally Files.deleteIfExists(path)
    }

    "must write headers and distinct semicolon-separated codes in deterministic order" in {
      val path   = Files.createTempFile("disa-errors", ".xlsx")
      val writer = new ValidationErrorsWorkbookWriter(path)

      try {
        writer.write(
          1,
          Seq(nationalInsuranceRequiredErrorCode, accountNumberRequiredErrorCode, nationalInsuranceRequiredErrorCode)
        )
        writer.close()

        writer.written mustBe true
        Using.resource(new XSSFWorkbook(Files.newInputStream(path))) { workbook =>
          val sheet = workbook.getSheet("Errors")
          sheet.getRow(0).getCell(0).getStringCellValue mustBe "RowNumber"
          sheet.getRow(0).getCell(1).getStringCellValue mustBe "ErrorCodes"
          sheet.getRow(1).getCell(0).getNumericCellValue mustBe 1.0
          sheet.getRow(1).getCell(1).getStringCellValue mustBe Seq(
            nationalInsuranceRequiredErrorCode,
            accountNumberRequiredErrorCode
          ).mkString(";")
        }
      } finally Files.deleteIfExists(path)
    }
  }
}
