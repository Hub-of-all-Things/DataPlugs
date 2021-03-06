/*
 * Copyright (C) 2017-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@dataswift.io> 5, 2017
 */

package com.hubofallthings.dataplugFacebook.apiInterfaces

import akka.actor.Scheduler
import com.google.inject.Inject
import com.hubofallthings.dataplug.apiInterfaces.DataPlugOptionsCollector
import com.hubofallthings.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import com.hubofallthings.dataplug.apiInterfaces.models.{ ApiEndpoint, ApiEndpointCall, ApiEndpointMethod, ApiEndpointVariant, ApiEndpointVariantChoice }
import com.hubofallthings.dataplug.services.UserService
import com.hubofallthings.dataplug.utils.Mailer
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.hubofallthings.dataplugFacebook.apiInterfaces.authProviders._
import com.typesafe.config.ConfigFactory
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.ws.WSClient

class FacebookProfileCheck @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: FacebookProvider) extends DataPlugOptionsCollector with RequestAuthenticatorOAuth2 {

  val namespace: String = "facebook"
  val endpoint: String = "profile"
  protected val logger: Logger = Logger(this.getClass)
  val baseApiUrl = ConfigFactory.load.getString("service.baseApiUrl")

  val defaultApiEndpoint = ApiEndpointCall(
    baseApiUrl,
    "/me",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map(),
    Map(),
    Some(Map()))

  def generateEndpointChoices(responseBody: Option[JsValue]): Seq[ApiEndpointVariantChoice] = staticEndpointChoices

  def staticEndpointChoices: Seq[ApiEndpointVariantChoice] = {
    val profileVariant = ApiEndpointVariant(
      ApiEndpoint("profile", "User's Facebook profile information", None),
      Some(""), Some(""),
      Some(FacebookProfileInterface.defaultApiEndpoint))

    val profilePictureVariant = ApiEndpointVariant(
      ApiEndpoint("profile/picture", "User's Facebook profile picture", None),
      Some(""), Some(""),
      Some(FacebookProfilePictureInterface.defaultApiEndpoint))

    val feedVariant = ApiEndpointVariant(
      ApiEndpoint("feed", "User's Facebook posts feed", None),
      Some(""), Some(""),
      Some(FacebookFeedInterface.defaultApiEndpoint))

    val postsVariant = ApiEndpointVariant(
      ApiEndpoint("posts", "User's own Facebook posts", None),
      Some(""), Some(""),
      Some(FacebookPostsInterface.defaultApiEndpoint))

    val choices = Seq(
      ApiEndpointVariantChoice("profile", "User's Facebook profile information", active = true, profileVariant),
      ApiEndpointVariantChoice("profile/picture", "User's Facebook profile picture", active = true, profilePictureVariant),
      ApiEndpointVariantChoice("feed", "User's Facebook posts feed", active = true, feedVariant),
      ApiEndpointVariantChoice("posts", "User's own Facebook posts", active = true, postsVariant))

    choices
  }
}
