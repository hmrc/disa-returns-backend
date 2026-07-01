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

package uk.gov.hmrc.disareturnsbackend.jobs

import org.apache.pekko.actor.ActorSystem
import play.api.Logging
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.mongo.workitem.{WorkItem, WorkItemRepository}

import java.time.{Clock, Duration}
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

abstract class BaseWorkItemJob[A](
  actorSystem: ActorSystem,
  clock: Clock,
  lifecycle: ApplicationLifecycle,
  workItemRepository: WorkItemRepository[A],
  dispatcherName: String,
  pollInterval: FiniteDuration,
  failedRetryAfter: Duration
) extends Logging {

  private val workerCount =
    math.max(1, Runtime.getRuntime.availableProcessors() / 2)
  private val started     = new AtomicBoolean(false)
  private val stopping    = new AtomicBoolean(false)

  protected implicit val workerExecutionContext: ExecutionContext =
    actorSystem.dispatchers.lookup(dispatcherName)

  protected def jobName: String

  lifecycle.addStopHook { () =>
    stop()
    Future.successful(())
  }

  def start(): Unit =
    if (started.compareAndSet(false, true)) {
      logger.info(s"[$jobName][start] Starting with $workerCount workers")
      stopping.set(false)
      (1 to workerCount).foreach(startWorker)
    }

  private def stop(): Unit =
    stopping.set(true)

  private def startWorker(workerId: Int): Unit =
    runWorker(workerId)

  private def runWorker(workerId: Int): Unit = {
    if (stopping.get()) {
      logger.info(s"[$jobName][runWorker] Worker $workerId stopping")
      return
    }

    processNextWorkItem(workerId)
      .recover { case exception =>
        logger.error(
          s"[$jobName][runWorker] Worker $workerId failed while processing work items",
          exception
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

    workItemRepository.pullOutstanding(now.minus(failedRetryAfter), now).flatMap {
      case Some(workItem) =>
        processWorkItem(workerId, workItem)
      case None           =>
        Future.successful(false)
    }
  }

  protected def processWorkItem(workerId: Int, workItem: WorkItem[A]): Future[Boolean]
}
