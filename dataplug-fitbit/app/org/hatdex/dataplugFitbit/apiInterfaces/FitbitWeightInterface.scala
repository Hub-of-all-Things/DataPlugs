package org.hatdex.dataplugFitbit.apiInterfaces

import akka.actor.ActorRef
import akka.util.Timeout
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import org.hatdex.commonPlay.utils.FutureTransformations
import org.hatdex.dataplug.apiInterfaces.DataPlugEndpointInterface
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.Mailer
import org.hatdex.dataplugFitbit.apiInterfaces.authProviders.FitbitProvider
import org.hatdex.dataplugFitbit.models.FitbitWeight
import org.joda.time.DateTime
import org.joda.time.format.{ DateTimeFormat, DateTimeFormatter }
import play.api.Logger
import play.api.cache.CacheApi
import play.api.libs.json._
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class FitbitWeightInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val cacheApi: CacheApi,
    val mailer: Mailer,
    val provider: FitbitProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth2 {

  val namespace: String = "fitbit"
  val endpoint: String = "weight"
  protected val logger: Logger = Logger("FitbitWeightInterface")

  val defaultApiEndpoint = FitbitWeightInterface.defaultApiEndpoint

  val refreshInterval = 24.hours

  def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    None
  }

  def buildNextSync(content: JsValue, params: ApiEndpointCall): ApiEndpointCall = {
    val nextSyncDate = DateTime.now.toString(FitbitWeightInterface.apiDateFormat)
    val nextPathParameters = params.copy(pathParameters = params.pathParameters + ("date" -> nextSyncDate))

    nextPathParameters
  }

  override def buildFetchParameters(params: Option[ApiEndpointCall])(implicit ec: ExecutionContext): Future[ApiEndpointCall] = {
    logger.debug(s"Custom building fetch params: \n $params")

    val finalFetchParams = params.map { p =>
      p.pathParameters.get("date").map { _ => p }.getOrElse {
        val updatedParameters = p.pathParameters + ("date" -> DateTime.now.minusDays(1).toString(FitbitWeightInterface.apiDateFormat))
        p.copy(pathParameters = updatedParameters)
      }

    }.getOrElse {
      val updatedParameters = defaultApiEndpoint.pathParameters + ("date" -> DateTime.now.minusDays(1).toString(FitbitWeightInterface.apiDateFormat))

      defaultApiEndpoint.copy(pathParameters = updatedParameters)
    }

    logger.debug(s"Final fetch parameters: \n $finalFetchParams")

    Future.successful(finalFetchParams)
  }

  override protected def processResults(
    content: JsValue,
    hatAddress: String,
    hatClientActor: ActorRef,
    fetchParameters: ApiEndpointCall)(implicit ec: ExecutionContext, timeout: Timeout): Future[Unit] = {

    for {
      validatedData <- FutureTransformations.transform(validateMinDataStructure(content))
      _ <- uploadHatData(namespace, endpoint, validatedData, hatAddress, hatClientActor) // Upload the data
    } yield {
      logger.debug(s"Successfully synced new records for HAT $hatAddress")
    }
  }

  def validateMinDataStructure(rawData: JsValue): Try[JsArray] = {
    (rawData \ "weight").toOption.map {
      case data: JsArray if data.validate[List[FitbitWeight]].isSuccess =>
        logger.debug(s"Validated JSON object:\n${data.toString}")
        Success(data)
      case data: JsObject =>
        logger.error(s"Error validating data, some of the required fields missing:\n${data.toString}")
        Failure(new RuntimeException(s"Error validating data, some of the required fields missing."))
      case _ =>
        logger.error(s"Error parsing JSON object: ${rawData.toString}")
        Failure(new RuntimeException(s"Error parsing JSON object."))
    }.getOrElse {
      logger.error(s"Error parsing JSON object: ${rawData.toString}")
      Failure(new RuntimeException(s"Error parsing JSON object."))
    }
  }

}

object FitbitWeightInterface {
  val apiDateFormat: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd")

  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.fitbit.com",
    "/1/user/-/body/log/weight/date/[date].json",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map(),
    Map())
}

