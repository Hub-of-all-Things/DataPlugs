/*
 * Copyright (C) 2016-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@dataswift.io>, 10 2016
 */

package com.hubofallthings.dataplug.dao

import akka.{ Done, NotUsed }
import akka.stream.scaladsl.Source
import com.hubofallthings.dataplug.actors.IoExecutionContext
import com.hubofallthings.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointStatus, ApiEndpointVariant }
import com.hubofallthings.dataplug.dal.Tables
import javax.inject.{ Inject, Singleton }
import org.hatdex.libs.dal.SlickPostgresDriver
import org.hatdex.libs.dal.SlickPostgresDriver.api._
import org.joda.time.DateTime
import play.api.Logger
import play.api.db.slick.{ DatabaseConfigProvider, HasDatabaseConfigProvider }
import play.api.libs.json.Json

import scala.concurrent._

/**
 * Give access to the user object.
 */
@Singleton
class DataPlugEndpointDAOImpl @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)
  extends DataPlugEndpointDAO with HasDatabaseConfigProvider[SlickPostgresDriver] {

  import com.hubofallthings.dataplug.dal.ModelTranslation._
  import com.hubofallthings.dataplug.apiInterfaces.models.JsonProtocol._

  implicit val ec: ExecutionContext = IoExecutionContext.ioThreadPool
  private val logger = Logger(this.getClass)

  /**
   * Retrieves a user that matches the specified ID.
   *
   * @param phata The user phata.
   * @return The list of retrieved endpoints, as a String value
   */
  def retrievePhataEndpoints(phata: String): Future[Seq[ApiEndpointVariant]] = {
    val q = Tables.DataplugEndpoint
      .join(Tables.DataplugUser)
      .on(_.name === _.dataplugEndpoint)
      .filter(q1 => q1._2.phata === phata && q1._2.active === true)

    db.run(q.result).map(_.map(resultRow => fromDbModel(resultRow._1, resultRow._2)))
  }

  /**
   * Retrieves a list of all registered endpoints together with corresponding user PHATAs
   *
   * @return The list of tuples of PHATAs and corresponding endpoints
   */
  def retrieveAllEndpoints: Future[Seq[(String, ApiEndpointVariant)]] = {
    val q = Tables.DataplugEndpoint
      .join(Tables.DataplugUser)
      .on(_.name === _.dataplugEndpoint)
      .filter(_._2.active === true)

    db.run(q.result).map(_.map(resultRow => (resultRow._2.phata, fromDbModel(resultRow._1, resultRow._2))))
  }

  def retrieveAllActiveEndpointsStream: Source[(String, String, ApiEndpointVariant), NotUsed] = {
    val newerThan = DateTime.now().minusDays(3).toLocalDateTime
    val q = Tables.HatToken
      .filter(_.dateCreated > newerThan)
      .join(Tables.DataplugUser)
      .on(_.hat === _.phata)
      .filter(_._2.active === true)
      .join(Tables.DataplugEndpoint)
      .on(_._2.dataplugEndpoint === _.name)

    Source.fromPublisher(db.stream(q.result.transactionally.withStatementParameters(fetchSize = 1000)))
      .map(resultRow => (resultRow._1._2.phata, resultRow._1._1.accessToken, fromDbModel(resultRow._2, resultRow._1._2)))
  }

  /**
   * Activates an API endpoint for a user
   *
   * @param phata The user phata.
   * @param plugName The plug endpoint name.
   */
  def activateEndpoint(phata: String, plugName: String, variant: Option[String], configuration: Option[ApiEndpointCall]): Future[Done] = {
    import com.hubofallthings.dataplug.apiInterfaces.models.JsonProtocol.endpointCallFormat
    logger.debug(s"activating endpoint for $phata/$plugName, variant: $variant, config: $configuration")
    val q = for {
      rowsAffected <- Tables.DataplugUser
        .filter(user => user.phata === phata && user.dataplugEndpoint === plugName && user.endpointVariant === variant)
        .map(user => (user.endpointConfiguration, user.active))
        .update(configuration.map(c => Json.toJson(c)), true)
      result <- rowsAffected match {
        case 0 => Tables.DataplugUser += toDbModel(phata, plugName, variant, configuration, true)
        case 1 => DBIO.successful(1)
        case n => DBIO.failed(new RuntimeException(s"Expected 0 or 1 change, not $n for user $phata"))
      }
    } yield result

    db.run(q).map(_ => Done)
  }

  /**
   * Deactivates API endpoint for a user
   *
   * @param phata The user phata.
   * @param plugName The plug endpoint name.
   */
  def deactivateEndpoint(phata: String, plugName: String, variant: Option[String]): Future[Done] = {
    val q = Tables.DataplugUser
      .filter(user => user.phata === phata && user.dataplugEndpoint === plugName && user.endpointVariant === variant)
      .map(_.active)
      .update(false)

    db.run(q).map(_ => Done)
  }

  /**
   * Saves endpoint status for a given phata and plug endpoint
   *
   * @param phata The user phata.
   * @param endpointStatus The plug endpoint name.
   */
  def saveEndpointStatus(phata: String, endpointStatus: ApiEndpointStatus): Future[Done] = {
    val eventualEndpointStatusLog = saveEndpointStatusLog(phata, endpointStatus)
    val eventualEndpointStatus = upsertEndpointStatus(phata, endpointStatus)

    for {
      _ <- eventualEndpointStatus
      _ <- eventualEndpointStatusLog
    } yield Done
  }

  /**
   * Saves endpoint status log for a given phata and plug endpoint
   *
   * @param phata The user phata.
   * @param endpointStatus The plug endpoint name.
   */
  private def saveEndpointStatusLog(phata: String, endpointStatus: ApiEndpointStatus): Future[Done] = {
    val q = Tables.LogDataplugUser += Tables.LogDataplugUserRow(
      0,
      phata,
      endpointStatus.apiEndpoint.endpoint.name,
      Json.toJson(endpointStatus.endpointCall),
      endpointStatus.apiEndpoint.variant,
      endpointStatus.timestamp.toLocalDateTime,
      endpointStatus.successful,
      endpointStatus.message)

    db.run(q).map(_ => Done)
  }

  /**
   * Upserts endpoint status for a given phata and plug endpoint
   *
   * @param phata The user phata.
   * @param endpointStatus The plug endpoint name.
   */
  private def upsertEndpointStatus(phata: String, endpointStatus: ApiEndpointStatus): Future[Done] = {

    val endpointUrl = endpointStatus.apiEndpoint.endpoint.name
    val dateNow = DateTime.now().toLocalDateTime

    val q = for {
      rowsAffected <- Tables.DataplugUserStatus
        .filter(log => log.phata === phata && log.dataplugEndpoint === endpointUrl && log.endpointVariant === endpointStatus.apiEndpoint.variant)
        .map(log => (log.phata, log.dataplugEndpoint, log.endpointVariant, log.endpointConfiguration, log.updated, log.successful, log.message))
        .update(phata, endpointStatus.apiEndpoint.endpoint.name, endpointStatus.apiEndpoint.variant, Json.toJson(endpointStatus.endpointCall), dateNow, endpointStatus.successful, endpointStatus.message)
      result <- rowsAffected match {
        case 0 => Tables.DataplugUserStatus += toDbModel(
          phata,
          endpointStatus.apiEndpoint.endpoint.name,
          endpointStatus.apiEndpoint.configuration,
          endpointStatus.apiEndpoint.variant,
          dateNow,
          dateNow,
          endpointStatus.successful,
          endpointStatus.message)
        case 1 => DBIO.successful(1)
        case n => DBIO.failed(new RuntimeException(s"Expected 0 or 1 change, not $n for user $phata"))
      }
    } yield result

    db.run(q).map(_ => Done)
  }

  /**
   * Fetches endpoint status from logs for a given phata and plug endpoint
   *
   * @param phata The user phata.
   * @return The available API endpoint configurations
   */
  def listEndpointStatusLogs(phata: String): Future[Seq[ApiEndpointStatus]] = {
    val innerQuery = Tables.LogDataplugUser.groupBy(u => (u.phata, u.dataplugEndpoint, u.endpointVariant))
      .map({
        case (key, group) =>
          (key._1, key._2, key._3, group.map(_.created).max)
      })

    val q = for {
      ldu <- Tables.LogDataplugUser.filter(ldu => ldu.phata === phata)
        .join(innerQuery).on((l, r) => l.phata === r._1 && l.dataplugEndpoint === r._2 && l.endpointVariant === r._3 && l.created === r._4)
        .map(_._1)
      du <- Tables.DataplugUser.filter(du => du.dataplugEndpoint === ldu.dataplugEndpoint && du.phata === ldu.phata && du.endpointVariant === ldu.endpointVariant)
      de <- ldu.dataplugEndpointFk
    } yield (ldu, du, de)

    db.run(q.result)
      .map { data =>
        data.map {
          case (ldu, du, de) =>
            ApiEndpointStatus(ldu.phata, fromDbModel(de, du), ldu.endpointConfiguration.as[ApiEndpointCall], ldu.created.toDateTime(), ldu.successful, ldu.message)
        }
      }
  }

  /**
   * Fetches endpoint status for a given phata and plug endpoint
   *
   * @param phata The user phata.
   * @return The available API endpoint configurations
   */
  def listCurrentEndpointStatuses(phata: String): Future[Seq[ApiEndpointStatus]] = {
    val query = for {
      ldu <- Tables.DataplugUserStatus.filter(_.phata === phata)
      du <- Tables.DataplugUser.filter(du => du.dataplugEndpoint === ldu.dataplugEndpoint && du.phata === ldu.phata && du.endpointVariant === ldu.endpointVariant)
      de <- ldu.dataplugEndpointFk
    } yield (ldu, du, de)

    db.run(query.result).map(_.map(r => fromDbModel(r._1, r._2, r._3)))
  }

  /**
   * Retrieves most recent endpoint status for a given phata and plug endpoint
   *
   * @param phata The user phata.
   * @param plugName The plug endpoint name.
   * @return The available API endpoint configuration
   */
  def retrieveLastSuccessfulEndpointVariant(phata: String, plugName: String, variant: Option[String]): Future[Option[ApiEndpointVariant]] = {
    val q = Tables.DataplugEndpoint
      .join(Tables.DataplugUser)
      .on(_.name === _.dataplugEndpoint)
      .filter(user => user._2.phata === phata && user._2.dataplugEndpoint === plugName && user._2.endpointVariant === variant)

    db.run(q.result).map(_.headOption.map(r => fromDbModel(r._1, r._2)))
  }

}
