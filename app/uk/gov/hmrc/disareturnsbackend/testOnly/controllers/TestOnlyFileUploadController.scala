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

package uk.gov.hmrc.disareturnsbackend.testOnly.controllers

import play.api.libs.json.Json
import play.api.Environment
import play.api.http.Status.FORBIDDEN
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.disareturnsbackend.utils.Constants.XLSX_MIME_TYPE
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.util.Using

@Singleton
class TestOnlyFileUploadController @Inject() (cc: ControllerComponents, environment: Environment)
    extends BackendController(cc) {

  private val baseResourcePath = "test-only/file-upload/monthly"
  private val filenameRegex    = "^[A-Za-z0-9-]+\\.(csv|xlsx)$".r

  def getMonthlyFile(filename: String): Action[AnyContent] = Action {
    filename match {
      case filenameRegex(_) =>
        val resourcePath = s"$baseResourcePath/$filename"

        environment.resourceAsStream(resourcePath) match {
          case Some(stream) =>
            val bytes = Using.resource(stream)(_.readAllBytes())
            Ok(bytes)
              .as(contentType(filename))
              .withHeaders(CONTENT_DISPOSITION -> s"inline; filename=$filename")

          case None => NotFound(Json.obj("message" -> s"File [$filename] not found"))
        }

      case _ => BadRequest(Json.obj("message" -> "Invalid file name"))
    }
  }

  def getExpiredUpscanDownload(): Action[AnyContent] = Action {
    Status(FORBIDDEN)(
      """<?xml version="1.0" encoding="UTF-8"?>
        |<Error><Code>AccessDenied</Code><Message>Request has expired</Message><X-Amz-Expires>21600</X-Amz-Expires></Error>""".stripMargin
    ).as("application/xml")
  }

  private def contentType(filename: String): String =
    if (filename.endsWith(".xlsx")) XLSX_MIME_TYPE else "text/csv"
}
