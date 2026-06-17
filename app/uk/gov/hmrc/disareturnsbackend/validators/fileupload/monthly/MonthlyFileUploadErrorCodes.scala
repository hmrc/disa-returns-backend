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

object MonthlyFileUploadErrorCodes {
  val InvalidRowColumnCount                     = "E001"
  val AccountNumberRequired                     = "E010"
  val AccountNumberMaxLength                    = "E011"
  val NationalInsuranceRequired                 = "E020"
  val NationalInsuranceFormat                   = "E021"
  val NationalInsuranceInvalidCharacters        = "E022"
  val NationalInsuranceTooLong                  = "E023"
  val NationalInsuranceTooShort                 = "E024"
  val FirstNameRequired                         = "E030"
  val FirstNameMaxLength                        = "E031"
  val FirstNameInvalidCharacters                = "E032"
  val MiddleNameMaxLength                       = "E040"
  val MiddleNameInvalidCharacters               = "E041"
  val SurnameRequired                           = "E050"
  val SurnameMaxLength                          = "E051"
  val SurnameInvalidCharacters                  = "E052"
  val DateOfBirthRequired                       = "E060"
  val DateOfBirthFormat                         = "E061"
  val DateOfBirthInvalidCharacters              = "E062"
  val IsaTypeRequired                           = "E070"
  val IsaTypeInvalid                            = "E071"
  val FlexibleIsaRequired                       = "E080"
  val FlexibleIsaInvalid                        = "E081"
  val TransferInRequired                        = "E090"
  val TransferInMoneyFormat                     = "E091"
  val TransferInInvalidCharacters               = "E092"
  val TransferOutRequired                       = "E100"
  val TransferOutMoneyFormat                    = "E101"
  val TransferOutInvalidCharacters              = "E102"
  val FirstSubscriptionRequired                 = "E110"
  val FirstSubscriptionDateFormat               = "E111"
  val FirstSubscriptionInvalidCharacters        = "E112"
  val LastSubscriptionRequired                  = "E120"
  val LastSubscriptionDateFormat                = "E121"
  val LastSubscriptionOutsideReportingPeriod    = "E122"
  val LastSubscriptionInvalidCharacters         = "E123"
  val CurrentYearSubscriptionsRequired          = "E130"
  val CurrentYearSubscriptionsMoneyFormat       = "E131"
  val CurrentYearSubscriptionsInvalidCharacters = "E132"
  val LisaQualifyingAdditionRequired            = "E140"
  val LisaQualifyingAdditionMoneyFormat         = "E141"
  val LisaQualifyingAdditionInvalidCharacters   = "E142"
  val LisaBonusClaimRequired                    = "E150"
  val LisaBonusClaimMoneyFormat                 = "E151"
  val LisaBonusClaimInvalidCharacters           = "E152"
  val MarketValueRequired                       = "E160"
  val MarketValueMoneyFormat                    = "E161"
  val MarketValueInvalidCharacters              = "E162"
  val ClosureDateFormat                         = "E170"
  val ClosureDateRequired                       = "E171"
  val ClosureDateInvalidCharacters              = "E172"
  val IsaClosureReasonRequired                  = "E180"
  val IsaClosureReasonInvalid                   = "E181"
  val IsaClosureReasonInvalidCharacters         = "E182"
  val LisaClosureReasonRequired                 = "E190"
  val LisaClosureReasonInvalid                  = "E191"
  val LisaClosureReasonInvalidCharacters        = "E192"
}
