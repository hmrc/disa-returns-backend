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

import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.libs.json.JsValue
import play.api.libs.ws.{WSClient, WSResponse, writeableOf_JsValue, writeableOf_String}
import play.api.test.Helpers.await
import play.api.test.{DefaultAwaitTimeout, Helpers}

trait RequestUtils extends DefaultAwaitTimeout {

  protected def ws: WSClient

  protected def serviceUrl(path: String): String

  protected def get(path: String): WSResponse =
    await(
      ws.url(serviceUrl(path))
        .get()
    )

  protected def delete(path: String): WSResponse =
    await(
      ws.url(serviceUrl(path))
        .delete()
    )

  protected def postJson(path: String, body: JsValue): WSResponse =
    await(
      ws.url(serviceUrl(path))
        .post(body)
    )

  protected def putString(path: String, body: String = ""): WSResponse =
    await(
      ws.url(serviceUrl(path))
        .put(body)
    )

  protected def putJson(path: String, body: JsValue): WSResponse =
    await(
      ws.url(serviceUrl(path))
        .put(body)
    )

  protected def postString(path: String, body: String, contentType: String): WSResponse =
    await(
      ws.url(serviceUrl(path))
        .withHttpHeaders(CONTENT_TYPE -> contentType)
        .post(body)
    )
}
