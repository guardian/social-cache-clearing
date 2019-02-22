package socialCacheClearing

import com.amazonaws.services.kinesis.clientlibrary.types.UserRecord
import com.amazonaws.services.kinesis.model.Record
import com.amazonaws.services.lambda.runtime.events.KinesisEvent
import com.gu.crier.model.event.v1.EventType._
import com.gu.crier.model.event.v1.{Event, EventType}
import com.gu.thrift.serializer.ThriftDeserializer

import scala.collection.JavaConverters._

class Lambda {
  val updateEventTypes = Set[EventType](Update, RetrievableUpdate)

  def handler(event: KinesisEvent): Unit = {

    val rawRecords: List[Record] = event.getRecords.asScala.map(_.getKinesis).toList
    val userRecords = UserRecord.deaggregate(rawRecords.asJava).asScala

    println(s"Processing ${userRecords.size} records ...")

    val idsForUpdateEvents: Set[String] = updateEventIds(userRecords)

    idsForUpdateEvents.foreach(sendTwitterDecacheRequest)
  }

  def sendTwitterDecacheRequest(id: String) = {
    println(s"sending Twitter decache request for ${id}")
  }

  def updateEventIds(records: Seq[UserRecord]): Set[String] = {
    records.flatMap { record =>
      ThriftDeserializer.deserialize(record.getData.array)(Event).toOption
    }.filter(event => updateEventTypes.contains(event.eventType)).map(_.payloadId).toSet
  }
}
