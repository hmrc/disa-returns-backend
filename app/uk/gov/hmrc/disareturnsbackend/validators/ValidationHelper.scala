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

import scala.Option.*

object ValidationHelper {

  def validateParams(
    zReference: String,
    taxYear: String,
    month: String
  ): Either[String, (String, String, Int)] = {
    val maybeMonth = MonthValidator.parse(month)

    val errors = Seq(
      when(!ZReferenceValidator.isValid(zReference))("zReference"),
      when(!TaxYearValidator.isValid(taxYear))("taxYear"),
      when(maybeMonth.isEmpty)("month")
    ).flatten

    errors match {
      case Nil           => Right((zReference.toUpperCase, taxYear, maybeMonth.get))
      case invalidFields => Left(s"Invalid monthly return submission fields: [${invalidFields.mkString(", ")}]")
    }
  }
}
