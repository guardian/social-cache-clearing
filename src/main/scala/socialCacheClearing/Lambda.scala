package socialCacheClearing

import com.amazonaws.services.kinesis.clientlibrary.types.ProcessRecordsInput
import com.amazonaws.services.lambda.runtime.events.KinesisEvent
import com.amazonaws.services.kinesis.model.Record
import scala.collection.JavaConverters._
import com.amazonaws.services.kinesis.clientlibrary.types.UserRecord


object Lambda {
  def handler(event: KinesisEvent): Unit = {

    val rawRecords: List[Record] = event.getRecords.asScala.map(_.getKinesis).toList
    val userRecords = UserRecord.deaggregate(rawRecords.asJava)

    println(s"Processing ${userRecords.size} records ...")

    RecordProcessor.process(userRecords.asScala) { event =>
      (event.itemType, event.eventType) match {
        case (ItemType.Content, EventType.Delete) =>
          sendFastlyPurgeRequestAndAmpPingRequest(event.payloadId, Hard)
        case (ItemType.Content, EventType.Update) =>
          sendFastlyPurgeRequest(event.payloadId, Soft)
        case (ItemType.Content, EventType.RetrievableUpdate) =>
          sendFastlyPurgeRequest(event.payloadId, Soft)

        case other =>
          // for now we only send purges for content, so ignore any other events
          false
      }
    }

    ???
  }
}
