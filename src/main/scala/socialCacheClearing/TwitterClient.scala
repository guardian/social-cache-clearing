package socialCacheClearing

import com.danielasfregola.twitter4s.{RefreshResponse, TwitterRefreshClient}
import com.danielasfregola.twitter4s.entities.{AccessToken, ConsumerToken}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

object TwitterClient {
  val consumerToken = {
    val Array(key,secret) = Credentials.getCredential("twitter/consumer-key").split(':')
    ConsumerToken(key,secret)
  }

  val accessToken = {
    val Array(key,secret) = Credentials.getCredential("twitter/access-token").split(':')
    AccessToken(key,secret)
  }

  val restClient = new TwitterRefreshClient(consumerToken, accessToken)

  def refreshForSharedUrl(url: String): Future[RefreshResponse] = {
    restClient.refresh(url)
  }

  def refreshForSharedUrls(urls: Set[String]): Future[Set[RefreshResponse]] = {
    Future.traverse(urls)(refreshForSharedUrl)
  }
}
