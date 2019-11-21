package socialCacheClearing

import java.time.Duration.ofMinutes
import java.time.Instant
import java.time.Instant.ofEpochMilli

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpRequest
import akka.stream.ActorMaterializer
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, EnvironmentVariableCredentialsProvider, InstanceProfileCredentialsProvider}
import com.amazonaws.regions.Regions
import com.amazonaws.services.kinesis.clientlibrary.types.UserRecord
import com.amazonaws.services.kinesis.model.Record
import com.amazonaws.services.lambda.runtime.events.KinesisEvent
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterRequest
import com.danielasfregola.twitter4s.{TwitterRefreshClient, TwitterRestClient}
import com.danielasfregola.twitter4s.entities.{AccessToken, ConsumerToken}
import com.gu.contentapi.client.model.ItemQuery
import com.gu.contentapi.client.model.v1.Content
import com.gu.contentapi.client.{ContentApiClient, GuardianContentClient}
import com.gu.crier.model.event.v1.EventType._
import com.gu.crier.model.event.v1.{Event, EventPayload, EventType, RetrievableContent}
import com.gu.thrift.serializer.ThriftDeserializer
import scalaj.http.Http

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future, duration}
import scala.math.Ordering.Implicits._
import scala.util.parsing.json.JSONFormat

class Lambda {
  val updateEventTypes = Set[EventType](Update, RetrievableUpdate)
  val capiClient = new GuardianContentClient(Credentials.getCredential("capi-api-key"))

  def handler(event: KinesisEvent): Unit = {

    val rawRecords: List[Record] = event.getRecords.asScala.map(_.getKinesis).toList
    val userRecords = UserRecord.deaggregate(rawRecords.asJava).asScala

    println(s"Processing ${userRecords.size} records ...")

    val idsForUpdateEvents: Set[String] = Await.result(updateEventIds(userRecords), duration.Duration.Inf)
    println(s"Recently updated content ids: $idsForUpdateEvents")

    val sharedUris = OphanReferralsAPI.sharedUrisForIds(idsForUpdateEvents)

    println(s"Twitter referrals: ${Await.result(sharedUris, duration.Duration.Inf)}")
  }

  def retrieveContent(retrievableContent: RetrievableContent): Future[Option[Content]] = {

    val contentId = retrievableContent.id

    println(s"Fetching retrievable content with id: $contentId from the Content API")

    val query: ItemQuery = ContentApiClient.item(contentId)

    capiClient.getResponse(query).map(_.content)
  }

  def content(payload: EventPayload): Future[Option[Content]] = payload match {
    case EventPayload.Content(content) => Future.successful(Some(content))
    case EventPayload.RetrievableContent(retrievableContent) => retrieveContent(retrievableContent)
    case _ => Future.successful(None)
  }

  def idForContentWithRecentWebPublicationDate(event: Event): Future[Option[String]] = {
    val recencyThreshold = ofMinutes(5)

    event.payload match {
      case None => Future.successful(None)
      case Some(payload) => for {contentOpt <- content(payload)} yield {
        for {
          content <- contentOpt
          webPublicationDate <- content.webPublicationDate if ofEpochMilli(webPublicationDate.dateTime).plus(recencyThreshold) > Instant.now()
        } yield content.id
      }
    }

  }

  def updateEventIds(records: Seq[UserRecord]) = {

    val events = (for {
      record <- records
      event <- ThriftDeserializer.deserialize(record.getData.array)(Event).toOption.toSeq
    } yield event).toSet

    Future.traverse(events)(idForContentWithRecentWebPublicationDate).map(_.flatten)
  }
}

object Credentials {
  val credentialsProvider = new AWSCredentialsProviderChain(
    new InstanceProfileCredentialsProvider(false),
    new ProfileCredentialsProvider("capi"),
    new EnvironmentVariableCredentialsProvider
  )

  val systemsManagerClient = AWSSimpleSystemsManagementClientBuilder
    .standard()
    .withCredentials(credentialsProvider)
    .withRegion(Regions.EU_WEST_1)
    .build()

  def getCredential(name: String) = systemsManagerClient.getParameter(
    new GetParameterRequest()
      .withName("/social-cache-clearing/"+name)
      .withWithDecryption(true)
  ).getParameter.getValue
}

object Main extends App {
  private val client = TwitterClient.restClient

  val testUrl = "https://www.theguardian.com/info/2019/mar/08/has-mary-wollstonecrafts-cpu-spiked"

  println(Await.result(TwitterClient.restClient.refresh(testUrl), duration.Duration.Inf))
}

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
