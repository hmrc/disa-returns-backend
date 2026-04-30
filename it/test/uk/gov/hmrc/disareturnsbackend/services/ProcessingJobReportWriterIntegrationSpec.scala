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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import uk.gov.hmrc.disareturnsbackend.config.AppConfig

import java.nio.file.Files
import java.time.Instant

class ProcessingJobReportWriterIntegrationSpec extends AnyWordSpec with Matchers:

  private val expectedHeader = Vector(
    "workItemId",
    "workItemReceivedAt",
    "workItemAvailableAt",
    "workItemFailureCount",
    "queueWaitMillis",
    "startedAt",
    "finishedAt",
    "workerId",
    "filename",
    "fileSizeBytes",
    "rowsProcessed",
    "validRows",
    "invalidRows",
    "maxErrorsReached",
    "totalMillis",
    "jvmHeapUsedBytesStart",
    "jvmHeapUsedBytesPeak",
    "jvmHeapUsedBytesEnd",
    "processResidentMemoryBytesStart",
    "processResidentMemoryBytesPeak",
    "processResidentMemoryBytesEnd",
    "outcome",
    "errorMessage"
  )

  private val expectedRow = Vector(
    "work-item-1",
    "2026-01-01T00:00:00Z",
    "2026-01-01T00:01:00Z",
    "2",
    "60000",
    "2026-01-01T00:02:00Z",
    "2026-01-01T00:03:00Z",
    "7",
    "example.csv",
    "4096",
    "10",
    "8",
    "2",
    "false",
    "1234",
    "111",
    "222",
    "333",
    "444",
    "555",
    "666",
    "success",
    ""
  )

  private val row = CsvProcessingJobReportRow(
    workItemId = "work-item-1",
    workItemReceivedAt = Instant.parse("2026-01-01T00:00:00Z"),
    workItemAvailableAt = Instant.parse("2026-01-01T00:01:00Z"),
    workItemFailureCount = 2,
    queueWaitMillis = 60000L,
    startedAt = Instant.parse("2026-01-01T00:02:00Z"),
    finishedAt = Instant.parse("2026-01-01T00:03:00Z"),
    workerId = 7,
    filename = "example.csv",
    fileSizeBytes = 4096L,
    rowsProcessed = 10L,
    validRows = 8L,
    invalidRows = 2L,
    maxErrorsReached = false,
    totalMillis = 1234L,
    jvmHeapUsedBytesStart = 111L,
    jvmHeapUsedBytesPeak = 222L,
    jvmHeapUsedBytesEnd = 333L,
    processResidentMemoryBytesStart = Some(444L),
    processResidentMemoryBytesPeak = Some(555L),
    processResidentMemoryBytesEnd = Some(666L),
    outcome = "success",
    errorMessage = None
  )

  "CsvProcessingJobReportWriter" should:
    "write the expected header and row layout" in:
      assertWriterOutput { reportFile =>
        new CsvProcessingJobReportWriter(
          new AppConfig(
            Configuration(
              "appName" -> "disa-returns-backend-test",
              "csvProcessingJob.reportFile" -> reportFile.toString
            )
          )
        ).write(row)
      }

  "XlsxProcessingJobReportWriter" should:
    "write the expected header and row layout" in:
      assertWriterOutput { reportFile =>
        new XlsxProcessingJobReportWriter(
          new AppConfig(
            Configuration(
              "appName" -> "disa-returns-backend-test",
              "xlsxProcessingJob.reportFile" -> reportFile.toString
            )
          )
        ).write(
          XlsxProcessingJobReportRow(
            workItemId = "work-item-1",
            workItemReceivedAt = Instant.parse("2026-01-01T00:00:00Z"),
            workItemAvailableAt = Instant.parse("2026-01-01T00:01:00Z"),
            workItemFailureCount = 2,
            queueWaitMillis = 60000L,
            startedAt = Instant.parse("2026-01-01T00:02:00Z"),
            finishedAt = Instant.parse("2026-01-01T00:03:00Z"),
            workerId = 7,
            filename = "example.csv",
            fileSizeBytes = 4096L,
            rowsProcessed = 10L,
            validRows = 8L,
            invalidRows = 2L,
            maxErrorsReached = false,
            totalMillis = 1234L,
            jvmHeapUsedBytesStart = 111L,
            jvmHeapUsedBytesPeak = 222L,
            jvmHeapUsedBytesEnd = 333L,
            processResidentMemoryBytesStart = Some(444L),
            processResidentMemoryBytesPeak = Some(555L),
            processResidentMemoryBytesEnd = Some(666L),
            outcome = "success",
            errorMessage = None
          )
        )
      }

  private def assertWriterOutput(writeRow: java.nio.file.Path => Unit): Unit =
    val reportFile = Files.createTempDirectory("report-writer-test").resolve("report.csv")

    try {
      writeRow(reportFile)

      val lines = Files.readAllLines(reportFile)
      lines.size() shouldBe 2
      lines.get(0).split(",", -1).toVector shouldBe expectedHeader
      lines.get(1).split(",", -1).toVector shouldBe expectedRow
    } finally {
      Files.deleteIfExists(reportFile)
      Files.deleteIfExists(reportFile.getParent)
    }
