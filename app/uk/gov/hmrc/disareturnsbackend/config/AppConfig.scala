/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.disareturnsbackend.config

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import java.nio.file.{Path, Paths}
import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId}
import scala.concurrent.duration.{Duration, DurationInt}

@Singleton
class AppConfig @Inject() (config: Configuration)
    extends ServicesConfig(config) {

  val appName: String = config.get[String]("appName")

  lazy val disaReturnsStubs: String =
    baseUrl("disa-returns-stubs")

  lazy val objectStoreUrl: String = s"$disaReturnsStubs/object-store"

  lazy val upscanDownloadUrl: String = s"$disaReturnsStubs/upscan-download"

  lazy val csvProcessingJobRequestTimeout: Duration =
    config
      .getOptional[Duration]("csvProcessingJob.requestTimeout")
      .getOrElse(30.minutes)

  lazy val csvProcessingJobReportFile: Path =
    Paths.get(
      config
        .getOptional[String]("csvProcessingJob.reportFile")
        .getOrElse {
          val timestamp =
            LocalDateTime
              .now(ZoneId.systemDefault())
              .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))

          s"target/$appName-csv-processing-job-report-$timestamp.csv"
        }
    )

  lazy val xlsxProcessingJobReportFile: Path =
    Paths.get(
      config
        .getOptional[String]("xlsxProcessingJob.reportFile")
        .getOrElse {
          val timestamp =
            LocalDateTime
              .now(ZoneId.systemDefault())
              .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))

          s"target/$appName-xlsx-processing-job-report-$timestamp.csv"
        }
    )
}
