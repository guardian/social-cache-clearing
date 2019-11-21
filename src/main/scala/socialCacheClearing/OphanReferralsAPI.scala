package socialCacheClearing

import play.api.libs.json.{JsError, JsSuccess, Json, JsonValidationError, Reads}

import scala.concurrent.Future
import sttp.client._
import sttp.client.asynchttpclient.future._
import sttp.model.Uri

import scala.concurrent.ExecutionContext.Implicits.global

object OphanReferralsAPI {

  val apiKey = Credentials.getCredential("ophan/api-key")

  def slashPrefixedPath(capiId: String) = "/" + capiId

  implicit lazy val backend = AsyncHttpClientFutureBackend()

  def twitterReferrals(capiId: String): Future[Seq[ReferralCount]] = {
    implicit val reads: Reads[ReferralCount] = Json.reads[ReferralCount]

    val uri = uri"https://ophan.dashboard.co.uk/api/twitter/referrals?path=${slashPrefixedPath(capiId)}&api-key=$apiKey"

    def referralCounts(response: String): Future[Seq[ReferralCount]] = {
      Json.parse(response).validate[Seq[ReferralCount]] match {
        case JsSuccess(value, _) => Future.successful(value)
        case JsError(errors) => Future.failed(
          //TODO: change this - not sure what we want to log
          JsonParsingError(errors.flatMap(_._2))
        )
      }
    }

    def getResponse(uri: Uri): Future[String] = {
      basicRequest.get(uri).send().flatMap { _.body match {
          case Right(res) => Future.successful(res)
          case Left(err) => Future.failed(new Throwable(err))
        }
      }
    }

    for {
      resp <- getResponse(uri)
      referralCounts <- referralCounts(resp)
    } yield referralCounts
  }

  def sharedUrisForIds(ids: Set[String]): Future[Seq[ReferralCount]] = {
    Future.traverse(ids)(twitterReferrals).map(_.flatten)
  }

  case class JsonParsingError(errors: Seq[JsonValidationError]) extends Throwable

  case class ReferralCount(item: String, count: Int)

}
