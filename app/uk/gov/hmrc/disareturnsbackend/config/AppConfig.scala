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

import javax.inject.*
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

  val fileUploadMaxInlineErrors: Int = config.getOptional[Int]("fileUploadMaxInlineErrors").getOrElse(25)

  val internalAuthService: String = servicesConfig.baseUrl("internal-auth")
  val internalAuthToken: String   = config.get[String]("internal-auth.token")

  val returnsSubmissionService: String = servicesConfig.baseUrl("disa-returns-submission")

  val monthlyReturnFileUploadJobEnabled: Boolean = config
    .getOptional[Boolean]("monthly-return-file-upload-work-item-job.enabled")
    .getOrElse(true)

  val monthlyReturnFileUploadJobInProgressRetryAfter: Duration = config
    .getOptional[Duration]("monthly-return-file-upload-work-item-job.inProgressRetryAfter")
    .getOrElse(Duration.ofMinutes(5))

  val monthlyReturnFileUploadJobFailedRetryAfter: Duration = config
    .getOptional[Duration]("monthly-return-file-upload-work-item-job.failedRetryAfter")
    .getOrElse(Duration.ofMinutes(1))

  val monthlyReturnFileUploadJobPollInterval: FiniteDuration = config
    .getOptional[Duration]("monthly-return-file-upload-work-item-job.pollInterval")
    .getOrElse(Duration.ofSeconds(10))
    .toScala

  val monthlyReturnSubmissionJobEnabled: Boolean = config
    .getOptional[Boolean]("monthly-return-submission-work-item-job.enabled")
    .getOrElse(true)

  val monthlyReturnSubmissionJobFailedRetryAfter: Duration = config
    .getOptional[Duration]("monthly-return-submission-work-item-job.failedRetryAfter")
    .getOrElse(Duration.ofMinutes(5))

  val monthlyReturnSubmissionJobInProgressRetryAfter: Duration = config
    .getOptional[Duration]("monthly-return-submission-work-item-job.inProgressRetryAfter")
    .getOrElse(Duration.ofMinutes(5))

  val monthlyReturnSubmissionJobPollInterval: FiniteDuration = config
    .getOptional[Duration]("monthly-return-submission-work-item-job.pollInterval")
    .getOrElse(Duration.ofSeconds(10))
    .toScala

  val monthlyReturnSubmissionJobWorkerCount: Int = config
    .getOptional[Int]("monthly-return-submission-work-item-job.workerCount")
    .getOrElse(2)

  val monthlyReturnSubmissionEnqueueAttempts: Int = config
    .getOptional[Int]("monthly-return-submission-work-item-job.submissionTransferEnqueueAttempts")
    .getOrElse(3)
  require(
    monthlyReturnSubmissionEnqueueAttempts > 0,
    "Monthly return submissionTransferEnqueueAttempts must be positive"
  )

  val monthlyReturnTimeToLiveInDays: Long = config.get[Long]("mongodb.monthlyReturnTimeToLiveInDays")

}
