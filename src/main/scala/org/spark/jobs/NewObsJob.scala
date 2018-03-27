

package org.spark.jobs

import org.apache.spark.streaming.dstream.DStream
import org.spark.stream._

import scala.concurrent.duration.FiniteDuration
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import org.elasticsearch.spark.sql._

class NewObsJob(config: ObsJobConfig, source: KafkaDStreamSource) extends SparkStreamingApplication {
  final val PATH = "payload.after"
  override def sparkConfig: Map[String, String] = config.spark

  override def streamingBatchDuration: FiniteDuration = config.streamingBatchDuration

  override def streamingCheckpointDir: String = "./checkpoint/obs"+ config.streamingCheckpointDir

  def start(): Unit = {
      withSparkStreamingContext { (spark, ssc) =>
        import spark.implicits._

        val kafkaStream: DStream[KafkaPayload] = source.createSource(ssc, Array("dbserver1.openmrs.obs"))
        println("\n<-------Obs Job------->\n")

        val stringCodec = spark.sparkContext.broadcast(KafkaPayloadStringCodec())

        val obsStream = kafkaStream.map(record => stringCodec.value.decodeValue(record).getOrElse(""))

        obsStream.foreachRDD(rdd => {
          if(!rdd.isEmpty) {

            val encounters = spark.read.parquet("spark-warehouse/encounter_ids")

            val obs = spark.read.json(rdd.toDS)
              .select(PATH+".obs_id", PATH+".obs_group_id",PATH+".concept_id",PATH+".value_coded",PATH+".value_text",PATH+".value_numeric", PATH+".voided",
                      PATH+".person_id",PATH+".encounter_id",PATH+".value_datetime",PATH+".value_modifier",PATH+".value_drug")
              .withColumnRenamed("voided","obs_voided")
              .join(encounters,"encounter_id")

            val filteredVals = obs.filter("value_coded is not null").withColumn("value",$"value_coded").withColumn("value_type",lit("coded"))
                                    .union(obs.filter("value_text is not null").withColumn("value",$"value_text").withColumn("value_type",lit("text")))
                                      .union(obs.filter("value_numeric is not null").withColumn("value",$"value_numeric").withColumn("value_type",lit("numeric")))
                                        .union(obs.filter("value_datetime is not null").withColumn("value",$"value_datetime").withColumn("value_type",lit("datetime")))
                                            .union(obs.filter("value_modifier is not null").withColumn("value",$"value_modifier").withColumn("value_type", lit("modifier")))
                                                .union(obs.filter("value_drug is not null").withColumn("value",$"value_drug").withColumn("value_type", lit("drug")))

            val finalObsJson = filteredVals
                .withColumn("obs", to_json(struct($"obs_id", $"obs_voided", $"value_type", map($"concept_id",$"value").as("ob"))))
                .groupBy("encounter_id","person_id","encounter_voided")
                .agg(collect_set($"obs").alias("obs"))

            finalObsJson.printSchema()

            finalObsJson.write.json("spark-warehouse/obs.json")

            finalObsJson.saveToEs("kafka/obs_v1")


          }
        })
      }

  }
}


  object NewObsJob {

    def main(args: Array[String]): Unit = {
      val config = ObsJobConfig()
      val streamingJob = new NewObsJob(config, KafkaDStreamSource(config.sourceKafka))
      streamingJob.start()
    }

  }

case class ObsJobConfig(
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




object ObsJobConfig {

    import com.typesafe.config.{Config, ConfigFactory}
    import net.ceedubs.ficus.Ficus._

    def apply(): ObsJobConfig = apply(ConfigFactory.load)

    def apply(applicationConfig: Config): ObsJobConfig = {

      val config = applicationConfig.getConfig("config")

      new ObsJobConfig(
        config.as[Array[String]]("input.topics"),
        config.as[String]("output.topic"),
        config.as[FiniteDuration]("windowDuration"),
        config.as[FiniteDuration]("slideDuration"),
        config.as[Map[String, String]]("spark"),
        config.as[FiniteDuration]("streamingBatchDuration"),
        config.as[String]("streamingCheckpointDir"),
        config.as[Map[String, String]]("kafkaSource"),
        config.as[Map[String, String]]("kafkaSink")
      )
    }



}
