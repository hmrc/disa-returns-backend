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

package uk.gov.hmrc.disareturnsbackend.validators

import scala.util.matching.Regex

object TaxYearValidator {

  private val TaxYearPattern: Regex = raw"^20(\d{2})-(\d{2})$$".r

  def isValid(taxYear: String): Boolean =
    Option(taxYear).exists {
      case TaxYearPattern(startYear, endYear) =>
        endYear.toInt == startYear.toInt + 1
      case _                                  => false
    }
}
