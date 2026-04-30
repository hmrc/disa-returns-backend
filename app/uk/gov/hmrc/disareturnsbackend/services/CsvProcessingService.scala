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

package uk.gov.hmrc.disareturnsbackend.services

import com.univocity.parsers.csv.{CsvParser, CsvParserSettings}
import org.apache.pekko.actor.ActorSystem
import play.api.Logging
import uk.gov.hmrc.disareturnsbackend.models.{CsvValidationError, CsvValidationReport}

import java.io.{BufferedReader, InputStreamReader}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContextExecutor, Future, blocking}
import scala.util.Using

@Singleton
class CsvProcessingService @Inject() (
    actorSystem: ActorSystem
)
    extends Logging {

  private val maxReportedErrors = 100

  private val blockingExecutionContext: ExecutionContextExecutor =
    actorSystem.dispatchers.lookup("contexts.blocking")

  def validate(file: Path): Future[CsvValidationReport] =
    Future {
      blocking {
        val settings = new CsvParserSettings()
        settings.setHeaderExtractionEnabled(false)
        settings.setLineSeparatorDetectionEnabled(true)
        settings.setIgnoreLeadingWhitespaces(false)
        settings.setIgnoreTrailingWhitespaces(false)

        val parser = new CsvParser(settings)

        Using.resource(
          new BufferedReader(
            new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)
          )
        ) { reader =>
          parser.beginParsing(reader)

          try {
            buildValidationReport(parser)
          } finally {
            parser.stopParsing()
          }
        }
      }
    }(blockingExecutionContext)

  private def buildValidationReport(parser: CsvParser): CsvValidationReport = {
    var rowsProcessed    = 0L
    var validRows        = 0L
    var invalidRows      = 0L
    var expectedColumns  = Option.empty[Int]
    val reportedErrors   = Vector.newBuilder[CsvValidationError]
    var errorsReported   = 0
    var maxErrorsReached = false

    var row = parser.parseNext()
    while (row != null) {
      rowsProcessed += 1

      val columns = row.toIndexedSeq
      val errors  = validateRow(rowsProcessed, columns, expectedColumns)

      if (rowsProcessed == 1 && errors.isEmpty) {
        expectedColumns = Some(columns.length)
      }

      if (errors.isEmpty) {
        validRows += 1
      } else {
        invalidRows += 1
        errors.foreach { error =>
          if (errorsReported < maxReportedErrors) {
            reportedErrors += error
            errorsReported += 1
          } else {
            maxErrorsReached = true
          }
        }
      }

      row = parser.parseNext()
    }

    if (rowsProcessed == 0) {
      CsvValidationReport(
        rowsProcessed = 0,
        validRows = 0,
        invalidRows = 1,
        errors = Seq(CsvValidationError(1, None, "CSV file is empty")),
        maxErrorsReached = false
      )
    } else {
      CsvValidationReport(
        rowsProcessed = rowsProcessed,
        validRows = validRows,
        invalidRows = invalidRows,
        errors = reportedErrors.result(),
        maxErrorsReached = maxErrorsReached
      )
    }
  }

  private def validateRow(
      rowNumber: Long,
      columns: IndexedSeq[String],
      expectedColumns: Option[Int]
  ): Seq[CsvValidationError] = {
    val errors = Vector.newBuilder[CsvValidationError]

    if (columns.isEmpty || columns.forall(value => Option(value).forall(_.trim.isEmpty))) {
      errors += CsvValidationError(rowNumber, None, "Row is empty")
    }

    expectedColumns.foreach { expected =>
      if (columns.length != expected) {
        errors += CsvValidationError(rowNumber, None, s"Expected $expected columns but found ${columns.length}")
      }
    }

    errors.result()
  }
}
