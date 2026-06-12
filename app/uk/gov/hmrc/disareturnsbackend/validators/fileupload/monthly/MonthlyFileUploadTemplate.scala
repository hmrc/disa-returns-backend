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

import uk.gov.hmrc.disareturnsbackend.validators.fileupload.FileUploadSchema

object MonthlyFileUploadTemplate extends FileUploadSchema {
  override val headers: Vector[String] = Vector(
    "Account Number",
    "National Insurance Number",
    "First Name",
    "Middle Name",
    "Surname",
    "Date of Birth",
    "ISA Type being reported",
    "Flexible ISA",
    "Total current year subscriptions transferred in",
    "Total current year subscriptions transferred out",
    "Date of first subscription event",
    "Date of last subscription event",
    "Total current year to date subscriptions",
    "LISA qualifying addition",
    "LISA bonus claim",
    "Market value of account",
    "Closure Date",
    "ISA Reason for closure",
    "LISA Reason for closure"
  )

  override val xlsxRuleRowIndexZeroBased      = 0
  override val xlsxHeaderRowIndexZeroBased    = 1
  override val xlsxDataStartRowIndexZeroBased = 2

  override val csvHeaderRowIndexZeroBased    = 0
  override val csvDataStartRowIndexZeroBased = 1
}
