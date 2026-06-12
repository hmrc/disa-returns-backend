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

class FileUploadSchemaSpec extends SpecBase {

  "FileUploadSchema" - {

    "must validate matching headers" in {
      TestSchema.validHeader(Vector("Column A", "Column B")) mustBe true
    }

    "must validate matching headers with empty trailing columns" in {
      TestSchema.validHeader(Vector("Column A", "Column B", "", "")) mustBe true
    }

    "must reject missing headers" in {
      TestSchema.validHeader(Vector("Column A")) mustBe false
    }

    "must reject headers in the wrong order" in {
      TestSchema.validHeader(Vector("Column B", "Column A")) mustBe false
    }

    "must reject non-empty extra columns" in {
      TestSchema.validHeader(Vector("Column A", "Column B", "Extra")) mustBe false
    }
  }

  private object TestSchema extends FileUploadSchema {
    override val headers: Vector[String]             = Vector("Column A", "Column B")
    override val csvHeaderRowIndexZeroBased: Int     = 0
    override val csvDataStartRowIndexZeroBased: Int  = 1
    override val xlsxRuleRowIndexZeroBased: Int      = 0
    override val xlsxHeaderRowIndexZeroBased: Int    = 1
    override val xlsxDataStartRowIndexZeroBased: Int = 2
  }
}
