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
import com.github.tomakehurst.wiremock.client.WireMock.*
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import org.mongodb.scala.ObservableFuture
import org.mongodb.scala.model.Filters
import play.api.Application
import play.api.http.HeaderNames.{AUTHORIZATION, WWW_AUTHENTICATE}
import play.api.http.Status.{CREATED, OK, UNAUTHORIZED}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.WSClient
import play.api.test.DefaultAwaitTimeout
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturnsbackend.utils.RequestUtils
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.play.audit.http.connector.DatastreamMetrics

import java.time.{Clock, ZoneOffset}
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
    with WireMockSupport
    with TestConstants
    with RequestUtils {

  override lazy val app: Application = new GuiceApplicationBuilder()
    .configure(config)
    .overrides(
      bind[Clock].toInstance(Clock.fixed(testCreatedOn, ZoneOffset.UTC)),
      bind[DatastreamMetrics].toInstance(DatastreamMetrics.disabled)
    )
    .build()

  def config: Map[String, Any] =
    Map(
      "auditing.enabled"                    -> false,
      "create-internal-auth-token-on-start" -> false,
      "mongodb.uri"                         -> "mongodb://localhost:27017/disa-returns-backend-it",
      "microservice.services.auth.protocol" -> "http",
      "microservice.services.auth.host"     -> "localhost",
      "microservice.services.auth.port"     -> wireMockPort,
      "microservice.services.disa-returns-submission.protocol" -> "http",
      "microservice.services.disa-returns-submission.host"     -> "localhost",
      "microservice.services.disa-returns-submission.port"     -> wireMockPort
    )

  protected def inject[T: ClassTag]: T =
    app.injector.instanceOf[T]

  implicit val ws: WSClient                       = inject[WSClient]
  implicit val executionContext: ExecutionContext = inject[ExecutionContext]

  override protected def defaultAuthorizationHeader: Option[String] = Some(testBearerToken)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    stubAuth()
    stubReturnsSubmissionCreateMonthlyReturn()
    stubReturnsSubmissionDeclareMonthlyReturn()
    clearMongoCollections()
  }

  protected def stubReturnsSubmissionCreateMonthlyReturn(
    status: Int = CREATED,
    submissionId: String = testSubmissionId.toString
  ): Unit =
    stubFor(
      post(urlPathMatching("/disa-returns-submission/monthly/[^/]+/[^/]+/[^/]+"))
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(Json.obj(submissionIdFieldName -> submissionId).toString())
        )
    )

  protected def stubReturnsSubmissionGetMonthlyReturn(body: JsObject): Unit =
    stubFor(
      com.github.tomakehurst.wiremock.client.WireMock.get(
        urlPathMatching("/disa-returns-submission/monthly/[^/]+/[^/]+/[^/]+")
      )
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withHeader("Content-Type", "application/json")
            .withBody(body.toString())
        )
    )

  protected def stubReturnsSubmissionDeclareMonthlyReturn(status: Int = OK): Unit =
    stubFor(
      post(urlPathMatching("/disa-returns-submission/monthly/[^/]+/[^/]+/[^/]+/declarations"))
        .willReturn(aResponse().withStatus(status))
    )

  protected def stubAuth(zReference: String = testZReference, authorizationHeader: String = testBearerToken): Unit =
    stubFor(
      post(urlEqualTo("/auth/authorise"))
        .withHeader(AUTHORIZATION, equalTo(authorizationHeader))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withHeader("Content-Type", "application/json")
            .withBody(
              Json
                .obj(
                  "allEnrolments" -> Json.arr(
                    Json.obj(
                      "key" -> "HMRC-DISA-ORG",
                      "identifiers" -> Json.arr(
                        Json.obj(
                          "key"   -> "ZREF",
                          "value" -> zReference
                        )
                      ),
                      "state" -> "Activated"
                    )
                  )
                )
                .toString()
            )
        )
    )

  protected def stubInvalidBearerToken(): Unit =
    stubFor(
      post(urlEqualTo("/auth/authorise"))
        .withHeader(AUTHORIZATION, equalTo(invalidTestBearerToken))
        .willReturn(
          aResponse()
            .withStatus(UNAUTHORIZED)
            .withHeader(WWW_AUTHENTICATE, """MDTP detail="InvalidBearerToken"""")
        )
    )

  def serviceUrl(path: String): String = s"http://localhost:$port$path"

  protected val emptyJson: JsObject             = Json.obj()
  protected val nilReturnFalseRequest: JsObject = Json.obj(nilReturnFieldName -> false)

  def clearMongoCollections(): Unit = {
    val database = inject[MongoComponent].database

    Seq(monthlyReturnsCollectionName, monthlyReturnFileUploadWorkItemsCollectionName).foreach { collectionName =>
      await(
        database
          .getCollection(collectionName)
          .deleteMany(Filters.empty())
          .toFuture()
      )
    }
  }
}
