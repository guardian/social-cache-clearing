package socialCacheClearing

import com.amazonaws.services.lambda.runtime.events.KinesisEvent
import com.amazonaws.services.kinesis.model.Record
import scala.collection.JavaConverters._
import com.amazonaws.services.kinesis.clientlibrary.types.UserRecord
import com.gu.crier.model.event.v1.EventType

object Lambda {
  def handler(event: KinesisEvent): Unit = {

    val rawRecords: List[Record] = event.getRecords.asScala.map(_.getKinesis).toList
    val userRecords = UserRecord.deaggregate(rawRecords.asJava)

    println(s"Processing ${userRecords.size} records ...")

    RecordProcessor.process(userRecords.asScala) { event =>
      event.eventType match {
        case EventType.Update => sendTwitterDecacheRequest(event.payloadId)
        case EventType.RetrievableUpdate => sendTwitterDecacheRequest(event.payloadId)
        case other => false
      }
    }

  }

  def sendTwitterDecacheRequest(id: String): Boolean = {
    println(s"sending Twitter decache request for ${id}")
    true
  }
}
