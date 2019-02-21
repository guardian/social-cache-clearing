package socialCacheClearing

import com.amazonaws.services.kinesis.clientlibrary.types.UserRecord
import com.gu.crier.model.event.v1.Event
import com.gu.thrift.serializer.ThriftDeserializer
import scala.util.Try

object RecordProcessor {

  def process(records: Seq[UserRecord])(process: Event => Boolean) = {
    val recordsProcessedResult = records.flatMap { record =>
      val bytes = record.getData.array
      val event: Try[Event] = ThriftDeserializer.deserialize(bytes)(Event)

      event.map{
        e => process(e)
      }.recover{
        case error =>
          println("Failed to deserialize Crier event from Kinesis record. Skipping.")
          false
      }.toOption

    }

    println(s"Successfully processed ${recordsProcessedResult.count(_ == true)} events")
  }

}