/*
 * Copyright 2025 HM Revenue & Customs
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
import uk.gov.hmrc.disareturnsbackend.validators.fileupload.monthly.MonthlyFileUploadErrorCodes.*

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, YearMonth}
import java.util.Locale
import scala.util.Try
import scala.util.matching.Regex

trait MonthlyFileUploadColumnValidator {
  def validate(row: FileUploadRow, context: MonthlyReturnFileUploadValidationContext): Seq[String]
}

trait MonthlyFileUploadColumnValidationSupport {

  protected val moneyPattern: Regex                          = raw"^£?\d+(\.\d{2})$$".r
  protected val nameAllowedCharactersPattern: Regex          = raw"^[A-Za-z '’\-]+$$".r
  protected val ninoAllowedCharactersPattern: Regex          = raw"^[A-Za-z0-9 ]+$$".r
  protected val dateAllowedCharactersPattern: Regex          = raw"^[0-9\-]+$$".r
  protected val moneyAllowedCharactersPattern: Regex         = raw"^£?[0-9.]+$$".r
  protected val closureReasonAllowedCharactersPattern: Regex = raw"^[A-Z_]+$$".r
  protected val dateFormatter: DateTimeFormatter             = DateTimeFormatter.ISO_LOCAL_DATE

  protected val isaTypes: Set[String] =
    Set("CASH", "STOCKS_AND_SHARES", "INNOVATIVE_FINANCE", "LISA")

  protected val flexibleIsaTypes: Set[String] =
    Set("CASH", "STOCKS_AND_SHARES", "INNOVATIVE_FINANCE")

  protected val isaClosureReasons: Set[String] =
    Set("CANCELLED", "CLOSED", "VOID")

  protected val lisaClosureReasons: Set[String] =
    isaClosureReasons ++ Set("TRANSFERRED_IN_FULL", "ALL_FUNDS_WITHDRAWN")

  protected def value(row: FileUploadRow, header: String): String =
    row.valuesByHeader.getOrElse(header, "").trim

  protected def required(value: String, code: String): Option[String] =
    Option.when(value.isEmpty)(code)

  protected def maxLength(value: String, maximum: Int, code: String): Option[String] =
    Option.when(value.length > maximum)(code)

  protected def validMoney(value: String, code: String): Option[String] =
    Option.when(value.nonEmpty && !moneyPattern.matches(value))(code)

  protected def invalidCharacters(value: String, allowedPattern: Regex, code: String): Option[String] =
    Option.when(value.nonEmpty && !allowedPattern.matches(value))(code)

  protected def invalidFormatWhenAllowedCharacters(
    value: String,
    allowedPattern: Regex,
    isValidFormat: Boolean,
    code: String
  ): Option[String] =
    Option.when(value.nonEmpty && allowedPattern.matches(value) && !isValidFormat)(code)

  protected def validMoneyWhenAllowedCharacters(value: String, code: String): Option[String] =
    invalidFormatWhenAllowedCharacters(value, moneyAllowedCharactersPattern, moneyPattern.matches(value), code)

  protected def parseIsoDate(value: String): Option[LocalDate] =
    Try(LocalDate.parse(value, dateFormatter)).toOption

  protected def validIsoDate(value: String, code: String): Option[String] =
    Option.when(value.nonEmpty && parseIsoDate(value).isEmpty)(code)

  protected def presentErrors(errors: Option[String]*): Seq[String] =
    errors.flatten
}

object AccountNumberValidator extends MonthlyFileUploadColumnValidator with MonthlyFileUploadColumnValidationSupport {
  val header                = "Account Number"
  val requiredCode: String  = AccountNumberRequired
  val maxLengthCode: String = AccountNumberMaxLength

  override def validate(row: FileUploadRow, context: MonthlyReturnFileUploadValidationContext): Seq[String] = {
    val accountNumber = value(row, header)

    presentErrors(
      required(accountNumber, requiredCode),
      maxLength(accountNumber, 20, maxLengthCode)
    )
  }
}

object ClosureDateValidator extends MonthlyFileUploadColumnValidator with MonthlyFileUploadColumnValidationSupport {
  val header                        = "Closure Date"
  val requiredCode: String          = ClosureDateRequired
  val formatCode: String            = ClosureDateFormat
  val invalidCharactersCode: String = ClosureDateInvalidCharacters

  override def validate(row: FileUploadRow, context: MonthlyReturnFileUploadValidationContext): Seq[String] = {
    val closureDate          = value(row, header)
    val closureReasonPresent =
      value(row, IsaClosureReasonValidator.header).nonEmpty || value(row, LisaClosureReasonValidator.header).nonEmpty

    presentErrors(
      Option.when(closureDate.isEmpty && closureReasonPresent)(requiredCode),
      invalidCharacters(closureDate, dateAllowedCharactersPattern, invalidCharactersCode),
      invalidFormatWhenAllowedCharacters(
        closureDate,
        dateAllowedCharactersPattern,
        parseIsoDate(closureDate).isDefined,
        formatCode
      )
    )
  }
}

object CurrentYearSubscriptionsValidator
    extends MonthlyFileUploadColumnValidator
    with MonthlyFileUploadColumnValidationSupport {
  val header                        = "Total current year to date subscriptions"
  val requiredCode: String          = CurrentYearSubscriptionsRequired
  val moneyFormatCode: String       = CurrentYearSubscriptionsMoneyFormat
  val invalidCharactersCode: String = CurrentYearSubscriptionsInvalidCharacters

  override def validate(row: FileUploadRow, context: MonthlyReturnFileUploadValidationContext): Seq[String] = {
    val money = value(row, header)

    presentErrors(
      required(money, requiredCode),
      invalidCharacters(money, moneyAllowedCharactersPattern, invalidCharactersCode),
      validMoneyWhenAllowedCharacters(money, moneyFormatCode)
    )
  }
}

object DateOfBirthValidator extends MonthlyFileUploadColumnValidator with MonthlyFileUploadColumnValidationSupport {
  val header                        = "Date of Birth"
  val requiredCode: String          = DateOfBirthRequired
  val formatCode: String            = DateOfBirthFormat
  val invalidCharactersCode: String = DateOfBirthInvalidCharacters

  override def validate(row: FileUploadRow, context: MonthlyReturnFileUploadValidationContext): Seq[String] = {
    val dateOfBirth = value(row, header)

    presentErrors(
      required(dateOfBirth, requiredCode),
      invalidCharacters(dateOfBirth, dateAllowedCharactersPattern, invalidCharactersCode),
      invalidFormatWhenAllowedCharacters(
        dateOfBirth,
        dateAllowedCharactersPattern,
        parseIsoDate(dateOfBirth).isDefined,
        formatCode
      )
    )
  }
}

object FirstNameValidator extends MonthlyFileUploadColumnValidator with MonthlyFileUploadColumnValidationSupport {
  val header                        = "First Name"
  val requiredCode: String          = FirstNameRequired
  val maxLengthCode: String         = FirstNameMaxLength
  val invalidCharactersCode: String = FirstNameInvalidCharacters

  override def validate(row: FileUploadRow, context: MonthlyReturnFileUploadValidationContext): Seq[String] = {
    val firstName = value(row, header)

    presentErrors(
      required(firstName, requiredCode),
      maxLength(firstName, 50, maxLengthCode),
      invalidCharacters(firstName, nameAllowedCharactersPattern, invalidCharactersCode)
    )
  }
}

object FirstSubscriptionEventDateValidator
    extends MonthlyFileUploadColumnValidator
    with MonthlyFileUploadColumnValidationSupport {
  val header                        = "Date of first subscription event"
  val requiredCode: String          = FirstSubscriptionRequired
  val formatCode: String            = FirstSubscriptionDateFormat
  val invalidCharactersCode: String = FirstSubscriptionInvalidCharacters

  override def validate(row: FileUploadRow, context: MonthlyReturnFileUploadValidationContext): Seq[String] = {
    val isaType           = value(row, IsaTypeValidator.header)
    val firstSubscription = value(row, header)

    presentErrors(
      Option.when(isaType == "LISA" && firstSubscription.isEmpty)(requiredCode),
      invalidCharacters(firstSubscription, dateAllowedCharactersPattern, invalidCharactersCode),
      invalidFormatWhenAllowedCharacters(
        firstSubscription,
        dateAllowedCharactersPattern,
        parseIsoDate(firstSubscription).isDefined,
        formatCode
      )
    )
  }
}

object FlexibleIsaValidator extends MonthlyFileUploadColumnValidator with MonthlyFileUploadColumnValidationSupport {
  val header               = "Flexible ISA"
  val requiredCode: String = FlexibleIsaRequired
  val invalidCode: String  = FlexibleIsaInvalid

  override def validate(row: FileUploadRow, context: MonthlyReturnFileUploadValidationContext): Seq[String] = {
    val isaType     = value(row, IsaTypeValidator.header)
    val flexibleIsa = value(row, header)

    presentErrors(
      Option.when(flexibleIsaTypes.contains(isaType) && flexibleIsa.isEmpty)(requiredCode),
      Option.when(flexibleIsa.nonEmpty && flexibleIsa != "Yes" && flexibleIsa != "No")(invalidCode)
    )
  }
}

object IsaClosureReasonValidator
    extends MonthlyFileUploadColumnValidator
    with MonthlyFileUploadColumnValidationSupport {
  val header                        = "ISA Reason for closure"
  val requiredCode: String          = IsaClosureReasonRequired
  val invalidCode: String           = IsaClosureReasonInvalid
  val invalidCharactersCode: String = IsaClosureReasonInvalidCharacters

  override def validate(row: FileUploadRow, context: MonthlyReturnFileUploadValidationContext): Seq[String] = {
    val isaType       = value(row, IsaTypeValidator.header)
    val closureDate   = value(row, ClosureDateValidator.header)
    val closureReason = value(row, header)

    presentErrors(
      Option.when(closureDate.nonEmpty && flexibleIsaTypes.contains(isaType) && closureReason.isEmpty)(requiredCode),
      invalidCharacters(closureReason, closureReasonAllowedCharactersPattern, invalidCharactersCode),
      invalidFormatWhenAllowedCharacters(
        closureReason,
        closureReasonAllowedCharactersPattern,
        isaClosureReasons.contains(closureReason),
        invalidCode
      )
    )
  }
}

object IsaTypeValidator extends MonthlyFileUploadColumnValidator with MonthlyFileUploadColumnValidationSupport {
  val header               = "ISA Type being reported"
  val requiredCode: String = IsaTypeRequired
  val invalidCode: String  = IsaTypeInvalid

  override def validate(row: FileUploadRow, context: MonthlyReturnFileUploadValidationContext): Seq[String] = {
    val isaType = value(row, header)

    presentErrors(
      required(isaType, requiredCode),
      Option.when(isaType.nonEmpty && !isaTypes.contains(isaType))(invalidCode)
    )
  }
}

object LastSubscriptionEventDateValidator
    extends MonthlyFileUploadColumnValidator
    with MonthlyFileUploadColumnValidationSupport {
  val header                             = "Date of last subscription event"
  val requiredCode: String               = LastSubscriptionRequired
  val formatCode: String                 = LastSubscriptionDateFormat
  val outsideReportingPeriodCode: String = LastSubscriptionOutsideReportingPeriod
  val invalidCharactersCode: String      = LastSubscriptionInvalidCharacters

  override def validate(row: FileUploadRow, context: MonthlyReturnFileUploadValidationContext): Seq[String] = {
    val lastSubscription = value(row, header)
    val dateError        = parseIsoDate(lastSubscription) match {
      case Some(date)
          if !ReportingPeriodValidator.isInCurrentOrPreviousReportingMonth(date, context.taxYear, context.month) =>
        Some(outsideReportingPeriodCode)
      case None if lastSubscription.nonEmpty && dateAllowedCharactersPattern.matches(lastSubscription) =>
        Some(formatCode)
      case _                                                                                           => None
    }

    presentErrors(
      required(lastSubscription, requiredCode),
      invalidCharacters(lastSubscription, dateAllowedCharactersPattern, invalidCharactersCode),
      dateError
    )
  }
}

object LisaBonusClaimValidator extends MonthlyFileUploadColumnValidator with MonthlyFileUploadColumnValidationSupport {
  val header                        = "LISA bonus claim"
  val requiredCode: String          = LisaBonusClaimRequired
  val moneyFormatCode: String       = LisaBonusClaimMoneyFormat
  val invalidCharactersCode: String = LisaBonusClaimInvalidCharacters

  override def validate(row: FileUploadRow, context: MonthlyReturnFileUploadValidationContext): Seq[String] = {
    val isaType = value(row, IsaTypeValidator.header)
    val money   = value(row, header)

    presentErrors(
      Option.when(isaType == "LISA" && money.isEmpty)(requiredCode),
      invalidCharacters(money, moneyAllowedCharactersPattern, invalidCharactersCode),
      validMoneyWhenAllowedCharacters(money, moneyFormatCode)
    )
  }
}

object LisaClosureReasonValidator
    extends MonthlyFileUploadColumnValidator
    with MonthlyFileUploadColumnValidationSupport {
  val header                        = "LISA Reason for closure"
  val requiredCode: String          = LisaClosureReasonRequired
  val invalidCode: String           = LisaClosureReasonInvalid
  val invalidCharactersCode: String = LisaClosureReasonInvalidCharacters

  override def validate(row: FileUploadRow, context: MonthlyReturnFileUploadValidationContext): Seq[String] = {
    val isaType       = value(row, IsaTypeValidator.header)
    val closureDate   = value(row, ClosureDateValidator.header)
    val closureReason = value(row, header)

    presentErrors(
      Option.when(closureDate.nonEmpty && isaType == "LISA" && closureReason.isEmpty)(requiredCode),
      invalidCharacters(closureReason, closureReasonAllowedCharactersPattern, invalidCharactersCode),
      invalidFormatWhenAllowedCharacters(
        closureReason,
        closureReasonAllowedCharactersPattern,
        lisaClosureReasons.contains(closureReason),
        invalidCode
      )
    )
  }
}

object LisaQualifyingAdditionValidator
    extends MonthlyFileUploadColumnValidator
    with MonthlyFileUploadColumnValidationSupport {
  val header                        = "LISA qualifying addition"
  val requiredCode: String          = LisaQualifyingAdditionRequired
  val moneyFormatCode: String       = LisaQualifyingAdditionMoneyFormat
  val invalidCharactersCode: String = LisaQualifyingAdditionInvalidCharacters

  override def validate(row: FileUploadRow, context: MonthlyReturnFileUploadValidationContext): Seq[String] = {
    val isaType = value(row, IsaTypeValidator.header)
    val money   = value(row, header)

    presentErrors(
      Option.when(isaType == "LISA" && money.isEmpty)(requiredCode),
      invalidCharacters(money, moneyAllowedCharactersPattern, invalidCharactersCode),
      validMoneyWhenAllowedCharacters(money, moneyFormatCode)
    )
  }
}

object MarketValueValidator extends MonthlyFileUploadColumnValidator with MonthlyFileUploadColumnValidationSupport {
  val header                        = "Market value of account"
  val requiredCode: String          = MarketValueRequired
  val moneyFormatCode: String       = MarketValueMoneyFormat
  val invalidCharactersCode: String = MarketValueInvalidCharacters

  override def validate(row: FileUploadRow, context: MonthlyReturnFileUploadValidationContext): Seq[String] = {
    val money = value(row, header)

    presentErrors(
      required(money, requiredCode),
      invalidCharacters(money, moneyAllowedCharactersPattern, invalidCharactersCode),
      validMoneyWhenAllowedCharacters(money, moneyFormatCode)
    )
  }
}

object MiddleNameValidator extends MonthlyFileUploadColumnValidator with MonthlyFileUploadColumnValidationSupport {
  val header                        = "Middle Name"
  val maxLengthCode: String         = MiddleNameMaxLength
  val invalidCharactersCode: String = MiddleNameInvalidCharacters

  override def validate(row: FileUploadRow, context: MonthlyReturnFileUploadValidationContext): Seq[String] =
    presentErrors(
      maxLength(value(row, header), 50, maxLengthCode),
      invalidCharacters(value(row, header), nameAllowedCharactersPattern, invalidCharactersCode)
    )
}

object NationalInsuranceNumberValidator
    extends MonthlyFileUploadColumnValidator
    with MonthlyFileUploadColumnValidationSupport {
  val header                        = "National Insurance Number"
  val requiredCode: String          = NationalInsuranceRequired
  val formatCode: String            = NationalInsuranceFormat
  val invalidCharactersCode: String = NationalInsuranceInvalidCharacters
  val tooLongCode: String           = NationalInsuranceTooLong
  val tooShortCode: String          = NationalInsuranceTooShort

  private val minimumNinoLength = 8
  private val maximumNinoLength = 9

  private val ninoRegex =
    ("^([ACEHJLMOPRSWXY][A-CEGHJ-NPR-TW-Z]|B[A-CEHJ-NPR-TW-Z]|G[ACEGHJ-NPR-TW-Z]|" +
      "[KT][A-CEGHJ-MPR-TW-Z]|N[A-CEGHJL-NPR-SW-Z]|Z[A-CEGHJ-NPR-TW-Y])[0-9]{6}[A-D ]?$").r

  override def validate(row: FileUploadRow, context: MonthlyReturnFileUploadValidationContext): Seq[String] = {
    val nino                 = value(row, header)
    val normalisedNino       = nino.toUpperCase(Locale.ROOT)
    val hasAllowedCharacters = ninoAllowedCharactersPattern.matches(nino)

    presentErrors(
      required(nino, requiredCode),
      invalidCharacters(nino, ninoAllowedCharactersPattern, invalidCharactersCode),
      Option.when(nino.nonEmpty && hasAllowedCharacters && normalisedNino.length < minimumNinoLength)(tooShortCode),
      Option.when(nino.nonEmpty && hasAllowedCharacters && normalisedNino.length > maximumNinoLength)(tooLongCode),
      Option.when(
        nino.nonEmpty &&
          hasAllowedCharacters &&
          normalisedNino.length >= minimumNinoLength &&
          normalisedNino.length <= maximumNinoLength &&
          !ninoRegex.matches(normalisedNino)
      )(formatCode)
    )
  }
}

object ReportingPeriodValidator {
  def isInCurrentOrPreviousReportingMonth(date: LocalDate, taxYear: String, month: Int): Boolean = {
    val startYear     = taxYear.take(4).toInt
    val reportingYear = if (month >= 4) startYear else startYear + 1
    val currentMonth  = YearMonth.of(reportingYear, month)
    val currentStart  = currentMonth.atDay(1)
    val currentEnd    = currentMonth.atEndOfMonth()
    val previousStart = currentStart.minusMonths(1)
    val previousEnd   = currentStart.minusDays(1)
    val taxYearStart  = LocalDate.of(startYear, 4, 6)
    val taxYearEnd    = LocalDate.of(startYear + 1, 4, 5)

    !date.isBefore(taxYearStart) &&
    !date.isAfter(taxYearEnd) &&
    ((!date.isBefore(currentStart) && !date.isAfter(currentEnd)) ||
      (!date.isBefore(previousStart) && !date.isAfter(previousEnd)))
  }
}

object RowColumnCountValidator extends MonthlyFileUploadColumnValidator with MonthlyFileUploadColumnValidationSupport {
  val invalidCode: String = InvalidRowColumnCount

  override def validate(row: FileUploadRow, context: MonthlyReturnFileUploadValidationContext): Seq[String] =
    Option.when(row.rawColumnCount != MonthlyFileUploadTemplate.headers.length)(invalidCode).toSeq
}

object SurnameValidator extends MonthlyFileUploadColumnValidator with MonthlyFileUploadColumnValidationSupport {
  val header                        = "Surname"
  val requiredCode: String          = SurnameRequired
  val maxLengthCode: String         = SurnameMaxLength
  val invalidCharactersCode: String = SurnameInvalidCharacters

  override def validate(row: FileUploadRow, context: MonthlyReturnFileUploadValidationContext): Seq[String] = {
    val surname = value(row, header)

    presentErrors(
      required(surname, requiredCode),
      maxLength(surname, 50, maxLengthCode),
      invalidCharacters(surname, nameAllowedCharactersPattern, invalidCharactersCode)
    )
  }
}

object TransferInValidator extends MonthlyFileUploadColumnValidator with MonthlyFileUploadColumnValidationSupport {
  val header                        = "Total current year subscriptions transferred in"
  val requiredCode: String          = TransferInRequired
  val moneyFormatCode: String       = TransferInMoneyFormat
  val invalidCharactersCode: String = TransferInInvalidCharacters

  override def validate(row: FileUploadRow, context: MonthlyReturnFileUploadValidationContext): Seq[String] = {
    val money = value(row, header)

    presentErrors(
      required(money, requiredCode),
      invalidCharacters(money, moneyAllowedCharactersPattern, invalidCharactersCode),
      validMoneyWhenAllowedCharacters(money, moneyFormatCode)
    )
  }
}

object TransferOutValidator extends MonthlyFileUploadColumnValidator with MonthlyFileUploadColumnValidationSupport {
  val header                        = "Total current year subscriptions transferred out"
  val requiredCode: String          = TransferOutRequired
  val moneyFormatCode: String       = TransferOutMoneyFormat
  val invalidCharactersCode: String = TransferOutInvalidCharacters

  override def validate(row: FileUploadRow, context: MonthlyReturnFileUploadValidationContext): Seq[String] = {
    val money = value(row, header)

    presentErrors(
      required(money, requiredCode),
      invalidCharacters(money, moneyAllowedCharactersPattern, invalidCharactersCode),
      validMoneyWhenAllowedCharacters(money, moneyFormatCode)
    )
  }
}
