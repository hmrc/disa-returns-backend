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

import uk.gov.hmrc.disareturnsbackend.validators.fileupload.FileUploadRow

import base.SpecBase
import uk.gov.hmrc.disareturnsbackend.validators.fileupload.monthly.MonthlyFileUploadErrorCodes.*

import java.time.LocalDate

class MonthlyFileUploadRowValidatorSpec extends SpecBase {

  private val validator = inject[MonthlyFileUploadRowValidator]
  private val context   = MonthlyReturnFileUploadValidationContext("2026-27", 5)

  "MonthlyFileUploadRowValidator" - {

    "must accept a valid row" in {
      validator.validate(validRow(), context) mustBe empty
    }

    "must validate required fields, formats, enums and money fields" in {
      val values = validValues
        .updated("National Insurance Number", "QQ123456A")
        .updated("Date of Birth", "2026-99-99")
        .updated("ISA Type being reported", "BAD")
        .updated("Flexible ISA", "Maybe")
        .updated("Total current year subscriptions transferred in", "10")
        .updated("Account Number", "")

      validator.validate(
        FileUploadRow(1, values, MonthlyFileUploadTemplate.headers.length),
        context
      ) must contain allOf (
        AccountNumberRequired,
        NationalInsuranceFormat,
        DateOfBirthFormat,
        IsaTypeInvalid,
        FlexibleIsaInvalid,
        TransferInMoneyFormat
      )
    }

    "must return multiple validation errors in deterministic validator order" in {
      val values = validValues
        .updated("Account Number", "")
        .updated("National Insurance Number", "bad")
        .updated("First Name", "John1")
        .updated("Middle Name", "a" * 51)
        .updated("Surname", "Surname1")
        .updated("Date of Birth", "2026/99/99")
        .updated("ISA Type being reported", "BAD")
        .updated("Flexible ISA", "Maybe")
        .updated("Total current year subscriptions transferred in", "10A")
        .updated("Total current year subscriptions transferred out", "20A")
        .updated("Date of first subscription event", "bad")
        .updated("Date of last subscription event", "2026-07-01")
        .updated("Total current year to date subscriptions", "30A")
        .updated("LISA qualifying addition", "40A")
        .updated("LISA bonus claim", "50A")
        .updated("Market value of account", "60A")
        .updated("Closure Date", "bad")
        .updated("ISA Reason for closure", "CLOSED!")
        .updated("LISA Reason for closure", "CLOSED!")

      validator.validate(FileUploadRow(1, values, MonthlyFileUploadTemplate.headers.length + 1), context) mustBe Seq(
        InvalidRowColumnCount,
        AccountNumberRequired,
        NationalInsuranceTooShort,
        FirstNameInvalidCharacters,
        MiddleNameMaxLength,
        SurnameInvalidCharacters,
        DateOfBirthInvalidCharacters,
        IsaTypeInvalid,
        FlexibleIsaInvalid,
        TransferInInvalidCharacters,
        TransferOutInvalidCharacters,
        FirstSubscriptionInvalidCharacters,
        LastSubscriptionOutsideReportingPeriod,
        CurrentYearSubscriptionsInvalidCharacters,
        LisaQualifyingAdditionInvalidCharacters,
        LisaBonusClaimInvalidCharacters,
        MarketValueInvalidCharacters,
        ClosureDateInvalidCharacters,
        IsaClosureReasonInvalidCharacters,
        LisaClosureReasonInvalidCharacters
      )
    }

    "must suppress duplicate errors for empty and invalid-character values" in {
      validator.validate(
        FileUploadRow(1, validValues.updated("Date of Birth", ""), MonthlyFileUploadTemplate.headers.length),
        context
      ) mustBe Seq(DateOfBirthRequired)
      validator.validate(
        FileUploadRow(1, validValues.updated("Date of Birth", "1997/08/15"), MonthlyFileUploadTemplate.headers.length),
        context
      ) mustBe Seq(DateOfBirthInvalidCharacters)
      validator.validate(
        FileUploadRow(
          1,
          validValues.updated("Total current year subscriptions transferred in", "£5A.75"),
          MonthlyFileUploadTemplate.headers.length
        ),
        context
      ) mustBe Seq(TransferInInvalidCharacters)
    }

    "must apply LISA conditional fields and closure reason rules" in {
      val values = validValues
        .updated("ISA Type being reported", "LISA")
        .updated("Flexible ISA", "")
        .updated("Date of first subscription event", "")
        .updated("LISA qualifying addition", "")
        .updated("LISA bonus claim", "")
        .updated("Closure Date", "2026-05-12")
        .updated("LISA Reason for closure", "")

      validator.validate(
        FileUploadRow(1, values, MonthlyFileUploadTemplate.headers.length),
        context
      ) must contain allOf (
        FirstSubscriptionRequired,
        LisaQualifyingAdditionRequired,
        LisaBonusClaimRequired,
        LisaClosureReasonRequired
      )
    }

    "must reject last subscription date outside current or previous reporting month" in {
      val values = validValues.updated("Date of last subscription event", "2026-07-01")

      validator.validate(FileUploadRow(1, values, MonthlyFileUploadTemplate.headers.length), context) must contain(
        LastSubscriptionOutsideReportingPeriod
      )
    }

    "must calculate current or previous reporting month" in {
      validator.isInCurrentOrPreviousReportingMonth(LocalDate.parse("2026-04-06"), "2026-27", 5) mustBe true
      validator.isInCurrentOrPreviousReportingMonth(LocalDate.parse("2026-05-31"), "2026-27", 5) mustBe true
      validator.isInCurrentOrPreviousReportingMonth(LocalDate.parse("2026-06-01"), "2026-27", 5) mustBe false
    }

    "must calculate reporting year for January to March reporting months" in {
      validator.isInCurrentOrPreviousReportingMonth(LocalDate.parse("2027-02-01"), "2026-27", 2) mustBe true
      validator.isInCurrentOrPreviousReportingMonth(LocalDate.parse("2026-02-01"), "2026-27", 2) mustBe false
    }
  }

  private def validRow(): FileUploadRow =
    FileUploadRow(1, validValues, MonthlyFileUploadTemplate.headers.length)

  private val validValues: Map[String, String] = Map(
    "Account Number"                                   -> "ACC123",
    "National Insurance Number"                        -> "AA123456A",
    "First Name"                                       -> "First",
    "Middle Name"                                      -> "Middle",
    "Surname"                                          -> "Surname",
    "Date of Birth"                                    -> "1990-01-01",
    "ISA Type being reported"                          -> "CASH",
    "Flexible ISA"                                     -> "Yes",
    "Total current year subscriptions transferred in"  -> "10.00",
    "Total current year subscriptions transferred out" -> "0.00",
    "Date of first subscription event"                 -> "",
    "Date of last subscription event"                  -> "2026-05-12",
    "Total current year to date subscriptions"         -> "20.00",
    "LISA qualifying addition"                         -> "",
    "LISA bonus claim"                                 -> "",
    "Market value of account"                          -> "100.00",
    "Closure Date"                                     -> "",
    "ISA Reason for closure"                           -> "",
    "LISA Reason for closure"                          -> ""
  )
}
