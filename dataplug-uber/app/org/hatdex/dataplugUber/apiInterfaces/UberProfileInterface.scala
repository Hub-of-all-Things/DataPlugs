package org.hatdex.dataplugUber.apiInterfaces

import akka.Done
import akka.actor.Scheduler
import akka.util.Timeout
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import org.hatdex.dataplug.actors.Errors.SourceDataProcessingException
import org.hatdex.dataplug.apiInterfaces.DataPlugEndpointInterface
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.{ AuthenticatedHatClient, FutureTransformations, Mailer }
import org.hatdex.dataplugUber.apiInterfaces.authProviders.UberProvider
import org.hatdex.dataplugUber.models.UberProfile
import play.api.Logger
import play.api.libs.json._
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class UberProfileInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: UberProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth2 {

  val namespace: String = "monzo"
  val endpoint: String = "uberprofile"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint: ApiEndpointCall = UberProfileInterface.defaultApiEndpoint

  val refreshInterval: FiniteDuration = 7.days

  def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    None
  }

  def buildNextSync(content: JsValue, params: ApiEndpointCall): ApiEndpointCall = {
    params
  }

  override protected def processResults(
    content: JsValue,
    hatAddress: String,
    hatClient: AuthenticatedHatClient,
    fetchParameters: ApiEndpointCall)(implicit ec: ExecutionContext, timeout: Timeout): Future[Done] = {

    logger.debug("processing results")

    val validatedData: Try[JsArray] = validateMinDataStructure(content)

    // Shape results into HAT data records
    val resultsPosted = for {
      validatedData <- FutureTransformations.transform(validatedData) // Parse calendar events into strongly-typed structures
      _ <- uploadHatData(namespace, endpoint, validatedData, hatAddress, hatClient) // Upload the data
    } yield {
      logger.debug(s"Successfully synced new records for HAT $hatAddress")
      Done
    }

    resultsPosted
  }

  override def validateMinDataStructure(rawData: JsValue): Try[JsArray] = {
    rawData match {
      case data: JsObject if data.validate[UberProfile].isSuccess =>
        logger.debug(s"Validated JSON profile object")
        Success(JsArray(Seq(data)))
      case data: JsObject =>
        logger.error(s"Error validating data, some of the required fields missing: ${data.toString}")
        Failure(SourceDataProcessingException(s"Error validating data, some of the required fields missing."))
      case data =>
        logger.error(s"Error parsing JSON object: ${data.toString} ${data.validate[UberProfile]}")
        Failure(SourceDataProcessingException(s"Error parsing JSON object."))
    }
  }
}

object UberProfileInterface {
  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.uber.com",
    "/v1.2/me",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map(),
    Map(),
    Some(Map()))
}