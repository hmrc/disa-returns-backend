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

import uk.gov.hmrc.disareturnsbackend.utils.Constants.{CSV_MIME_TYPE, XLSX_MIME_TYPE}

import java.util.Locale
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.disareturnsbackend.validators.fileupload.{FileUploadValidator, FileUploadValidatorSelector}

@Singleton
class MonthlyReturnFileValidatorSelector @Inject() (
  csvValidator: MonthlyCsvFileUploadValidator,
  xlsxValidator: MonthlyXlsxFileUploadValidator
) extends FileUploadValidatorSelector[MonthlyReturnFileUploadValidationContext] {

  override def select(
    fileMimeType: String
  ): Either[String, FileUploadValidator[MonthlyReturnFileUploadValidationContext]] =
    Option(fileMimeType).map(_.toLowerCase(Locale.ROOT).trim) match {
      case Some(CSV_MIME_TYPE)  =>
        Right(csvValidator)
      case Some(XLSX_MIME_TYPE) =>
        Right(xlsxValidator)
      case Some(other)          =>
        Left(other)
      case None                 =>
        Left("")
    }
}
