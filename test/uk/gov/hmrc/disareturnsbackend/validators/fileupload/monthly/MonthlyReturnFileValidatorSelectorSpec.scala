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

package uk.gov.hmrc.disareturnsbackend.validators.fileupload.monthly

import base.SpecBase
import uk.gov.hmrc.disareturnsbackend.utils.Constants.XLSX_MIME_TYPE

class MonthlyReturnFileValidatorSelectorSpec extends SpecBase {

  private val selector = inject[MonthlyReturnFileValidatorSelector]

  "MonthlyReturnFileValidatorSelector" - {

    "must select CSV validator for text/csv" in {
      selector.select("text/csv").toOption.value mustBe a[MonthlyCsvFileUploadValidator]
    }

    "must select XLSX validator" in {
      selector.select(XLSX_MIME_TYPE).toOption.value mustBe a[MonthlyXlsxFileUploadValidator]
    }

    "must return Left for unknown MIME" in {
      selector.select("application/json") mustBe Left("application/json")
    }

    "must return Left for a missing MIME type" in {
      selector.select(null) mustBe Left("")
    }
  }
}
