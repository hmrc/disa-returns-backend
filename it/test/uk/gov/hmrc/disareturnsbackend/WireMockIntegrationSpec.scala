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

package uk.gov.hmrc.disareturnsbackend

import org.scalatest.concurrent.Eventually
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.play.audit.http.connector.DatastreamMetrics

import java.time.Clock

trait WireMockIntegrationSpec extends BaseIntegrationSpec with Eventually {

  override lazy val app: Application = new GuiceApplicationBuilder()
    .configure(config)
    .overrides(
      bind[Clock].toInstance(Clock.systemUTC()),
      bind[DatastreamMetrics].toInstance(DatastreamMetrics.disabled)
    )
    .build()

  override def config: Map[String, Any] =
    super.config ++ Map(
      "microservice.services.object-store.protocol" -> "http",
      "microservice.services.object-store.host"     -> "localhost",
      "microservice.services.object-store.port"     -> wireMockPort,
      "fileUploadMaxInlineErrors"                   -> 2,
      "monthly-return-file-upload-work-item-job.pollInterval" -> "100 milliseconds",
      "monthly-return-file-upload-work-item-job.inProgressRetryAfter" -> "1 second"
    )

}
