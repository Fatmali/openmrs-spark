package org.spark.jobs

import org.apache.spark.streaming.dstream.DStream
import org.spark.stream.{KafkaDStreamSource, KafkaPayload, KafkaPayloadStringCodec, SparkStreamingApplication}

import scala.concurrent.duration.FiniteDuration


class EncounterJob(config: EncounterJobConfig, source: KafkaDStreamSource) extends SparkStreamingApplication {

  final val PATH = "payload.after"
  override def streamingBatchDuration: FiniteDuration = config.streamingBatchDuration

  override def streamingCheckpointDir: String = "./checkpoint/encounter" + config.streamingCheckpointDir

  override def sparkConfig: Map[String, String] = config.spark

  def start(): Unit = {

    withSparkStreamingContext { (spark, ssc) =>
      println("\n<-------Encounter Job------->\n")
      import spark.implicits._
      val kafkaStream: DStream[KafkaPayload] = source.createSource(ssc, Array("dbserver1.openmrs.encounter"))
      val stringCodec = spark.sparkContext.broadcast(KafkaPayloadStringCodec())
      val encounterIDsStream = kafkaStream.map(record => stringCodec.value.decodeValue(record).getOrElse(""))
      encounterIDsStream.foreachRDD(rdd => {
        if(!rdd.isEmpty) {
          val encounterDF = spark.read.json(rdd.toDS).select(PATH+".encounter_id", PATH+".voided").withColumnRenamed("voided","encounter_voided")
          encounterDF.write.parquet("spark-warehouse/encounter_ids")
        }
      })
    }
  }
}


object EncounterJob {

  def main(args: Array[String]): Unit = {
    val config = EncounterJobConfig()
    val streamingJob = new EncounterJob(config, KafkaDStreamSource(config.sourceKafka))
    streamingJob.start()
  }

}

case class EncounterJobConfig(
                      inputTopic: Array[String],
                      outputTopic: String,
                      windowDuration: FiniteDuration,
                      slideDuration: FiniteDuration,
                      spark: Map[String, String],
                      streamingBatchDuration: FiniteDuration,
                      streamingCheckpointDir: String,
                      sourceKafka: Map[String, String],
                      sinkKafka: Map[String, String]
                    ) extends Serializable




object EncounterJobConfig {

  import com.typesafe.config.{Config, ConfigFactory}
  import net.ceedubs.ficus.Ficus._

  def apply(): EncounterJobConfig = apply(ConfigFactory.load)

  def apply(applicationConfig: Config): EncounterJobConfig = {

    val config = applicationConfig.getConfig("config")

    new EncounterJobConfig(
      config.as[Array[String]]("input.topics"),
      config.as[String]("output.topic"),
      config.as[FiniteDuration]("windowDuration"),
      config.as[FiniteDuration]("slideDuration"),
      config.as[Map[String, String]]("spark"),
      FiniteDuration(1, "seconds"),
      config.as[String]("streamingCheckpointDir"),
      config.as[Map[String, String]]("kafkaSource"),
      config.as[Map[String, String]]("kafkaSink")
    )
  }

}


