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

import base.TestConstants
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import org.mongodb.scala.ObservableFuture
import org.mongodb.scala.model.Filters
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSClient
import play.api.test.DefaultAwaitTimeout
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturnsbackend.utils.RequestUtils
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.play.audit.http.connector.DatastreamMetrics

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

trait BaseIntegrationSpec
    extends AnyWordSpec
    with Matchers
    with GuiceOneServerPerSuite
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with DefaultAwaitTimeout
    with ScalaFutures
    with IntegrationPatience
    with TestConstants
    with RequestUtils {

  override lazy val app: Application = new GuiceApplicationBuilder()
    .configure(config)
    .overrides(bind[DatastreamMetrics].toInstance(DatastreamMetrics.disabled))
    .build()

  def config: Map[String, Any] =
    Map(
      "auditing.enabled"                    -> false,
      "create-internal-auth-token-on-start" -> false,
      "mongodb.uri"                         -> "mongodb://localhost:27017/disa-returns-backend-it"
    )

  protected def inject[T: ClassTag]: T =
    app.injector.instanceOf[T]

  implicit val ws: WSClient                       = inject[WSClient]
  implicit val executionContext: ExecutionContext = inject[ExecutionContext]

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    clearMongoCollections()
  }

  def serviceUrl(path: String): String = s"http://localhost:$port$path"

  def clearMongoCollections(): Unit = {
    await(
      inject[MongoComponent]
        .database
        .getCollection(monthlyReturnsCollectionName)
        .deleteMany(Filters.empty())
        .toFuture()
    )
  }
}
