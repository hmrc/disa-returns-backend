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
import uk.gov.hmrc.disareturnsbackend.validators.fileupload.monthly.MonthlyFileUploadErrorCodes.*
import uk.gov.hmrc.disareturnsbackend.validators.fileupload.FileUploadRow

class MonthlyFileUploadColumnValidatorsSpec extends SpecBase {

  private val context = MonthlyReturnFileUploadValidationContext("2026-27", 5)

  "FileUpload column validators" - {

    "must validate row column count" in {
      RowColumnCountValidator.validate(
        row(rawColumnCount = MonthlyFileUploadTemplate.headers.length),
        context
      ) mustBe empty
      RowColumnCountValidator.validate(
        row(rawColumnCount = MonthlyFileUploadTemplate.headers.length - 1),
        context
      ) mustBe Seq(InvalidRowColumnCount)
      RowColumnCountValidator.validate(
        row(rawColumnCount = MonthlyFileUploadTemplate.headers.length + 1),
        context
      ) mustBe Seq(InvalidRowColumnCount)
    }

    "must validate account number" in {
      AccountNumberValidator.validate(row(AccountNumberValidator.header -> ""), context) mustBe Seq(
        AccountNumberRequired
      )
      AccountNumberValidator.validate(row(AccountNumberValidator.header -> "a" * 21), context) mustBe Seq(
        AccountNumberMaxLength
      )
      AccountNumberValidator.validate(row(), context) mustBe empty
    }

    "must validate national insurance number" in {
      NationalInsuranceNumberValidator.validate(row(NationalInsuranceNumberValidator.header -> ""), context) mustBe Seq(
        NationalInsuranceRequired
      )
      NationalInsuranceNumberValidator.validate(
        row(NationalInsuranceNumberValidator.header -> "AB123456!"),
        context
      ) mustBe Seq(NationalInsuranceInvalidCharacters)
      NationalInsuranceNumberValidator.validate(
        row(NationalInsuranceNumberValidator.header -> "AB12345"),
        context
      ) mustBe Seq(NationalInsuranceTooShort)
      NationalInsuranceNumberValidator.validate(
        row(NationalInsuranceNumberValidator.header -> "AB123456AA"),
        context
      ) mustBe Seq(NationalInsuranceTooLong)
      NationalInsuranceNumberValidator.validate(
        row(NationalInsuranceNumberValidator.header -> "QQ 12 34 56 A"),
        context
      ) mustBe Seq(NationalInsuranceTooLong)
      NationalInsuranceNumberValidator.validate(
        row(NationalInsuranceNumberValidator.header -> "QQ123456A"),
        context
      ) mustBe Seq(NationalInsuranceFormat)
      NationalInsuranceNumberValidator.validate(
        row(NationalInsuranceNumberValidator.header -> "AA123456A"),
        context
      ) mustBe empty
      NationalInsuranceNumberValidator.validate(
        row(NationalInsuranceNumberValidator.header -> "AB123456"),
        context
      ) mustBe empty
      NationalInsuranceNumberValidator.validate(
        row(NationalInsuranceNumberValidator.header -> "CE123456D"),
        context
      ) mustBe empty
      NationalInsuranceNumberValidator.validate(
        row(NationalInsuranceNumberValidator.header -> "aa123456a"),
        context
      ) mustBe empty
    }

    "must validate names" in {
      FirstNameValidator.validate(row(FirstNameValidator.header -> ""), context) mustBe Seq(FirstNameRequired)
      FirstNameValidator.validate(row(FirstNameValidator.header -> "a" * 51), context) mustBe Seq(FirstNameMaxLength)
      FirstNameValidator.validate(row(FirstNameValidator.header -> "John1"), context) mustBe Seq(
        FirstNameInvalidCharacters
      )
      FirstNameValidator.validate(row(FirstNameValidator.header -> "Anne-Marie"), context) mustBe empty
      FirstNameValidator.validate(row(FirstNameValidator.header -> "O'Connor"), context) mustBe empty
      FirstNameValidator.validate(row(FirstNameValidator.header -> "O’Connor"), context) mustBe empty
      FirstNameValidator.validate(row(FirstNameValidator.header -> "Mary Jane"), context) mustBe empty
      MiddleNameValidator.validate(row(MiddleNameValidator.header -> ""), context) mustBe empty
      MiddleNameValidator.validate(row(MiddleNameValidator.header -> "a" * 51), context) mustBe Seq(MiddleNameMaxLength)
      MiddleNameValidator.validate(row(MiddleNameValidator.header -> "John1"), context) mustBe Seq(
        MiddleNameInvalidCharacters
      )
      MiddleNameValidator.validate(row(MiddleNameValidator.header -> "Anne-Marie"), context) mustBe empty
      MiddleNameValidator.validate(row(MiddleNameValidator.header -> "O'Connor"), context) mustBe empty
      MiddleNameValidator.validate(row(MiddleNameValidator.header -> "O’Connor"), context) mustBe empty
      MiddleNameValidator.validate(row(MiddleNameValidator.header -> "Mary Jane"), context) mustBe empty
      SurnameValidator.validate(row(SurnameValidator.header -> ""), context) mustBe Seq(SurnameRequired)
      SurnameValidator.validate(row(SurnameValidator.header -> "a" * 51), context) mustBe Seq(SurnameMaxLength)
      SurnameValidator.validate(row(SurnameValidator.header -> "John1"), context) mustBe Seq(SurnameInvalidCharacters)
      SurnameValidator.validate(row(SurnameValidator.header -> "Anne-Marie"), context) mustBe empty
      SurnameValidator.validate(row(SurnameValidator.header -> "O'Connor"), context) mustBe empty
      SurnameValidator.validate(row(SurnameValidator.header -> "O’Connor"), context) mustBe empty
      SurnameValidator.validate(row(SurnameValidator.header -> "Mary Jane"), context) mustBe empty
    }

    "must validate dates and ISA type" in {
      DateOfBirthValidator.validate(row(DateOfBirthValidator.header -> ""), context) mustBe Seq(DateOfBirthRequired)
      DateOfBirthValidator.validate(row(DateOfBirthValidator.header -> "1997/08/15"), context) mustBe Seq(
        DateOfBirthInvalidCharacters
      )
      DateOfBirthValidator.validate(row(DateOfBirthValidator.header -> "1997-AB-15"), context) mustBe Seq(
        DateOfBirthInvalidCharacters
      )
      DateOfBirthValidator.validate(row(DateOfBirthValidator.header -> "1997-13-15"), context) mustBe Seq(
        DateOfBirthFormat
      )
      DateOfBirthValidator.validate(row(DateOfBirthValidator.header -> "1997-02-30"), context) mustBe Seq(
        DateOfBirthFormat
      )
      DateOfBirthValidator.validate(row(DateOfBirthValidator.header -> "1997-08-15"), context) mustBe empty
      IsaTypeValidator.validate(row(IsaTypeValidator.header -> ""), context) mustBe Seq(IsaTypeRequired)
      IsaTypeValidator.validate(row(IsaTypeValidator.header -> "BAD"), context) mustBe Seq(IsaTypeInvalid)
      IsaTypeValidator.validate(row(IsaTypeValidator.header -> "LISA"), context) mustBe empty
    }

    "must validate flexible ISA" in {
      FlexibleIsaValidator.validate(
        row(IsaTypeValidator.header -> "CASH", FlexibleIsaValidator.header -> ""),
        context
      ) mustBe Seq(FlexibleIsaRequired)
      FlexibleIsaValidator.validate(
        row(IsaTypeValidator.header -> "LISA", FlexibleIsaValidator.header -> ""),
        context
      ) mustBe empty
      FlexibleIsaValidator.validate(row(FlexibleIsaValidator.header -> "Maybe"), context) mustBe Seq(FlexibleIsaInvalid)
      FlexibleIsaValidator.validate(row(FlexibleIsaValidator.header -> "Yes"), context) mustBe empty
      FlexibleIsaValidator.validate(row(FlexibleIsaValidator.header -> "No"), context) mustBe empty
    }

    "must validate money fields" in {
      TransferInValidator.validate(row(TransferInValidator.header -> ""), context) mustBe Seq(TransferInRequired)
      assertMoneyValidation(
        TransferInValidator,
        TransferInValidator.header,
        TransferInInvalidCharacters,
        TransferInMoneyFormat
      )
      TransferOutValidator.validate(row(TransferOutValidator.header -> ""), context) mustBe Seq(TransferOutRequired)
      assertMoneyValidation(
        TransferOutValidator,
        TransferOutValidator.header,
        TransferOutInvalidCharacters,
        TransferOutMoneyFormat
      )
      CurrentYearSubscriptionsValidator.validate(
        row(CurrentYearSubscriptionsValidator.header -> ""),
        context
      ) mustBe Seq(CurrentYearSubscriptionsRequired)
      assertMoneyValidation(
        CurrentYearSubscriptionsValidator,
        CurrentYearSubscriptionsValidator.header,
        CurrentYearSubscriptionsInvalidCharacters,
        CurrentYearSubscriptionsMoneyFormat
      )
      MarketValueValidator.validate(row(MarketValueValidator.header -> ""), context) mustBe Seq(MarketValueRequired)
      assertMoneyValidation(
        MarketValueValidator,
        MarketValueValidator.header,
        MarketValueInvalidCharacters,
        MarketValueMoneyFormat
      )
    }

    "must validate LISA conditional fields" in {
      FirstSubscriptionEventDateValidator.validate(
        row(IsaTypeValidator.header -> "LISA", FirstSubscriptionEventDateValidator.header -> ""),
        context
      ) mustBe Seq(FirstSubscriptionRequired)
      FirstSubscriptionEventDateValidator.validate(
        row(IsaTypeValidator.header -> "CASH", FirstSubscriptionEventDateValidator.header -> ""),
        context
      ) mustBe empty
      FirstSubscriptionEventDateValidator.validate(
        row(FirstSubscriptionEventDateValidator.header -> "2026/05/12"),
        context
      ) mustBe Seq(FirstSubscriptionInvalidCharacters)
      FirstSubscriptionEventDateValidator.validate(
        row(FirstSubscriptionEventDateValidator.header -> "2026-99-99"),
        context
      ) mustBe Seq(FirstSubscriptionDateFormat)
      LisaQualifyingAdditionValidator.validate(
        row(IsaTypeValidator.header -> "LISA", LisaQualifyingAdditionValidator.header -> ""),
        context
      ) mustBe Seq(LisaQualifyingAdditionRequired)
      LisaQualifyingAdditionValidator.validate(
        row(IsaTypeValidator.header -> "CASH", LisaQualifyingAdditionValidator.header -> ""),
        context
      ) mustBe empty
      assertMoneyValidation(
        LisaQualifyingAdditionValidator,
        LisaQualifyingAdditionValidator.header,
        LisaQualifyingAdditionInvalidCharacters,
        LisaQualifyingAdditionMoneyFormat
      )
      LisaBonusClaimValidator.validate(
        row(IsaTypeValidator.header -> "LISA", LisaBonusClaimValidator.header -> ""),
        context
      ) mustBe Seq(LisaBonusClaimRequired)
      LisaBonusClaimValidator.validate(
        row(IsaTypeValidator.header -> "CASH", LisaBonusClaimValidator.header -> ""),
        context
      ) mustBe empty
      assertMoneyValidation(
        LisaBonusClaimValidator,
        LisaBonusClaimValidator.header,
        LisaBonusClaimInvalidCharacters,
        LisaBonusClaimMoneyFormat
      )
    }

    "must validate last subscription reporting period" in {
      LastSubscriptionEventDateValidator.validate(
        row(LastSubscriptionEventDateValidator.header -> ""),
        context
      ) mustBe Seq(LastSubscriptionRequired)
      LastSubscriptionEventDateValidator.validate(
        row(LastSubscriptionEventDateValidator.header -> "2026/05/12"),
        context
      ) mustBe Seq(LastSubscriptionInvalidCharacters)
      LastSubscriptionEventDateValidator.validate(
        row(LastSubscriptionEventDateValidator.header -> "2026-99-99"),
        context
      ) mustBe Seq(LastSubscriptionDateFormat)
      LastSubscriptionEventDateValidator.validate(
        row(LastSubscriptionEventDateValidator.header -> "2026-07-01"),
        context
      ) mustBe Seq(LastSubscriptionOutsideReportingPeriod)
      LastSubscriptionEventDateValidator.validate(
        row(LastSubscriptionEventDateValidator.header -> "2026-05-31"),
        context
      ) mustBe empty
      LastSubscriptionEventDateValidator.validate(
        row(LastSubscriptionEventDateValidator.header -> "2026-04-06"),
        context
      ) mustBe empty
      LastSubscriptionEventDateValidator.validate(
        row(LastSubscriptionEventDateValidator.header -> "2026-04-05"),
        context
      ) mustBe Seq(LastSubscriptionOutsideReportingPeriod)
    }

    "must validate closure fields" in {
      ClosureDateValidator.validate(row(ClosureDateValidator.header -> ""), context) mustBe empty
      ClosureDateValidator.validate(
        row(ClosureDateValidator.header -> "", IsaClosureReasonValidator.header -> "CLOSED"),
        context
      ) mustBe Seq(ClosureDateRequired)
      ClosureDateValidator.validate(
        row(ClosureDateValidator.header -> "", LisaClosureReasonValidator.header -> "TRANSFERRED_IN_FULL"),
        context
      ) mustBe Seq(ClosureDateRequired)
      ClosureDateValidator.validate(row(ClosureDateValidator.header -> "2026/05/12"), context) mustBe Seq(
        ClosureDateInvalidCharacters
      )
      ClosureDateValidator.validate(row(ClosureDateValidator.header -> "2026-99-99"), context) mustBe Seq(
        ClosureDateFormat
      )
      IsaClosureReasonValidator.validate(
        row(
          ClosureDateValidator.header      -> "2026-05-01",
          IsaTypeValidator.header          -> "CASH",
          IsaClosureReasonValidator.header -> ""
        ),
        context
      ) mustBe Seq(IsaClosureReasonRequired)
      IsaClosureReasonValidator.validate(
        row(ClosureDateValidator.header -> "", IsaClosureReasonValidator.header -> ""),
        context
      ) mustBe empty
      IsaClosureReasonValidator.validate(row(IsaClosureReasonValidator.header -> "CLOSED!"), context) mustBe Seq(
        IsaClosureReasonInvalidCharacters
      )
      IsaClosureReasonValidator.validate(
        row(IsaClosureReasonValidator.header -> "TRANSFERRED_IN_FULL"),
        context
      ) mustBe Seq(IsaClosureReasonInvalid)
      IsaClosureReasonValidator.validate(row(IsaClosureReasonValidator.header -> "CLOSED"), context) mustBe empty
      LisaClosureReasonValidator.validate(
        row(
          ClosureDateValidator.header       -> "2026-05-01",
          IsaTypeValidator.header           -> "LISA",
          LisaClosureReasonValidator.header -> ""
        ),
        context
      ) mustBe Seq(LisaClosureReasonRequired)
      LisaClosureReasonValidator.validate(
        row(ClosureDateValidator.header -> "", LisaClosureReasonValidator.header -> ""),
        context
      ) mustBe empty
      LisaClosureReasonValidator.validate(row(LisaClosureReasonValidator.header -> "CLOSED!"), context) mustBe Seq(
        LisaClosureReasonInvalidCharacters
      )
      LisaClosureReasonValidator.validate(row(LisaClosureReasonValidator.header -> "NOT_A_REASON"), context) mustBe Seq(
        LisaClosureReasonInvalid
      )
      LisaClosureReasonValidator.validate(
        row(LisaClosureReasonValidator.header -> "TRANSFERRED_IN_FULL"),
        context
      ) mustBe empty
      LisaClosureReasonValidator.validate(
        row(LisaClosureReasonValidator.header -> "ALL_FUNDS_WITHDRAWN"),
        context
      ) mustBe empty
    }
  }

  private def assertMoneyValidation(
    validator: MonthlyFileUploadColumnValidator,
    header: String,
    invalidCharactersCode: String,
    formatCode: String
  ): Unit = {
    validator.validate(row(header -> "£5A.75"), context) mustBe Seq(invalidCharactersCode)
    validator.validate(row(header -> "5,75"), context) mustBe Seq(invalidCharactersCode)
    validator.validate(row(header -> "5"), context) mustBe Seq(formatCode)
    validator.validate(row(header -> "5.7"), context) mustBe Seq(formatCode)
    validator.validate(row(header -> "5.777"), context) mustBe Seq(formatCode)
    validator.validate(row(header -> "£5"), context) mustBe Seq(formatCode)
    validator.validate(row(header -> "5.75"), context) mustBe empty
    validator.validate(row(header -> "£5.75"), context) mustBe empty
  }

  private def row(overrides: (String, String)*): FileUploadRow =
    row(rawColumnCount = MonthlyFileUploadTemplate.headers.length, overrides: _*)

  private def row(rawColumnCount: Int, overrides: (String, String)*): FileUploadRow =
    FileUploadRow(1, validValues ++ overrides.toMap, rawColumnCount)

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
