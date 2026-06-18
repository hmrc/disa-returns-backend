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

import java.time.Duration
import scala.concurrent.duration.FiniteDuration
import scala.jdk.DurationConverters.*

@Singleton
class AppConfig @Inject() (
  config: Configuration,
  servicesConfig: ServicesConfig
) {

  val appName: String = config.get[String]("appName")

  val declarationPeriodEnd: Int   = config.get[Int]("declarationPeriodEnd")
  val declarationPeriodStart: Int = config.get[Int]("declarationPeriodStart")

  val fileUploadMaxInlineErrors: Int = config.getOptional[Int]("fileUploadMaxInlineErrors").getOrElse(25)

  val internalAuthService: String = servicesConfig.baseUrl("internal-auth")
  val internalAuthToken: String   = config.get[String]("internal-auth.token")

  val returnsSubmissionService: String = servicesConfig.baseUrl("disa-returns-submission")

  val monthlyReturnFileUploadJobInProgressRetryAfter: Duration = config
    .getOptional[Duration]("monthly-return-file-upload-work-item-job.inProgressRetryAfter")
    .getOrElse(Duration.ofMinutes(5))

  val monthlyReturnFileUploadJobPollInterval: FiniteDuration = config
    .getOptional[Duration]("monthly-return-file-upload-work-item-job.pollInterval")
    .getOrElse(Duration.ofSeconds(10))
    .toScala

  val monthlyReturnTimeToLiveInDays: Long = config.get[Long]("mongodb.monthlyReturnTimeToLiveInDays")
}
