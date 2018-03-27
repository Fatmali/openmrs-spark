

package org.spark.stream

import scala.concurrent.duration.FiniteDuration
import org.apache.spark.sql.SparkSession
import org.apache.spark.streaming.Seconds
import org.apache.spark.streaming.StreamingContext

trait SparkStreamingApplication extends SparkApplication {

  def streamingBatchDuration: FiniteDuration

  def streamingCheckpointDir: String

  def withSparkStreamingContext(f: (SparkSession, StreamingContext) => Unit): Unit = {
    withSparkContext { spark =>
      val ssc = new StreamingContext(spark.sparkContext, Seconds(streamingBatchDuration.toSeconds))
      ssc.checkpoint(streamingCheckpointDir)
      f(spark, ssc)
      ssc.start()
      ssc.awaitTermination()
    }
  }

}
