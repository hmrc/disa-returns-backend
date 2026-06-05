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

import base.SpecBase

class ValidationHelperSpec extends SpecBase {

  "ZReferenceValidator.isValid" - {

    "must return true for a valid upper or lower case zReference" in {
      ZReferenceValidator.isValid(testZReference) mustBe true
      ZReferenceValidator.isValid(lowercaseTestZReference) mustBe true
    }

    "must return false for an invalid zReference" in {
      ZReferenceValidator.isValid(invalidTestZReference) mustBe false
      ZReferenceValidator.isValid("Z12345") mustBe false
      ZReferenceValidator.isValid(null) mustBe false
    }
  }

  "TaxYearValidator.isValid" - {

    "must return true for a valid tax year" in {
      TaxYearValidator.isValid(testTaxYear) mustBe true
    }

    "must return false for an invalid tax year" in {
      TaxYearValidator.isValid("2026-28") mustBe false
      TaxYearValidator.isValid(invalidTestTaxYear) mustBe false
      TaxYearValidator.isValid(null) mustBe false
    }
  }

  "MonthValidator.isValid" - {

    "must return true for a valid month" in {
      MonthValidator.isValid("1") mustBe true
      MonthValidator.isValid("12") mustBe true
    }

    "must return false for an invalid month" in {
      MonthValidator.isValid("0") mustBe false
      MonthValidator.isValid(invalidTestMonth.toString) mustBe false
      MonthValidator.isValid("MONTH") mustBe false
      MonthValidator.isValid(null) mustBe false
    }

    "must parse a valid month" in {
      MonthValidator.parse(testRouteMonth) mustBe Some(testMonth)
    }
  }

  "ValidationHelper.validateParams" - {

    "must normalise valid path parameters" in {
      ValidationHelper.validateParams(lowercaseTestZReference, testTaxYear, testRouteMonth) mustBe
        Right((testZReference, testTaxYear, testMonth))
    }

    "must return all invalid field names" in {
      ValidationHelper.validateParams(invalidTestZReference, invalidTestTaxYear, invalidTestMonth.toString) mustBe Left(
        s"Invalid monthly return submission fields: [$zReferenceFieldName, $taxYearFieldName, $monthFieldName]"
      )
    }
  }
}
