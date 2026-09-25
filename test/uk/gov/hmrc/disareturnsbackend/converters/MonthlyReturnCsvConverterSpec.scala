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

package uk.gov.hmrc.disareturnsbackend.converters

import base.SpecBase
import play.api.libs.json.Json
import uk.gov.hmrc.disareturnsbackend.validators.fileupload.monthly.MonthlyFileUploadTemplate

import java.nio.charset.StandardCharsets
import java.nio.file.Files

class MonthlyReturnCsvConverterSpec extends SpecBase {
  "MonthlyReturnNdjsonWriter" - {
    "must produce all four ISA payload shapes with typed dates, money and LIFETIME ISA type" in {
      val common         = Map(
        "Account Number"                                   -> "ACC123",
        "National Insurance Number"                        -> "AB123456C",
        "First Name"                                       -> "Ada",
        "Surname"                                          -> "Lovelace",
        "Date of Birth"                                    -> "1980-01-02",
        "Total current year subscriptions transferred in"  -> "£10.00",
        "Total current year subscriptions transferred out" -> "0.00",
        "Date of last subscription event"                  -> "2026-05-01",
        "Total current year to date subscriptions"         -> "20.00",
        "Market value of account"                          -> "100.00"
      )
      val expectedCommon = Json.obj(
        "accountNumber"                       -> "ACC123",
        "nino"                                -> "AB123456C",
        "firstName"                           -> "Ada",
        "lastName"                            -> "Lovelace",
        "dateOfBirth"                         -> "1980-01-02",
        "amountTransferredIn"                 -> BigDecimal("10.00"),
        "amountTransferredOut"                -> BigDecimal("0.00"),
        "dateOfLastSubscription"              -> "2026-05-01",
        "totalCurrentYearSubscriptionsToDate" -> BigDecimal("20.00"),
        "marketValueOfAccount"                -> BigDecimal("100.00")
      )
      val lifetimeValues = Map(
        "ISA Type being reported"          -> "LISA",
        "Date of first subscription event" -> "2026-04-07",
        "LISA qualifying addition"         -> "3.00",
        "LISA bonus claim"                 -> "4.00"
      )
      val lifetimeFields = Json.obj(
        "isaType"                 -> "LIFETIME",
        "dateOfFirstSubscription" -> "2026-04-07",
        "lisaQualifyingAddition"  -> BigDecimal("3.00"),
        "lisaBonusClaim"          -> BigDecimal("4.00")
      )
      val scenarios      = Seq(
        (
          Map("ISA Type being reported" -> "CASH", "Flexible ISA" -> "Yes"),
          expectedCommon ++ Json.obj("isaType" -> "CASH", "flexibleIsa" -> true)
        ),
        (
          Map(
            "ISA Type being reported" -> "CASH",
            "Flexible ISA"            -> "No",
            "Closure Date"            -> "2026-05-02",
            "ISA Reason for closure"  -> "CLOSED"
          ),
          expectedCommon ++ Json.obj(
            "isaType"          -> "CASH",
            "flexibleIsa"      -> false,
            "closureDate"      -> "2026-05-02",
            "reasonForClosure" -> "CLOSED"
          )
        ),
        (lifetimeValues, expectedCommon ++ lifetimeFields),
        (
          lifetimeValues ++ Map("Closure Date" -> "2026-05-02", "LISA Reason for closure" -> "TRANSFERRED_IN_FULL"),
          expectedCommon ++ lifetimeFields ++ Json
            .obj("closureDate" -> "2026-05-02", "reasonForClosure" -> "TRANSFERRED_IN_FULL")
        )
      )

      val writer = inject[MonthlyReturnNdjsonWriter]
      val output = Files.createTempFile("monthly-return-payload-", ".ndjson")
      try {
        writer.withOutput(output) { file =>
          scenarios.foreach { case (values, _) =>
            writer.writeRow(
              MonthlyFileUploadTemplate.headers.map(header => (common ++ values).getOrElse(header, "")),
              file
            )
          }
        }

        Files.readAllLines(output, StandardCharsets.UTF_8).toArray.toSeq.map(line => Json.parse(line.toString)) mustBe
          scenarios.map(_._2)
      } finally Files.deleteIfExists(output)
    }
  }

  "MonthlyReturnCsvConverter" - {
    "must convert a monthly CSV row to NDJSON fields and numeric values" in {
      val input  = Files.createTempFile("monthly-return-converter-", ".csv")
      val output = Files.createTempFile("monthly-return-converter-", ".ndjson")
      try {
        Files.writeString(
          input,
          "Account Number,National Insurance Number,First Name,Middle Name,Surname,Date of Birth,ISA Type being reported,Flexible ISA,Total current year subscriptions transferred in,Total current year subscriptions transferred out,Date of first subscription event,Date of last subscription event,Total current year to date subscriptions,LISA qualifying addition,LISA bonus claim,Market value of account,Closure Date,ISA Reason for closure,LISA Reason for closure\n" +
            "ACC123,AB123456C,Ada,,Lovelace,1980-01-02,CASH,Yes,10.00,0.00,,2026-05-01,20.00,,,100.00,,,\n",
          StandardCharsets.UTF_8
        )

        inject[MonthlyReturnCsvConverter].convert(input, output)

        val lines = Files.readAllLines(output, StandardCharsets.UTF_8)
        lines.size() mustBe 1
        val row   = Json.parse(lines.get(0))
        (row \ "accountNumber").as[String] mustBe "ACC123"
        (row \ "flexibleIsa").as[Boolean] mustBe true
        (row \ "amountTransferredIn").as[BigDecimal] mustBe BigDecimal("10.00")
      } finally {
        Files.deleteIfExists(input)
        Files.deleteIfExists(output)
      }
    }
  }

}
