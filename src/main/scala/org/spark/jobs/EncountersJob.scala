package org.spark.jobs

import org.apache.spark.streaming.dstream.DStream
import org.spark.stream.{KafkaDStreamSource, KafkaPayload, KafkaPayloadStringCodec, SparkStreamingApplication}

import scala.concurrent.duration.FiniteDuration


class EncounterJob(config: JobConfig, source: KafkaDStreamSource) extends SparkStreamingApplication {

  override def streamingBatchDuration: FiniteDuration = config.streamingBatchDuration

  override def streamingCheckpointDir: String = "./checkpoint"+config.

  override def sparkConfig: Map[String, String] = config.spark

  def start(): Unit = {
    withSparkStreamingContext { (sc, ssc) =>
    val kafkaStream: DStream[KafkaPayload] = source.createSource(ssc, "dbserver1.openmrs.obs, dbserver1.openmrs.encounter")
    println("Job------------------------------------------------>")
    val stringCodec = sc.broadcast(KafkaPayloadStringCodec())
    val json = kafkaStream.map(record => (record.key, record.value))
      json.print();
    }
  }
}


object EncounterJob {

  def main(args: Array[String]): Unit = {
    val config = JobConfig()
    val streamingJob = new EncounterJob(config, KafkaDStreamSource(config.sourceKafka))
    streamingJob.start()
  }

}



