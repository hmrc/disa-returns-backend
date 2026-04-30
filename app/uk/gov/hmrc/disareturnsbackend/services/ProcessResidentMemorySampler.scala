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
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import scala.util.Try

@ImplementedBy(classOf[DefaultProcessResidentMemorySampler])
trait ProcessResidentMemorySampler:
  def currentProcessResidentMemoryBytes(): Option[Long]

trait ProcessCommandRunner:
  def run(command: Seq[String], timeoutMillis: Long): Option[String]

private final class DefaultProcessCommandRunner extends ProcessCommandRunner:
  override def run(command: Seq[String], timeoutMillis: Long): Option[String] =
    Try {
      val process = new ProcessBuilder(command: _*).redirectErrorStream(true).start()

      if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
        process.destroyForcibly()
        None
      } else {
        Some(new String(process.getInputStream.readAllBytes(), StandardCharsets.UTF_8))
      }
    }.toOption.flatten

object ProcessResidentMemorySampler:
  private val VmRssPattern = """^VmRSS:\s+(\d+)\s+kB$""".r

  def parseVmRssBytes(statusText: String): Option[Long] =
    statusText.linesIterator
      .map(_.trim)
      .collectFirst {
        case VmRssPattern(value) => value.toLong * 1024L
      }

  def parsePsRssBytes(output: String): Option[Long] =
    output.linesIterator
      .map(_.trim)
      .find(_.nonEmpty)
      .flatMap(_.toLongOption)
      .map(_ * 1024L)

@Singleton
class DefaultProcessResidentMemorySampler extends ProcessResidentMemorySampler:
  private val psTimeoutMillis = 1000L

  protected def statusPath: Path = Path.of("/proc/self/status")

  protected def processCommandRunner: ProcessCommandRunner = new DefaultProcessCommandRunner

  protected def osName: String = System.getProperty("os.name", "")

  protected def currentPid: Long = ProcessHandle.current().pid()

  override def currentProcessResidentMemoryBytes(): Option[Long] =
    if (isMacOs) {
      currentProcessResidentMemoryBytesFromPs
    } else {
      Try(Files.readString(statusPath, StandardCharsets.UTF_8)).toOption.flatMap(
        ProcessResidentMemorySampler.parseVmRssBytes
      )
    }

  private def currentProcessResidentMemoryBytesFromPs: Option[Long] =
    processCommandRunner.run(
      Seq("ps", "-o", "rss=", "-p", currentPid.toString),
      psTimeoutMillis
    ).flatMap(ProcessResidentMemorySampler.parsePsRssBytes)

  private def isMacOs: Boolean =
    osName.toLowerCase.contains("mac")
