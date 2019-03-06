package socialCacheClearing

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.regions.Regions
import com.amazonaws.services.kinesis.clientlibrary.types.UserRecord
import com.amazonaws.services.kinesis.model.Record
import com.amazonaws.services.lambda.runtime.events.KinesisEvent
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterRequest
import com.gu.crier.model.event.v1.EventType._
import com.gu.crier.model.event.v1.{Event, EventType}
import com.gu.thrift.serializer.ThriftDeserializer
import scalaj.http.Http

import scala.collection.JavaConverters._
import scala.util.parsing.json.JSONFormat

class Lambda {
  val updateEventTypes = Set[EventType](Update, RetrievableUpdate)

  def handler(event: KinesisEvent): Unit = {

    val rawRecords: List[Record] = event.getRecords.asScala.map(_.getKinesis).toList
    val userRecords = UserRecord.deaggregate(rawRecords.asJava).asScala

    println(s"Processing ${userRecords.size} records ...")

    val idsForUpdateEvents: Set[String] = updateEventIds(userRecords)

    idsForUpdateEvents.foreach(FacebookClient.scrapeForId)
  }

  def updateEventIds(records: Seq[UserRecord]): Set[String] = {
    records.flatMap { record =>
      ThriftDeserializer.deserialize(record.getData.array)(Event).toOption
    }.filter(event => updateEventTypes.contains(event.eventType)).map(_.payloadId).toSet
  }
}

object FacebookClient {
  val ssmClient = AWSSimpleSystemsManagementClientBuilder.standard()
    .withRegion(Regions.EU_WEST_1).build()

  val accessToken = ssmClient.getParameter(
    new GetParameterRequest()
    .withName("/social-cache-clearing/facebook-access-token")
    .withWithDecryption(true)
    .withRequestCredentialsProvider(new ProfileCredentialsProvider("capi"))
  ).getParameter.getValue

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
