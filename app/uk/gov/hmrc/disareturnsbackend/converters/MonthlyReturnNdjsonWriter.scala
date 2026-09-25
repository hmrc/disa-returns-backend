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

import play.api.libs.json.Json
import uk.gov.hmrc.disareturnsbackend.models.*
import uk.gov.hmrc.disareturnsbackend.validators.fileupload.monthly.MonthlyFileUploadTemplate

import java.io.*
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.time.LocalDate
import javax.inject.*
import scala.util.Using

trait MonthlyReturnNdjsonWriter {
  def withOutput(output: Path)(write: BufferedWriter => Unit): Unit
  def writeRow(cells: Vector[String], writer: BufferedWriter): Unit
}

@Singleton
class MonthlyReturnNdjsonWriterImpl @Inject() () extends MonthlyReturnNdjsonWriter {
  override def withOutput(output: Path)(write: BufferedWriter => Unit): Unit =
    Using.resource(new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(output), StandardCharsets.UTF_8)))(
      write
    )

  override def writeRow(cells: Vector[String], writer: BufferedWriter): Unit = {
    writer.write(Json.stringify(Json.toJson(toSubmission(cells))))
    writer.newLine()
  }

  private def toSubmission(cells: Vector[String]): MonthlyReturnSubmission = {
    val values                                   = MonthlyFileUploadTemplate.headers.zipAll(cells.map(v => Option(v).getOrElse("")), "", "").toMap
    def value(header: String): String            = values.getOrElse(header, "").trim
    def decimal(header: String): BigDecimal      = BigDecimal(value(header).stripPrefix("£"))
    def optional(header: String): Option[String] = Option.when(value(header).nonEmpty)(value(header))
    def date(header: String): LocalDate          = LocalDate.parse(value(header))

    val isaType = MonthlyReturnIsaType.valueOf(if (value("ISA Type being reported") == "LISA") {
      "LIFETIME"
    } else {
      value("ISA Type being reported")
    })
    val common  = MonthlyReturnSubmissionCommon(
      accountNumber = value("Account Number"),
      nino = value("National Insurance Number"),
      firstName = value("First Name"),
      middleName = optional("Middle Name"),
      lastName = value("Surname"),
      dateOfBirth = date("Date of Birth"),
      amountTransferredIn = decimal("Total current year subscriptions transferred in"),
      amountTransferredOut = decimal("Total current year subscriptions transferred out"),
      dateOfLastSubscription = date("Date of last subscription event"),
      totalCurrentYearSubscriptionsToDate = decimal("Total current year to date subscriptions"),
      marketValueOfAccount = decimal("Market value of account"),
      isaType = isaType
    )

    optional("Closure Date") match {
      case Some(closureDate) if isaType == MonthlyReturnIsaType.LIFETIME =>
        MonthlyReturnLifetimeClosure(
          common = common,
          dateOfFirstSubscription = date("Date of first subscription event"),
          lisaQualifyingAddition = decimal("LISA qualifying addition"),
          lisaBonusClaim = decimal("LISA bonus claim"),
          closureDate = LocalDate.parse(closureDate),
          reasonForClosure = MonthlyReturnClosureReason.valueOf(value("LISA Reason for closure"))
        )
      case Some(closureDate)                                             =>
        val reason = MonthlyReturnClosureReason.valueOf(value("ISA Reason for closure"))
        require(
          reason != MonthlyReturnClosureReason.TRANSFERRED_IN_FULL && reason != MonthlyReturnClosureReason.ALL_FUNDS_WITHDRAWN,
          s"Invalid standard ISA closure reason [$reason]"
        )
        MonthlyReturnStandardClosure(
          common = common,
          flexibleIsa = value("Flexible ISA") == "Yes",
          closureDate = LocalDate.parse(closureDate),
          reasonForClosure = reason
        )
      case None if isaType == MonthlyReturnIsaType.LIFETIME              =>
        MonthlyReturnLifetimeSubscription(
          common = common,
          dateOfFirstSubscription = date("Date of first subscription event"),
          lisaQualifyingAddition = decimal("LISA qualifying addition"),
          lisaBonusClaim = decimal("LISA bonus claim")
        )
      case None                                                          =>
        MonthlyReturnStandardSubscription(common, flexibleIsa = value("Flexible ISA") == "Yes")
    }
  }
}
