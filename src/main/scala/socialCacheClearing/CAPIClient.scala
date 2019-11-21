package socialCacheClearing

import java.time.Duration.ofMinutes
import java.time.Instant
import java.time.Instant.ofEpochMilli

import com.amazonaws.services.kinesis.clientlibrary.types.UserRecord
import com.gu.contentapi.client.{ContentApiClient, GuardianContentClient}
import com.gu.contentapi.client.model.ItemQuery
import com.gu.contentapi.client.model.v1.Content
import com.gu.crier.model.event.v1.EventType.{RetrievableUpdate, Update}
import com.gu.crier.model.event.v1.{Event, EventPayload, EventType, RetrievableContent}
import com.gu.thrift.serializer.ThriftDeserializer
import scala.math.Ordering.Implicits._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object CAPIClient {
  val capiClient = new GuardianContentClient(Credentials.getCredential("capi-api-key"))
  val updateEventTypes = Set[EventType](Update, RetrievableUpdate)

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
