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

package uk.gov.hmrc.disareturnsbackend.models

import play.api.libs.json.*

import java.time.LocalDate

enum MonthlyReturnIsaType {
  case CASH, STOCKS_AND_SHARES, INNOVATIVE_FINANCE, LIFETIME
}

enum MonthlyReturnClosureReason {
  case CANCELLED, CLOSED, VOID, TRANSFERRED_IN_FULL, ALL_FUNDS_WITHDRAWN
}

final case class MonthlyReturnSubmissionCommon(
  accountNumber: String,
  nino: String,
  firstName: String,
  middleName: Option[String],
  lastName: String,
  dateOfBirth: LocalDate,
  amountTransferredIn: BigDecimal,
  amountTransferredOut: BigDecimal,
  dateOfLastSubscription: LocalDate,
  totalCurrentYearSubscriptionsToDate: BigDecimal,
  marketValueOfAccount: BigDecimal,
  isaType: MonthlyReturnIsaType
)

sealed trait MonthlyReturnSubmission {
  def common: MonthlyReturnSubmissionCommon
}

final case class MonthlyReturnStandardSubscription(common: MonthlyReturnSubmissionCommon, flexibleIsa: Boolean)
    extends MonthlyReturnSubmission

final case class MonthlyReturnStandardClosure(
  common: MonthlyReturnSubmissionCommon,
  flexibleIsa: Boolean,
  closureDate: LocalDate,
  reasonForClosure: MonthlyReturnClosureReason
) extends MonthlyReturnSubmission

final case class MonthlyReturnLifetimeSubscription(
  common: MonthlyReturnSubmissionCommon,
  dateOfFirstSubscription: LocalDate,
  lisaQualifyingAddition: BigDecimal,
  lisaBonusClaim: BigDecimal
) extends MonthlyReturnSubmission

final case class MonthlyReturnLifetimeClosure(
  common: MonthlyReturnSubmissionCommon,
  dateOfFirstSubscription: LocalDate,
  lisaQualifyingAddition: BigDecimal,
  lisaBonusClaim: BigDecimal,
  closureDate: LocalDate,
  reasonForClosure: MonthlyReturnClosureReason
) extends MonthlyReturnSubmission

object MonthlyReturnSubmission {
  private implicit val isaTypeWrites: Writes[MonthlyReturnIsaType]             = Writes(value => JsString(value.toString))
  private implicit val closureReasonWrites: Writes[MonthlyReturnClosureReason] =
    Writes(value => JsString(value.toString))
  private implicit val commonWrites: OWrites[MonthlyReturnSubmissionCommon]    = Json.writes[MonthlyReturnSubmissionCommon]

  implicit val writes: Writes[MonthlyReturnSubmission] = Writes { submission =>
    val common   = Json.toJson(submission.common).as[JsObject]
    val specific = submission match {
      case value: MonthlyReturnStandardSubscription =>
        Json.obj("flexibleIsa" -> value.flexibleIsa)
      case value: MonthlyReturnStandardClosure      =>
        Json.obj(
          "flexibleIsa"      -> value.flexibleIsa,
          "closureDate"      -> value.closureDate,
          "reasonForClosure" -> value.reasonForClosure
        )
      case value: MonthlyReturnLifetimeSubscription =>
        Json.obj(
          "dateOfFirstSubscription" -> value.dateOfFirstSubscription,
          "lisaQualifyingAddition"  -> value.lisaQualifyingAddition,
          "lisaBonusClaim"          -> value.lisaBonusClaim
        )
      case value: MonthlyReturnLifetimeClosure      =>
        Json.obj(
          "dateOfFirstSubscription" -> value.dateOfFirstSubscription,
          "lisaQualifyingAddition"  -> value.lisaQualifyingAddition,
          "lisaBonusClaim"          -> value.lisaBonusClaim,
          "closureDate"             -> value.closureDate,
          "reasonForClosure"        -> value.reasonForClosure
        )
    }
    common ++ specific
  }
}
