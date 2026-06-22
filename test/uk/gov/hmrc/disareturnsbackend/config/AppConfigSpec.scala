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
  }

  private def appConfig(fileUploadMaxInlineErrors: Option[Int]): AppConfig = {
    val servicesConfig = mock[ServicesConfig]
    when(servicesConfig.baseUrl("internal-auth")).thenReturn("http://internal-auth")
    when(servicesConfig.baseUrl("disa-returns-submission")).thenReturn("http://disa-returns-submission")

    val configValues = Map[String, Any](
      "appName"                                                       -> "disa-returns-backend",
      "declarationPeriodEnd"                                          -> 19,
      "declarationPeriodStart"                                        -> 6,
      "internal-auth.token"                                           -> "valid-internal-auth-token-disa-returns-backend",
      "mongodb.monthlyReturnTimeToLiveInDays"                         -> 20L,
      "monthly-return-file-upload-work-item-job.pollInterval"         -> "10 seconds",
      "monthly-return-file-upload-work-item-job.inProgressRetryAfter" -> "5 minutes"
    ) ++ fileUploadMaxInlineErrors.map("fileUploadMaxInlineErrors" -> _)

    new AppConfig(Configuration.from(configValues), servicesConfig)
  }
}
