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

import base.SpecBase
import org.apache.pekko.actor.ActorSystem
import org.bson.types.ObjectId
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{timeout as mockitoTimeout, verify, when}
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem, WorkItemRepository}

import java.time.{Clock, Duration, Instant, ZoneOffset}
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Future, Promise}

class WorkItemJobSpec extends SpecBase {

  private val workerCount      =
    math.max(1, Runtime.getRuntime.availableProcessors() / 2)
  private val now              = Instant.parse("2026-06-08T12:00:00Z")
  private val clock            = Clock.fixed(now, ZoneOffset.UTC)
  private val testWorkItem     = WorkItem(
    id = new ObjectId(),
    receivedAt = now,
    updatedAt = now,
    availableAt = now,
    status = ProcessingStatus.ToDo,
    failureCount = 0,
    item = "test-item"
  )
  private val actorSystem      = inject[ActorSystem]
  private val dispatcherName   = "contexts.monthly-return-file-upload-work-item"
  private val pollInterval     = 1.hour
  private val failedRetryAfter = Duration.ofMinutes(1)

  "start" - {

    "must poll for outstanding work items using the configured clock and failed retry delay" in new TestSetup {
      when(mockWorkItemRepository.pullOutstanding(any[Instant], any[Instant]))
        .thenReturn(Future.successful(None))

      job.start()

      verify(mockWorkItemRepository, mockitoTimeout(1000).times(workerCount))
        .pullOutstanding(eqTo(now.minus(failedRetryAfter)), eqTo(now))
    }

    "must process an outstanding work item" in new TestSetup {
      when(mockWorkItemRepository.pullOutstanding(any[Instant], any[Instant]))
        .thenReturn(Future.successful(Some(testWorkItem)), Future.successful(None))

      job.start()

      processedWorkItem.future.futureValue mustBe testWorkItem
    }

    "must immediately poll again when a work item was processed" in new TestSetup {
      override def processResult: Future[Boolean] = Future.successful(true)

      when(mockWorkItemRepository.pullOutstanding(any[Instant], any[Instant]))
        .thenReturn(Future.successful(Some(testWorkItem)), Future.successful(None))

      job.start()

      verify(mockWorkItemRepository, mockitoTimeout(1000).times(workerCount + 1))
        .pullOutstanding(any[Instant], any[Instant])
    }

    "must only start once" in new TestSetup {
      when(mockWorkItemRepository.pullOutstanding(any[Instant], any[Instant]))
        .thenReturn(Future.successful(None))

      job.start()
      job.start()

      verify(mockWorkItemRepository, mockitoTimeout(1000).times(workerCount))
        .pullOutstanding(any[Instant], any[Instant])
    }

    "must stop scheduled workers when the lifecycle stops" in new TestSetup {
      override def jobPollInterval = 1.second

      val pullCount      = new AtomicInteger(0)
      val firstPollsDone = Promise[Unit]()

      when(mockWorkItemRepository.pullOutstanding(any[Instant], any[Instant]))
        .thenAnswer { _ =>
          if (pullCount.incrementAndGet() == workerCount) {
            firstPollsDone.trySuccess(())
          }

          Future.successful(None)
        }

      job.start()
      firstPollsDone.future.futureValue
      lifecycle.stop().futureValue
      Thread.sleep(jobPollInterval.toMillis + 100)

      pullCount.get() mustBe workerCount
    }
  }

  trait TestSetup {
    val lifecycle: CapturingLifecycle                      = new CapturingLifecycle
    val mockWorkItemRepository: WorkItemRepository[String] = mock[WorkItemRepository[String]]
    val processedWorkItem: Promise[WorkItem[String]]       = Promise[WorkItem[String]]()

    def processResult: Future[Boolean] = Future.successful(false)

    def jobPollInterval = pollInterval

    val job = new BaseWorkItemJob[String](
      actorSystem = actorSystem,
      clock = clock,
      lifecycle = lifecycle,
      workItemRepository = mockWorkItemRepository,
      dispatcherName = dispatcherName,
      pollInterval = jobPollInterval,
      failedRetryAfter = failedRetryAfter
    ) {
      override protected val jobName: String = "TestWorkItemJob"

      override protected def processWorkItem(
        workerId: Int,
        workItem: WorkItem[String]
      ): Future[Boolean] = {
        processedWorkItem.trySuccess(workItem)
        processResult
      }
    }
  }

  final class CapturingLifecycle extends ApplicationLifecycle {

    private var stopHook: () => Future[?] = () => Future.successful(())

    override def addStopHook(hook: () => Future[?]): Unit =
      stopHook = hook

    override def stop(): Future[?] =
      stopHook()
  }
}
