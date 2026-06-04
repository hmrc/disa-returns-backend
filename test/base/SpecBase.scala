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

package base

import org.scalatest.{OptionValues, TryValues}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import uk.gov.hmrc.play.audit.http.connector.DatastreamMetrics
import uk.gov.hmrc.disareturnsbackend.config.{InternalAuthTokenInitialiser, NoOpInternalAuthTokenInitialiser}

import scala.concurrent.ExecutionContext

trait SpecBase
    extends AnyFreeSpec
    with Matchers
    with TryValues
    with OptionValues
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneAppPerSuite
    with MockitoSugar {

  implicit val ec: ExecutionContext =
    scala.concurrent.ExecutionContext.Implicits.global

  override lazy val app: Application = applicationBuilder().build()

  protected def applicationBuilder(
    additionalOverrides: Seq[GuiceableModule] = Nil
  ): GuiceApplicationBuilder = {

    val defaultOverrides: Seq[GuiceableModule] = Seq(
      bind[InternalAuthTokenInitialiser]
        .to[NoOpInternalAuthTokenInitialiser],
      bind[DatastreamMetrics]
        .toInstance(DatastreamMetrics.disabled)
    )

    val builder = new GuiceApplicationBuilder()
      .configure("create-internal-auth-token-on-start" -> false)
      .overrides(defaultOverrides ++ additionalOverrides: _*)

    builder
  }
}
