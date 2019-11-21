package socialCacheClearing

import scalaj.http.Http

import scala.util.parsing.json.JSONFormat

object FacebookClient {
  val accessToken = Credentials.getCredential("facebook-access-token")

  def scrapeForId(id: String) = {
    val postId = s"http://www.theguardian.com/${id}"
    println(s"sending Facebook decache request for $postId")

    // POST /?id={object-instance-id or object-url}&scrape=true
    val graphUrl = "https://graph.facebook.com"
    val queryString = Map("id" -> postId, "scrape" -> "true", "access_token" -> accessToken)
    val qsJson = scala.util.parsing.json.JSONObject(queryString).toString(JSONFormat.defaultFormatter)

    val response = Http(graphUrl).postData(qsJson)
      .header("Content-Type", "application/json")
      .header("Charset", "UTF-8").asString

    println(s"response code: ${response.code}, response: $response")
  }
}
