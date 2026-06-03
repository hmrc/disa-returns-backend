/*
 * Copyright 2025 HM Revenue & Customs
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

import config.{
  InternalAuthTokenInitialiser,
  InternalAuthTokenInitialiserImpl,
  NoOpInternalAuthTokenInitialiser
}
import play.api.{Configuration, Environment}
import play.api.inject.{Binding, Module as AppModule, bind as binding}

import java.time.{Clock, ZoneOffset}

class Module extends AppModule:

  override def bindings(
      environment: Environment,
      configuration: Configuration
  ): Seq[Binding[_]] = {

    val authTokenInitialiserBindings: Seq[Binding[?]] =
      if (configuration.get[Boolean]("create-internal-auth-token-on-start")) {
        Seq(
          binding[InternalAuthTokenInitialiser]
            .to[InternalAuthTokenInitialiserImpl]
        )
      } else {
        Seq(
          binding[InternalAuthTokenInitialiser]
            .to[NoOpInternalAuthTokenInitialiser]
        )
      }

    Seq(
      binding[Clock].to(Clock.systemDefaultZone.withZone(ZoneOffset.UTC)),
      binding[AppInitialiser].toSelf.eagerly()
    ) ++ authTokenInitialiserBindings
  }
