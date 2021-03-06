/*
 * Copyright (C) 2017-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@dataswift.io> 2, 2017
 */

package com.hubofallthings.dataplugTwitter.controllers

import com.hubofallthings.dataplug.actors.IoExecutionContext
import com.hubofallthings.dataplug.apiInterfaces.models.DataPlugNotableShareRequest
import com.hubofallthings.dataplug.services.{ DataPlugEndpointService, DataPlugNotablesService, DataplugSyncerActorManager }
import com.hubofallthings.dataplug.utils.{ JwtPhataAuthenticatedAction, JwtPhataAwareAction }
import com.hubofallthings.dataplugTwitter.apiInterfaces.TwitterStatusUpdateInterface
import javax.inject.Inject
import org.joda.time.DateTime
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc._
import play.api.{ Configuration, Logger }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Api @Inject() (
    components: ControllerComponents,
    configuration: Configuration,
    tokenUserAwareAction: JwtPhataAwareAction,
    tokenUserAuthenticatedAction: JwtPhataAuthenticatedAction,
    dataPlugEndpointService: DataPlugEndpointService,
    dataPlugNotablesService: DataPlugNotablesService,
    twitterStatusUpdateInterface: TwitterStatusUpdateInterface,
    syncerActorManager: DataplugSyncerActorManager) extends AbstractController(components) {

  val logger = Logger(this.getClass)

  val ioEC = IoExecutionContext.ioThreadPool

  def create: Action[DataPlugNotableShareRequest] = Action.async(parse.json[DataPlugNotableShareRequest]) { implicit request =>
    request.headers.get("x-auth-token") map { secret =>
      val configuredSecret = configuration.getOptional[String]("service.notables.secret").getOrElse("")

      if (secret == configuredSecret) {
        val notableShareRequest = request.body

        dataPlugNotablesService.find(notableShareRequest.notableId) flatMap { maybeNotableStatus =>
          if (!maybeNotableStatus.exists(_.posted)) {
            val sharedNotable = maybeNotableStatus.getOrElse(notableShareRequest.dataPlugSharedNotable)
            for {
              statusUpdate <- twitterStatusUpdateInterface.post(notableShareRequest.hatDomain, notableShareRequest)
              notableStatus <- dataPlugNotablesService.save(sharedNotable.copy(posted = true, postedTime = Some(DateTime.now()), providerId = Some(statusUpdate.id_str)))
              mns <- dataPlugNotablesService.find(notableShareRequest.notableId)
            } yield {
              logger.info(s"Found inserted notable: $mns")
              Ok(Json.toJson(Map("message" -> "Notable accepted for posting")))
            }
          }
          else {
            Future.successful(BadRequest(generateResponseJson("Bad Request", "Notable already exists")))
          }
        }
      }
      else {
        Future.successful(Unauthorized(generateResponseJson("Unauthorized", "Authentication failed")))
      }
    } getOrElse {
      Future.successful(Unauthorized(generateResponseJson("Unauthorized", "Authentication token missing or malformed")))
    }
  }

  def delete(id: String): Action[AnyContent] = Action.async { implicit request =>
    request.headers.get("X-Auth-Token") map { secret =>
      val configuredSecret = configuration.getOptional[String]("service.notables.secret").getOrElse("")

      if (secret == configuredSecret) {
        dataPlugNotablesService.find(id) flatMap {
          case Some(status) =>
            if (status.posted && !status.deleted && status.providerId.isDefined) {
              for {
                _ <- twitterStatusUpdateInterface.delete(status.phata, status.providerId.get)
                maybeNotableStatus <- dataPlugNotablesService.save(status.copy(posted = false, deleted = true, deletedTime = Some(DateTime.now())))
              } yield {
                Ok(Json.toJson(Map("message" -> "Notable deleted.")))
              }
            }
            else if (status.posted && status.deleted) {
              Future.successful(BadRequest(generateResponseJson("Bad request", "Already deleted")))
            }
            else {
              Future.successful(BadRequest(generateResponseJson("Bad request", "Could not complete requested action")))
            }
          case None =>
            Future.successful(BadRequest(generateResponseJson("Bad request", "Notable not found")))
        }
      }
      else {
        Future.successful(Unauthorized(generateResponseJson("Unauthorized", "Authentication failed")))
      }
    } getOrElse {
      Future.successful(Unauthorized(generateResponseJson("Unauthorized", "Authentication token missing or malformed")))
    }
  }

  private def generateResponseJson(message: String, error: String): JsValue =
    Json.toJson(Map(
      "message" -> message,
      "error" -> error))
}