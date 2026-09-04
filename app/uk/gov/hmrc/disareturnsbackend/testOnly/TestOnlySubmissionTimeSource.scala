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

import uk.gov.hmrc.disareturnsbackend.services.{SystemClock, TimeSource}
import uk.gov.hmrc.disareturnsbackend.testOnly.connectors.TestOnlyReturnsSubmissionConnector
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{Instant, ZoneOffset}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TestOnlySubmissionTimeSource @Inject() (
  connector: TestOnlyReturnsSubmissionConnector,
  systemClock: SystemClock
)(implicit ec: ExecutionContext)
    extends TimeSource {

  override def instant(zReference: String)(implicit hc: HeaderCarrier): Future[Instant] =
    connector.getOverrides(zReference).flatMap {
      _.clock match {
        case Some(clockOverride) => Future.successful(clockOverride.date.atStartOfDay(ZoneOffset.UTC).toInstant)
        case None                => systemClock.instant(zReference)
      }
    }
}
