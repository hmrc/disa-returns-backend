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

package uk.gov.hmrc.disareturnsbackend

import base.SpecBase
import org.apache.pekko.Done
import org.mockito.Mockito.*
import uk.gov.hmrc.disareturnsbackend.config.AppConfig
import uk.gov.hmrc.disareturnsbackend.config.InternalAuthTokenInitialiser
import uk.gov.hmrc.disareturnsbackend.jobs.*

import scala.concurrent.Future

class AppInitialiserSpec extends SpecBase {

  "AppInitialiser" - {
    "must complete construction and start the work-item job when internal-auth initialisation succeeds" in {
      val initialiser = internalAuthTokenInitialiser(Future.successful(Done))
      val job         = mock[MonthlyReturnWorkItemJob]
      val transferJob = mock[MonthlyReturnSubmissionWorkItemJob]

      val appInitialiser = new AppInitialiser(initialiser, jobConfig(), job, transferJob)

      appInitialiser.initialised.futureValue mustBe Done
      verify(job).start()
      verify(transferJob).start()
    }

    Seq((false, true), (true, false), (false, false)).foreach { case (validationEnabled, transferEnabled) =>
      s"must start only enabled jobs when validation is [$validationEnabled] and transfer is [$transferEnabled]" in {
        val initialiser = internalAuthTokenInitialiser(Future.successful(Done))
        val job         = mock[MonthlyReturnWorkItemJob]
        val transferJob = mock[MonthlyReturnSubmissionWorkItemJob]

        new AppInitialiser(initialiser, jobConfig(validationEnabled, transferEnabled), job, transferJob)

        if (validationEnabled) {
          verify(job).start()
        } else {
          verify(job, never()).start()
        }
        if (transferEnabled) {
          verify(transferJob).start()
        } else {
          verify(transferJob, never()).start()
        }
      }
    }

    "must fail construction without starting the work-item job when internal-auth initialisation fails" in {
      val exception   = new RuntimeException("Internal-auth initialisation failed")
      val initialiser = internalAuthTokenInitialiser(Future.failed(exception))
      val job         = mock[MonthlyReturnWorkItemJob]
      val transferJob = mock[MonthlyReturnSubmissionWorkItemJob]

      val thrown = intercept[RuntimeException] {
        new AppInitialiser(initialiser, jobConfig(), job, transferJob)
      }

      thrown mustBe exception
      verify(job, never).start()
      verify(transferJob, never).start()
    }
  }

  private def internalAuthTokenInitialiser(result: Future[Done]): InternalAuthTokenInitialiser =
    new InternalAuthTokenInitialiser {
      override protected def initialise(): Future[Done] = result
    }

  private def jobConfig(validationEnabled: Boolean = true, transferEnabled: Boolean = true): AppConfig = {
    val config = mock[AppConfig]
    when(config.monthlyReturnFileUploadJobEnabled).thenReturn(validationEnabled)
    when(config.monthlyReturnSubmissionJobEnabled).thenReturn(transferEnabled)
    config
  }
}
