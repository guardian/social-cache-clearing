package socialCacheClearing

import com.amazonaws.services.kinesis.clientlibrary.types.UserRecord
import com.amazonaws.services.kinesis.model.Record
import com.amazonaws.services.lambda.runtime.events.KinesisEvent
import com.gu.contentapi.client.GuardianContentClient
import com.gu.crier.model.event.v1.EventType
import com.gu.crier.model.event.v1.EventType._
import scala.concurrent.ExecutionContext.Implicits.global

import scala.collection.JavaConverters._
import scala.concurrent.{Await, duration}

class Lambda {
  val updateEventTypes = Set[EventType](Update, RetrievableUpdate)
  val capiClient = new GuardianContentClient(Credentials.getCredential("capi-api-key"))

  def handler(event: KinesisEvent): Unit = {

    val rawRecords: List[Record] = event.getRecords.asScala.map(_.getKinesis).toList
    val userRecords = UserRecord.deaggregate(rawRecords.asJava).asScala

    println(s"Processing ${userRecords.size} records ...")

    val idsForUpdateEvents: Set[String] = Await.result(CAPIClient.updateEventIds(userRecords), duration.Duration.Inf)
    println(s"Recently updated content ids: $idsForUpdateEvents")

    val sharedUrls = OphanReferralsAPI.twitterReferralsForIds(idsForUpdateEvents)
    println(s"Twitter referrals: ${Await.result(sharedUrls, duration.Duration.Inf)}")

    val twitterRefreshResponses = OphanReferralsAPI.twitterReferralsForIds(idsForUpdateEvents).flatMap(TwitterClient.refreshForSharedUrls).map(_.map(_.url_resolution_status))
    println(s"${Await.result(twitterRefreshResponses, duration.Duration.Inf)}")
  }
}

object Main extends App {
  private val client = TwitterClient.restClient

  val testCAPIIDs = Set(
    "politics/2019/nov/21/with-the-polls-offering-little-in-the-way-of-hope-corbyn-goes-for-broke",
    "lifeandstyle/lostinshowbiz/2019/nov/21/all-hail-the-new-lorraine-kelly-a-gritty-heroine-for-our-times"
  )

  val testSharedUris = OphanReferralsAPI.twitterReferralsForIds(testCAPIIDs)
  println(s"Twitter referrals: ${Await.result(testSharedUris, duration.Duration.Inf)}")

//  val testUrl = "https://www.theguardian.com/info/2019/mar/08/has-mary-wollstonecrafts-cpu-spiked"
//  println(Await.result(TwitterClient.restClient.refresh(testUrl), duration.Duration.Inf))
}


