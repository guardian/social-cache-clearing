package socialCacheClearing

import com.danielasfregola.twitter4s.TwitterRefreshClient
import com.danielasfregola.twitter4s.entities.{AccessToken, ConsumerToken}

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
}
