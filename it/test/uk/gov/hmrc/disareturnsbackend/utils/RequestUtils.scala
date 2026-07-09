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

package uk.gov.hmrc.disareturnsbackend.utils

import play.api.http.HeaderNames.{AUTHORIZATION, CONTENT_TYPE}
import play.api.libs.json.JsValue
import play.api.libs.ws.{WSClient, WSRequest, WSResponse, writeableOf_JsValue, writeableOf_String}
import play.api.test.Helpers.await
import play.api.test.{DefaultAwaitTimeout, Helpers}

trait RequestUtils extends DefaultAwaitTimeout {

  protected def ws: WSClient

  protected def serviceUrl(path: String): String

  protected def defaultAuthorizationHeader: Option[String] = None

  private def request(path: String, authorizationHeader: Option[String] = defaultAuthorizationHeader): WSRequest = {
    val wsRequest = ws.url(serviceUrl(path))

    authorizationHeader.fold(wsRequest) { authorization =>
      wsRequest.withHttpHeaders(AUTHORIZATION -> authorization)
    }
  }

  protected def get(path: String): WSResponse =
    await(
      request(path)
        .get()
    )

  protected def delete(path: String): WSResponse =
    await(
      request(path)
        .delete()
    )

  protected def postJson(path: String, body: JsValue): WSResponse =
    await(
      request(path)
        .post(body)
    )

  protected def putString(path: String, body: String = ""): WSResponse =
    await(
      request(path)
        .put(body)
    )

  protected def putJson(path: String, body: JsValue): WSResponse =
    await(
      request(path)
        .put(body)
    )

  protected def postString(path: String, body: String, contentType: String): WSResponse =
    await(
      request(path)
        .withHttpHeaders(CONTENT_TYPE -> contentType)
        .post(body)
    )

  protected def getWithoutAuthorization(path: String): WSResponse =
    await(request(path, None).get())

  protected def deleteWithoutAuthorization(path: String): WSResponse =
    await(request(path, None).delete())

  protected def postJsonWithoutAuthorization(path: String, body: JsValue): WSResponse =
    await(request(path, None).post(body))

  protected def putJsonWithoutAuthorization(path: String, body: JsValue): WSResponse =
    await(request(path, None).put(body))

  protected def getWithAuthorization(path: String, authorizationHeader: String): WSResponse =
    await(request(path, Some(authorizationHeader)).get())

  protected def deleteWithAuthorization(path: String, authorizationHeader: String): WSResponse =
    await(request(path, Some(authorizationHeader)).delete())

  protected def postJsonWithAuthorization(path: String, body: JsValue, authorizationHeader: String): WSResponse =
    await(request(path, Some(authorizationHeader)).post(body))

  protected def putJsonWithAuthorization(path: String, body: JsValue, authorizationHeader: String): WSResponse =
    await(request(path, Some(authorizationHeader)).put(body))
}
