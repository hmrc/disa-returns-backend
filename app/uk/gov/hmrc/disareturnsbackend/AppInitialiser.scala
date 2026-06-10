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

import org.apache.pekko.Done
import play.api.Logging
import uk.gov.hmrc.disareturnsbackend.config.InternalAuthTokenInitialiser
import uk.gov.hmrc.disareturnsbackend.jobs.FileUploadWorkItemJob

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class AppInitialiser @Inject() (
  internalAuthTokenInitialiser: InternalAuthTokenInitialiser,
  fileUploadWorkItemJob: FileUploadWorkItemJob
)(implicit ec: ExecutionContext)
    extends Logging {

  val initialised: Future[Done] =
    internalAuthTokenInitialiser.initialised

  initialised.foreach { _ =>
    logger.info("[AppInitialiser] Internal auth initialiser completed")

    Try(fileUploadWorkItemJob.start()).failed.foreach { exception =>
      logger.error("[AppInitialiser] File upload work item job failed to start", exception)
    }
  }

  initialised.failed.foreach { exception =>
    logger.error(
      "[AppInitialiser] Internal auth initialiser failed",
      exception
    )
  }
}
