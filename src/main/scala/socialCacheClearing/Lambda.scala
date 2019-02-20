package socialCacheClearing

import com.amazonaws.services.kinesis.clientlibrary.types.ProcessRecordsInput
import com.amazonaws.services.lambda.runtime.Context

object Lambda {
  def handler(lambdaInput: ProcessRecordsInput, context: Context): Unit = {
    ???
  }
}
