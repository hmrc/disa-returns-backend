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

package uk.gov.hmrc.disareturnsbackend.controllers

import play.api.http.HeaderNames.LOCATION
import play.api.Logging
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.disareturnsbackend.controllers.actions.{MonthlyReturnNotDeclaredAction, ValidatedMonthlyReturnAction}
import uk.gov.hmrc.disareturnsbackend.models.*
import uk.gov.hmrc.disareturnsbackend.services.*
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.bootstrap.controller.WithJsonBody
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

class MonthlyReturnController @Inject() (
  cc: ControllerComponents,
  monthlyReturnService: MonthlyReturnService,
  validatedMonthlyReturnAction: ValidatedMonthlyReturnAction,
  monthlyReturnNotDeclaredAction: MonthlyReturnNotDeclaredAction
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with WithJsonBody
    with Logging {

  def getMonthlyReturn(zReference: String, taxYear: String, month: String): Action[AnyContent] =
    validatedMonthlyReturnAction(zReference, taxYear, month).async { implicit request =>
      logger.info(
        s"[MonthlyReturnController][getMonthlyReturn] Get monthly return request for zReference [$zReference], taxYear [$taxYear], month [$month]"
      )

      implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

      monthlyReturnService
        .getWithDeclaration(request.zReference, request.taxYear, request.month)
        .map {
          case Some(monthlyReturn) => Ok(Json.toJson(monthlyReturn))
          case None                => NotFound
        }
        .recover { case NonFatal(_) => ServiceUnavailable }
    }

  def createMonthlyReturn(zReference: String, taxYear: String, month: String): Action[JsValue] =
    (validatedMonthlyReturnAction(zReference, taxYear, month) andThen monthlyReturnNotDeclaredAction(Conflict))
      .async(parse.json) { implicit request =>
        withJsonBody[CreateMonthlyReturnRequest] { createRequest =>
          implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

          logger.info(
            s"[MonthlyReturnController][createMonthlyReturn] Create monthly return request for zReference [${request.zReference}], taxYear [${request.taxYear}], month [${request.month}], nilReturn [${createRequest.nilReturn}]"
          )

          monthlyReturnService
            .create(request.zReference, request.taxYear, request.month, createRequest.nilReturn)
            .map {
              case CreateMonthlyReturnResult.Created(submissionId)    =>
                Created(Json.toJson(CreateMonthlyReturnResponse(submissionId))).withHeaders(LOCATION -> request.path)
              case CreateMonthlyReturnResult.AlreadyExists            => Conflict
              case CreateMonthlyReturnResult.OutsideDeclarationPeriod => UnprocessableEntity
            }
            .recover { case NonFatal(_) => ServiceUnavailable }
        }
      }

  def updateNilReturn(zReference: String, taxYear: String, month: String): Action[JsValue] =
    (validatedMonthlyReturnAction(zReference, taxYear, month) andThen monthlyReturnNotDeclaredAction(
      UnprocessableEntity
    ))
      .async(parse.json) { implicit request =>
        withJsonBody[UpdateNilReturnRequest] { updateRequest =>
          logger.info(
            s"[MonthlyReturnController][updateNilReturn] Update nilReturn request for zReference [${request.zReference}], taxYear [${request.taxYear}], month [${request.month}], value [${updateRequest.value}]"
          )

          monthlyReturnService
            .updateNilReturn(request.zReference, request.taxYear, request.month, updateRequest.value)
            .map {
              case UpdateNilReturnResult.NilReturnUpdated(monthlyReturn) => Ok(Json.toJson(monthlyReturn))
              case UpdateNilReturnResult.MonthlyReturnNotFound           => NotFound
            }
            .recover { case NonFatal(_) => ServiceUnavailable }
        }
      }

  def declareMonthlyReturn(zReference: String, taxYear: String, month: String): Action[AnyContent] =
    (validatedMonthlyReturnAction(zReference, taxYear, month) andThen monthlyReturnNotDeclaredAction(Conflict)).async {
      implicit request =>
        logger.info(
          s"[MonthlyReturnController][declareMonthlyReturn] Declare monthly return request for zReference [$zReference], taxYear [$taxYear], month [$month]"
        )

        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        monthlyReturnService
          .declare(request.zReference, request.taxYear, request.month)
          .map {
            case DeclareMonthlyReturnResult.Declared                 => NoContent
            case DeclareMonthlyReturnResult.AlreadyDeclared          => Conflict
            case DeclareMonthlyReturnResult.MonthlyReturnNotFound    => NotFound
            case DeclareMonthlyReturnResult.OutsideDeclarationPeriod => UnprocessableEntity
          }
          .recover { case NonFatal(_) => ServiceUnavailable }
    }

  def createFileUpload(zReference: String, taxYear: String, month: String): Action[JsValue] =
    (validatedMonthlyReturnAction(zReference, taxYear, month) andThen monthlyReturnNotDeclaredAction(
      UnprocessableEntity
    ))
      .async(parse.json) { implicit request =>
        withJsonBody[CreateFileUploadRequest] { createRequest =>
          logger.info(
            s"[MonthlyReturnController][createFileUpload] Create file upload request for zReference [${request.zReference}], taxYear [${request.taxYear}], month [${request.month}], upload reference [${createRequest.reference}]"
          )

          monthlyReturnService
            .createFileUpload(request.zReference, request.taxYear, request.month, createRequest.reference)
            .map {
              case CreateFileUploadResult.FileUploadCreated(_)    =>
                Created.withHeaders(LOCATION -> s"${request.path}/${createRequest.reference}")
              case CreateFileUploadResult.FileUploadAlreadyExists => Conflict
              case CreateFileUploadResult.MonthlyReturnNotFound   => NotFound
            }
            .recover { case NonFatal(_) => ServiceUnavailable }
        }
      }

  def getFileUpload(zReference: String, taxYear: String, month: String, reference: String): Action[AnyContent] =
    validatedMonthlyReturnAction(zReference, taxYear, month).async { implicit request =>
      logger.info(
        s"[MonthlyReturnController][getFileUpload] Get file upload request for zReference [$zReference], taxYear [$taxYear], month [$month], upload reference [$reference]"
      )

      monthlyReturnService
        .getFileUpload(request.zReference, request.taxYear, request.month, reference)
        .map {
          case Some(fileUpload) => Ok(Json.toJson(fileUpload))
          case None             => NotFound
        }
        .recover { case NonFatal(_) => ServiceUnavailable }
    }

  def deleteFileUpload(zReference: String, taxYear: String, month: String, reference: String): Action[AnyContent] =
    validatedMonthlyReturnAction(zReference, taxYear, month).async { implicit request =>
      logger.info(
        s"[MonthlyReturnController][deleteFileUpload] Delete file upload request for zReference [$zReference], taxYear [$taxYear], month [$month], upload reference [$reference]"
      )

      monthlyReturnService
        .deleteFileUpload(request.zReference, request.taxYear, request.month, reference)
        .map {
          case true  => NoContent
          case false => NotFound
        }
        .recover { case NonFatal(_) => ServiceUnavailable }
    }
}
