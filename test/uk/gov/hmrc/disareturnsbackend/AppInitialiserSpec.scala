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
import org.mockito.Mockito.{never, verify}
import uk.gov.hmrc.disareturnsbackend.config.InternalAuthTokenInitialiser
import uk.gov.hmrc.disareturnsbackend.jobs.MonthlyReturnWorkItemJob

import scala.concurrent.Future

class AppInitialiserSpec extends SpecBase {

  "AppInitialiser" - {
    "must complete construction and start the work-item job when internal-auth initialisation succeeds" in {
      val initialiser = internalAuthTokenInitialiser(Future.successful(Done))
      val job         = mock[MonthlyReturnWorkItemJob]

      val appInitialiser = new AppInitialiser(initialiser, job)

      appInitialiser.initialised.futureValue mustBe Done
      verify(job).start()
    }

    "must fail construction without starting the work-item job when internal-auth initialisation fails" in {
      val exception   = new RuntimeException("Internal-auth initialisation failed")
      val initialiser = internalAuthTokenInitialiser(Future.failed(exception))
      val job         = mock[MonthlyReturnWorkItemJob]

      val thrown = intercept[RuntimeException] {
        new AppInitialiser(initialiser, job)
      }

      thrown mustBe exception
      verify(job, never).start()
    }
  }

  private def internalAuthTokenInitialiser(result: Future[Done]): InternalAuthTokenInitialiser =
    new InternalAuthTokenInitialiser {
      override protected def initialise(): Future[Done] = result
    }
}
