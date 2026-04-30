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

import play.api.Logging
import uk.gov.hmrc.disareturnsbackend.config.AppConfig

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}
import java.time.Instant
import javax.inject.{Inject, Singleton}

// Memory fields are process/JVM-wide snapshots taken while the work item runs.
// JVM heap fields are JVM heap snapshots; resident memory fields are process RSS snapshots where available.
// Neither set represents memory owned by a single file or work item.
final case class XlsxProcessingJobReportRow(
    workItemId: String,
    workItemReceivedAt: Instant,
    workItemAvailableAt: Instant,
    workItemFailureCount: Int,
    queueWaitMillis: Long,
    startedAt: Instant,
    finishedAt: Instant,
    workerId: Int,
    filename: String,
    fileSizeBytes: Long,
    rowsProcessed: Long,
    validRows: Long,
    invalidRows: Long,
    maxErrorsReached: Boolean,
    totalMillis: Long,
    jvmHeapUsedBytesStart: Long,
    jvmHeapUsedBytesPeak: Long,
    jvmHeapUsedBytesEnd: Long,
    processResidentMemoryBytesStart: Option[Long],
    processResidentMemoryBytesPeak: Option[Long],
    processResidentMemoryBytesEnd: Option[Long],
    outcome: String,
    errorMessage: Option[String]
)

@Singleton
class XlsxProcessingJobReportWriter @Inject() (
    appConfig: AppConfig
) extends Logging {

  private val reportFile: Path = appConfig.xlsxProcessingJobReportFile
  private val header: String =
    List(
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
    ).mkString(",") + "\n"

  def write(row: XlsxProcessingJobReportRow): Unit =
    synchronized {
      try {
        Files.createDirectories(reportFile.getParent)

        if (!Files.exists(reportFile)) {
          Files.writeString(
            reportFile,
            header,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE
          )
        }

        Files.writeString(
          reportFile,
          renderRow(row),
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND
        )
      } catch {
        case ex: Exception =>
          logger.warn(
            s"[XlsxProcessingJobReportWriter] Failed to write report row to $reportFile",
            ex
          )
      }
    }

  private def renderRow(row: XlsxProcessingJobReportRow): String =
    List(
      row.workItemId,
      row.workItemReceivedAt,
      row.workItemAvailableAt,
      row.workItemFailureCount,
      row.queueWaitMillis,
      row.startedAt,
      row.finishedAt,
      row.workerId,
      row.filename,
      row.fileSizeBytes,
      row.rowsProcessed,
      row.validRows,
      row.invalidRows,
      row.maxErrorsReached,
      row.totalMillis,
      row.jvmHeapUsedBytesStart,
      row.jvmHeapUsedBytesPeak,
      row.jvmHeapUsedBytesEnd,
      row.processResidentMemoryBytesStart.fold("")(_.toString),
      row.processResidentMemoryBytesPeak.fold("")(_.toString),
      row.processResidentMemoryBytesEnd.fold("")(_.toString),
      row.outcome,
      row.errorMessage.getOrElse("")
    ).map(csv).mkString(",") + "\n"

  private def csv(value: Any): String = {
    val raw = String.valueOf(value)
    if (raw.exists(ch => ch == ',' || ch == '"' || ch == '\n' || ch == '\r')) {
      "\"" + raw.replace("\"", "\"\"") + "\""
    } else {
      raw
    }
  }
}
