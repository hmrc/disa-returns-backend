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

package uk.gov.hmrc.disareturnsbackend.testOnly

import base.SpecBase
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{verify, when}
import uk.gov.hmrc.disareturnsbackend.services.SystemClock
import uk.gov.hmrc.disareturnsbackend.testOnly.connectors.TestOnlyReturnsSubmissionConnector
import uk.gov.hmrc.disareturnsbackend.testOnly.models.{ClockOverride, TestOverride}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{Instant, LocalDate}
import scala.concurrent.Future

class TestOnlySubmissionTimeSourceSpec extends SpecBase {
  private val connector                  = mock[TestOnlyReturnsSubmissionConnector]
  private val systemClock                = mock[SystemClock]
  private val timeSource                 = new TestOnlySubmissionTimeSource(connector, systemClock)
  private implicit val hc: HeaderCarrier = HeaderCarrier()

  "TestOnlySubmissionTimeSource" - {
    "must use submission's clock override date at midnight UTC" in {
      val instant = Instant.parse("2026-09-17T00:00:00Z")
      when(connector.getOverrides(eqTo(testZReference))(any()))
        .thenReturn(
          Future.successful(TestOverride(testZReference, Some(ClockOverride(LocalDate.parse("2026-09-17"))), None))
        )

      timeSource.instant(testZReference).futureValue mustBe instant
      verify(connector).getOverrides(eqTo(testZReference))(eqTo(hc))
    }

    "must fall back to the backend system clock when submission has no clock override" in {
      val instant = Instant.parse("2026-09-17T12:34:56Z")
      when(connector.getOverrides(eqTo(testZReference))(any()))
        .thenReturn(Future.successful(TestOverride(testZReference, None, None)))
      when(systemClock.instant(eqTo(testZReference))(any())).thenReturn(Future.successful(instant))

      timeSource.instant(testZReference).futureValue mustBe instant
      verify(systemClock).instant(eqTo(testZReference))(eqTo(hc))
    }
  }
}
