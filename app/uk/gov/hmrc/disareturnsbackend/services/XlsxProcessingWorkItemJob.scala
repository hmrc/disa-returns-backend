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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.FileIO
import play.api.Logging
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.disareturnsbackend.connectors.*
import uk.gov.hmrc.disareturnsbackend.models.*
import uk.gov.hmrc.disareturnsbackend.repositories.*
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.workitem.*

import java.lang.management.ManagementFactory
import java.nio.file.{Files, Path}
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class XlsxProcessingWorkItemJob @Inject() (
    actorSystem: ActorSystem,
    lifecycle: ApplicationLifecycle,
    clock: Clock,
    xlsxProcessingService: XlsxProcessingService,
    xlsxProcessingWorkItemRepository: XlsxProcessingWorkItemRepository,
    fileUploadRepository: FileUploadRepository,
    objectStoreConnector: ObjectStoreConnector,
    upscanConnector: UpscanConnector,
    jobReportWriter: XlsxProcessingJobReportWriter,
    processResidentMemorySampler: ProcessResidentMemorySampler
)(implicit mat: Materializer)
    extends Logging
    with TempFileSupport {

  private val pollInterval = 10.seconds
  private val workerCount  = math.max(1, Runtime.getRuntime.availableProcessors() / 2)
  private val started      = new AtomicBoolean(false)
  private val stopping     = new AtomicBoolean(false)

  private implicit val workerExecutionContext: ExecutionContext = {
    actorSystem.dispatchers.lookup("contexts.xlsx-processing-job")
  }

  lifecycle.addStopHook { () =>
    stop()
    Future.successful(())
  }

  def start(): Unit =
    if (started.compareAndSet(false, true)) {
      stopping.set(false)
      (1 to workerCount).foreach(startWorker)
    }

  private def stop(): Unit =
    stopping.set(true)

  private def startWorker(workerId: Int): Unit = {
    runWorker(workerId)
  }

  private def runWorker(workerId: Int): Unit = {
    if (stopping.get()) {
      logger.info(s"[XlsxProcessingWorkItemJob] Worker $workerId stopping")
      return
    }

    processNextWorkItem(workerId)
      .recover { case ex =>
        logger.error(
          s"[XlsxProcessingWorkItemJob] Worker $workerId failed while processing work items",
          ex
        )
        false
      }
      .foreach { workItemWasProcessed =>
        if (!stopping.get()) {
          if (workItemWasProcessed) {
            runWorker(workerId)
          } else {
            actorSystem.scheduler.scheduleOnce(pollInterval) {
              runWorker(workerId)
            }
          }
        }
      }
  }

  private def processNextWorkItem(workerId: Int): Future[Boolean] = {
    val now = clock.instant()

    xlsxProcessingWorkItemRepository.pullOutstanding(now, now).flatMap {
      case Some(workItem) =>
        processWorkItem(workerId, workItem)
      case None =>
        Future.successful(false)
    }
  }

  private def processWorkItem(
      workerId: Int,
      workItem: WorkItem[XlsxProcessingWorkItem]
  ): Future[Boolean] = {
    val filename             = workItem.item.filename
    val workItemId           = workItem.id.toString
    val workItemReceivedAt   = workItem.receivedAt
    val workItemAvailableAt  = workItem.availableAt
    val workItemFailureCount = workItem.failureCount
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val startedAt = clock.instant()
    val startedNanos = System.nanoTime()
    val queueWaitMillis =
      math.max(0L, java.time.Duration.between(workItemAvailableAt, startedAt).toMillis)
    val initialHeapUsedBytes = heapUsedBytes()
    val initialProcessResidentMemoryBytes = processResidentMemorySampler.currentProcessResidentMemoryBytes()
    var peakHeapUsedBytes = initialHeapUsedBytes
    var peakProcessResidentMemoryBytes = initialProcessResidentMemoryBytes
    var fileSizeBytes = 0L

    def updatePeakHeap(): Unit = {
      peakHeapUsedBytes = math.max(peakHeapUsedBytes, heapUsedBytes())
    }

    def updatePeakProcessResidentMemory(): Unit = {
      peakProcessResidentMemoryBytes = maxObserved(
        peakProcessResidentMemoryBytes,
        processResidentMemorySampler.currentProcessResidentMemoryBytes()
      )
    }

    withTempFile("disa-return-", ".xlsx") { tempFile =>
      for {
        source <- upscanConnector.downloadFile(filename)
        _ <- source.runWith(FileIO.toPath(tempFile)).andThen { case _ =>
          updatePeakHeap()
          updatePeakProcessResidentMemory()
        }
        _ = {
          fileSizeBytes = Files.size(tempFile)
          updatePeakHeap()
          updatePeakProcessResidentMemory()
        }
        report <- xlsxProcessingService.validate(tempFile).andThen { case _ =>
          updatePeakHeap()
          updatePeakProcessResidentMemory()
        }
        _ <- storeValidatedFile(tempFile, report).andThen { case _ =>
          updatePeakHeap()
          updatePeakProcessResidentMemory()
        }
        _ <- updateFileUploadStatus(filename, report).andThen { case _ =>
          updatePeakHeap()
          updatePeakProcessResidentMemory()
        }
        _ <- xlsxProcessingWorkItemRepository
          .completeAndDelete(workItem.id)
          .andThen { case _ =>
            updatePeakHeap()
            updatePeakProcessResidentMemory()
          }
      } yield {
        val finishedAt = clock.instant()
        val totalMillis = (System.nanoTime() - startedNanos) / 1000000L
        val finalHeapUsed = heapUsedBytes()
        val finalProcessResidentMemoryBytes = processResidentMemorySampler.currentProcessResidentMemoryBytes()
        val finalPeakUsed = math.max(peakHeapUsedBytes, finalHeapUsed)
        val finalPeakProcessResidentMemoryBytes = maxObserved(
          peakProcessResidentMemoryBytes,
          finalProcessResidentMemoryBytes
        )
        logger.info(
          s"[XlsxProcessingWorkItemJob] Worker $workerId completed work item for $filename; submitted=${report.isValid}; failureCount=$workItemFailureCount"
        )
        jobReportWriter.write(
          XlsxProcessingJobReportRow(
            workItemId = workItemId,
            workItemReceivedAt = workItemReceivedAt,
            workItemAvailableAt = workItemAvailableAt,
            workItemFailureCount = workItemFailureCount,
            queueWaitMillis = queueWaitMillis,
            startedAt = startedAt,
            finishedAt = finishedAt,
            workerId = workerId,
            filename = filename,
            fileSizeBytes = fileSizeBytes,
            rowsProcessed = report.rowsProcessed,
            validRows = report.validRows,
            invalidRows = report.invalidRows,
            maxErrorsReached = report.maxErrorsReached,
            totalMillis = totalMillis,
            jvmHeapUsedBytesStart = initialHeapUsedBytes,
            jvmHeapUsedBytesPeak = finalPeakUsed,
            jvmHeapUsedBytesEnd = finalHeapUsed,
            processResidentMemoryBytesStart = initialProcessResidentMemoryBytes,
            processResidentMemoryBytesPeak = finalPeakProcessResidentMemoryBytes,
            processResidentMemoryBytesEnd = finalProcessResidentMemoryBytes,
            outcome = "success",
            errorMessage = None
          )
        )
        true
      }
    }.recoverWith { case exception =>
      val finishedAt = clock.instant()
      val totalMillis = (System.nanoTime() - startedNanos) / 1000000L
      val finalHeapUsed = heapUsedBytes()
      val finalProcessResidentMemoryBytes = processResidentMemorySampler.currentProcessResidentMemoryBytes()
      val finalPeakUsed = math.max(peakHeapUsedBytes, finalHeapUsed)
      val finalPeakProcessResidentMemoryBytes = maxObserved(
        peakProcessResidentMemoryBytes,
        finalProcessResidentMemoryBytes
      )
      logger.error(
        s"[XlsxProcessingWorkItemJob] Worker $workerId failed processing work item for $filename; failureCount=$workItemFailureCount",
        exception
      )
      fileUploadRepository
        .markParseFailed(
          filename,
          Seq(FileUploadParseError(0, None, exception.getMessage))
        )
        .flatMap(_ =>
          xlsxProcessingWorkItemRepository
            .markAs(workItem.id, ProcessingStatus.Failed)
        )
        .map { _ =>
          jobReportWriter.write(
            XlsxProcessingJobReportRow(
              workItemId = workItemId,
              workItemReceivedAt = workItemReceivedAt,
              workItemAvailableAt = workItemAvailableAt,
              workItemFailureCount = workItemFailureCount,
              queueWaitMillis = queueWaitMillis,
              startedAt = startedAt,
              finishedAt = finishedAt,
              workerId = workerId,
              filename = filename,
              fileSizeBytes = fileSizeBytes,
              rowsProcessed = 0L,
              validRows = 0L,
              invalidRows = 0L,
              maxErrorsReached = false,
              totalMillis = totalMillis,
              jvmHeapUsedBytesStart = initialHeapUsedBytes,
              jvmHeapUsedBytesPeak = finalPeakUsed,
              jvmHeapUsedBytesEnd = finalHeapUsed,
              processResidentMemoryBytesStart = initialProcessResidentMemoryBytes,
              processResidentMemoryBytesPeak = finalPeakProcessResidentMemoryBytes,
              processResidentMemoryBytesEnd = finalProcessResidentMemoryBytes,
              outcome = "failed",
              errorMessage = Some(exception.getMessage)
            )
          )
          true
        }
    }
  }

  private def storeValidatedFile(
      tempFile: Path,
      report: XlsxValidationReport
  )(implicit hc: HeaderCarrier): Future[Unit] = {
    if (report.isValid) {
      objectStoreConnector.putObject(tempFile, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").map(_ => ())
    } else {
      Future.successful(())
    }
  }

  private def toParseError(error: XlsxValidationError): FileUploadParseError = {
    FileUploadParseError(
      rowNumber = error.rowNumber,
      cell = error.cell,
      errorMessage = error.message
    )
  }

  private def updateFileUploadStatus(
      filename: String,
      report: XlsxValidationReport
  ): Future[Unit] = {
    if (report.isValid) {
      fileUploadRepository.markParseSucceeded(filename)
    } else {
      fileUploadRepository.markParseFailed(
        filename,
        report.errors.map(toParseError)
      )
    }
  }

  private def heapUsedBytes(): Long =
    ManagementFactory.getMemoryMXBean.getHeapMemoryUsage.getUsed

  private def maxObserved(left: Option[Long], right: Option[Long]): Option[Long] =
    (left, right) match {
      case (Some(l), Some(r)) => Some(math.max(l, r))
      case (Some(l), None)    => Some(l)
      case (None, Some(r))    => Some(r)
      case (None, None)       => None
    }
}
