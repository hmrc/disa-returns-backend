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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import java.nio.file.Files

class ProcessResidentMemorySamplerSpec extends AnyWordSpec with Matchers:

  "parseVmRssBytes" should:
    "parse VmRSS with tabs" in:
      ProcessResidentMemorySampler.parseVmRssBytes(
        "Name:\ttest\nVmRSS:\t 123456 kB\n"
      ) shouldBe Some(123456L * 1024L)

    "parse VmRSS with multiple spaces" in:
      ProcessResidentMemorySampler.parseVmRssBytes(
        "Name:\ttest\nVmRSS:      123456 kB\n"
      ) shouldBe Some(123456L * 1024L)

    "parse VmRSS with a single space" in:
      ProcessResidentMemorySampler.parseVmRssBytes(
        "Name:\ttest\nVmRSS: 123456 kB\n"
      ) shouldBe Some(123456L * 1024L)

    "return none when VmRSS is missing" in:
      ProcessResidentMemorySampler.parseVmRssBytes(
        "Name:\ttest\nVmSize:\t    123456 kB\n"
      ) shouldBe None

    "return none when VmRSS is malformed" in:
      ProcessResidentMemorySampler.parseVmRssBytes(
        "Name:\ttest\nVmRSS:\t    not-a-number kB\n"
      ) shouldBe None

    "return none when VmRSS unit is unsupported" in:
      ProcessResidentMemorySampler.parseVmRssBytes(
        "Name:\ttest\nVmRSS: 123456 KB\n"
      ) shouldBe None

  "parsePsRssBytes" should:
    "parse numeric ps output" in:
      ProcessResidentMemorySampler.parsePsRssBytes("123456") shouldBe Some(123456L * 1024L)

    "parse whitespace padded ps output" in:
      ProcessResidentMemorySampler.parsePsRssBytes("  123456\n") shouldBe Some(123456L * 1024L)

    "return none for empty output" in:
      ProcessResidentMemorySampler.parsePsRssBytes("") shouldBe None

    "return none for malformed output" in:
      ProcessResidentMemorySampler.parsePsRssBytes("not-a-number") shouldBe None

  "currentProcessResidentMemoryBytes" should:
    "read VmRSS from a status file fixture" in:
      val statusFile = Files.createTempFile("status", ".txt")
      Files.writeString(
        statusFile,
        "Name:\ttest\nVmRSS:\t 123456 kB\n",
        StandardCharsets.UTF_8
      )

      try {
        val sampler = new DefaultProcessResidentMemorySampler {
          override protected def osName = "Linux"
          override protected def statusPath = statusFile
        }

        sampler.currentProcessResidentMemoryBytes() shouldBe Some(123456L * 1024L)
      } finally {
        Files.deleteIfExists(statusFile)
      }

    "read rss from ps on macOS" in:
      val runner = new FakeProcessCommandRunner(Some("123456\n"))
      val sampler = new DefaultProcessResidentMemorySampler {
        override protected def osName = "Mac OS X"
        override protected def currentPid = 4242L
        override protected def processCommandRunner = runner
      }

      sampler.currentProcessResidentMemoryBytes() shouldBe Some(123456L * 1024L)
      runner.commands shouldBe Vector(Vector("ps", "-o", "rss=", "-p", "4242"))
      runner.timeouts shouldBe Vector(1000L)

    "return none when ps fails on macOS" in:
      val runner = new FakeProcessCommandRunner(None)
      val sampler = new DefaultProcessResidentMemorySampler {
        override protected def osName = "Mac OS X"
        override protected def currentPid = 4242L
        override protected def processCommandRunner = runner
      }

      sampler.currentProcessResidentMemoryBytes() shouldBe None

  private final class FakeProcessCommandRunner(response: Option[String]) extends ProcessCommandRunner:
    var commands: Vector[Seq[String]] = Vector.empty
    var timeouts: Vector[Long] = Vector.empty

    override def run(command: Seq[String], timeoutMillis: Long): Option[String] =
      commands = commands :+ command
      timeouts = timeouts :+ timeoutMillis
      response
