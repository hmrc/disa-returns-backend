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

import com.google.inject.ImplementedBy

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import javax.inject.Singleton
import scala.util.Try

@ImplementedBy(classOf[DefaultProcessResidentMemorySampler])
trait ProcessResidentMemorySampler:
  def currentProcessResidentMemoryBytes(): Option[Long]

object ProcessResidentMemorySampler:
  def parseVmRssBytes(statusText: String): Option[Long] =
    statusText.linesIterator
      .map(_.trim)
      .collectFirst {
        case line if line.startsWith("VmRSS:") =>
          line
            .stripPrefix("VmRSS:")
            .trim
            .split("\\s+")
            .headOption
            .flatMap(_.toLongOption)
            .map(_ * 1024L)
      }
      .flatten

@Singleton
class DefaultProcessResidentMemorySampler extends ProcessResidentMemorySampler:
  private val statusPath: Path = Path.of("/proc/self/status")

  override def currentProcessResidentMemoryBytes(): Option[Long] =
    Try(Files.readString(statusPath, StandardCharsets.UTF_8)).toOption.flatMap(
      ProcessResidentMemorySampler.parseVmRssBytes
    )
