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

import java.time.{Clock, Instant, LocalDate, ZoneId, ZoneOffset}
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Singleton

@Singleton
class MutableClock extends Clock {

  private val utcZone: ZoneId = ZoneOffset.UTC
  private val currentClock    = new AtomicReference[Clock](Clock.systemUTC())

  def setDate(date: LocalDate): Unit =
    currentClock.set(Clock.fixed(date.atStartOfDay(utcZone).toInstant, utcZone))

  def reset(): Unit =
    currentClock.set(Clock.systemUTC())

  override def getZone: ZoneId = utcZone

  override def withZone(zone: ZoneId): Clock = currentClock.get().withZone(zone)

  override def instant(): Instant = currentClock.get().instant()
}
