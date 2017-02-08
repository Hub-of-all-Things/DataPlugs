/*
 * Copyright (C) 2017 HAT Data Exchange Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Augustinas Markevicius <augustinas.markevicius@hatdex.org> 1, 2017
 */

package org.hatdex.dataplugTwitter.controllers

import javax.inject.Inject

import org.hatdex.dataplug.actors.IoExecutionContext
import org.hatdex.dataplug.apiInterfaces.models.{ DataPlugNotableShareRequest, DataPlugSharedNotable, JsonProtocol }
import org.hatdex.dataplug.services.{ DataPlugEndpointService, DataPlugNotablesService, DataplugSyncerActorManager }
import org.hatdex.dataplug.utils.{ JwtPhataAuthenticatedAction, JwtPhataAwareAction }
import org.hatdex.dataplugTwitter.apiInterfaces.TwitterStatusUpdateInterface
import org.joda.time.DateTime
import play.api.{ Configuration, Logger }
import play.api.i18n.MessagesApi
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Api @Inject() (
    messagesApi: MessagesApi,
    configuration: Configuration,
    tokenUserAwareAction: JwtPhataAwareAction,
    tokenUserAuthenticatedAction: JwtPhataAuthenticatedAction,
    dataPlugEndpointService: DataPlugEndpointService,
    dataPlugNotablesService: DataPlugNotablesService,
    twitterStatusUpdateInterface: TwitterStatusUpdateInterface,
    syncerActorManager: DataplugSyncerActorManager) extends Controller {

  val logger = Logger("application")

  val ioEC = IoExecutionContext.ioThreadPool

  import JsonProtocol.endpointStatusFormat
  def status: Action[AnyContent] = tokenUserAuthenticatedAction.async { implicit request =>
    // Check if the user has the required social profile linked
    request.identity.linkedUsers.find(_.providerId == "twitter") map {
      case _ =>
        val result = for {
          _ <- syncerActorManager.currentProviderApiVariantChoices(request.identity, "twitter")(ioEC)
          apiEndpointStatuses <- dataPlugEndpointService.listCurrentEndpointStatuses(request.identity.userId)
        } yield {
          Ok(Json.toJson(apiEndpointStatuses))
        }

        // In case fetching current endpoint statuses failed, assume the issue came from refreshing data from the provider
        result recover {
          case e =>
            Forbidden(
              Json.toJson(Map(
                "message" -> "Forbidden",
                "error" -> "The user is not authorized to access remote data - has Access Token been revoked?")))
        }
    } getOrElse {
      Future.successful(
        Forbidden(
          Json.toJson(Map(
            "message" -> "Forbidden",
            "error" -> "Required social profile not connected"))))
    }
  }

  def create: Action[DataPlugNotableShareRequest] = Action.async(BodyParsers.parse.json[DataPlugNotableShareRequest]) { implicit request =>
    request.headers.get("x-auth-token") map { secret =>
      val configuredSecret = configuration.getString("service.notables.secret").getOrElse("")

      if (secret == configuredSecret) {
        val notableShareRequest = request.body

        dataPlugNotablesService.find(notableShareRequest.notableId) flatMap { maybeNotableStatus =>
          if (!maybeNotableStatus.exists(_.posted)) {
            val sharedNotable = maybeNotableStatus.getOrElse(notableShareRequest.dataPlugSharedNotable)
            for {
              statusUpdate <- twitterStatusUpdateInterface.post(notableShareRequest.hatDomain, notableShareRequest.message)
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
      val configuredSecret = configuration.getString("service.notables.secret").getOrElse("")

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
      "error" -> error
    ))
}