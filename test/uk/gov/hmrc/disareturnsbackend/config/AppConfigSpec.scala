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

package uk.gov.hmrc.disareturnsbackend.config

import base.SpecBase
import org.mockito.Mockito.when
import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

class AppConfigSpec extends SpecBase {

  "AppConfig" - {

    "must read fileUploadMaxInlineErrors from config" in {
      appConfig(fileUploadMaxInlineErrors = Some(10)).fileUploadMaxInlineErrors mustBe 10
    }

    "must default fileUploadMaxInlineErrors to 25 when missing" in {
      appConfig(fileUploadMaxInlineErrors = None).fileUploadMaxInlineErrors mustBe 25
    }

    "must read the monthly return submission enqueue attempt limit" in {
      appConfig(None, enqueueAttempts = Some(2)).monthlyReturnSubmissionEnqueueAttempts mustBe 2
    }

    "must default to three monthly return submission enqueue attempts" in {
      appConfig(None).monthlyReturnSubmissionEnqueueAttempts mustBe 3
    }

    "must reject a non-positive enqueue attempt limit" in
      intercept[IllegalArgumentException](appConfig(None, enqueueAttempts = Some(0)))

    "must enable both monthly return jobs by default" in {
      val config = appConfig(None)
      config.monthlyReturnFileUploadJobEnabled mustBe true
      config.monthlyReturnSubmissionJobEnabled mustBe true
    }

    "must read each monthly return job's enabled flag independently" in {
      val config = appConfig(None, validationEnabled = Some(false), submissionEnabled = Some(true))
      config.monthlyReturnFileUploadJobEnabled mustBe false
      config.monthlyReturnSubmissionJobEnabled mustBe true

      val reversed = appConfig(None, validationEnabled = Some(true), submissionEnabled = Some(false))
      reversed.monthlyReturnFileUploadJobEnabled mustBe true
      reversed.monthlyReturnSubmissionJobEnabled mustBe false
    }
  }

  private def appConfig(
    fileUploadMaxInlineErrors: Option[Int],
    enqueueAttempts: Option[Int] = None,
    validationEnabled: Option[Boolean] = None,
    submissionEnabled: Option[Boolean] = None
  ): AppConfig = {
    val servicesConfig = mock[ServicesConfig]
    when(servicesConfig.baseUrl("internal-auth")).thenReturn("http://internal-auth")
    when(servicesConfig.baseUrl("disa-returns-submission")).thenReturn("http://disa-returns-submission")

    val configValues = Map[String, Any](
      "appName"                                                       -> "disa-returns-backend",
      "internal-auth.token"                                           -> "valid-internal-auth-token-disa-returns-backend",
      "mongodb.monthlyReturnTimeToLiveInDays"                         -> 20L,
      "monthly-return-file-upload-work-item-job.pollInterval"         -> "10 seconds",
      "monthly-return-file-upload-work-item-job.inProgressRetryAfter" -> "5 minutes"
    ) ++ fileUploadMaxInlineErrors.map("fileUploadMaxInlineErrors" -> _) ++
      enqueueAttempts.map("monthly-return-submission-work-item-job.submissionTransferEnqueueAttempts" -> _) ++
      validationEnabled.map("monthly-return-file-upload-work-item-job.enabled" -> _) ++
      submissionEnabled.map("monthly-return-submission-work-item-job.enabled" -> _)

    new AppConfig(Configuration.from(configValues), servicesConfig)
  }
}
