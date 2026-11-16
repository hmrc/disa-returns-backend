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

  "MonthValidator.parse" - {

    "must parse a valid month including the range boundaries" in {
      MonthValidator.parse("1") mustBe Some(1)
      MonthValidator.parse("12") mustBe Some(12)
      MonthValidator.parse(testRouteMonth) mustBe Some(testMonth)
    }

    "must reject an invalid month" in {
      MonthValidator.parse("0") mustBe None
      MonthValidator.parse(invalidTestMonth.toString) mustBe None
      MonthValidator.parse("MONTH") mustBe None
      MonthValidator.parse(null) mustBe None
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
