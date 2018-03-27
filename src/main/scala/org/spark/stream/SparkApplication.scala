

package org.spark.stream

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession

trait SparkApplication {

  def sparkConfig: Map[String, String]

  def withSparkContext(f: SparkSession => Unit): Unit = {
    val conf = new SparkConf()
    sparkConfig.foreach { case (k, v) => conf.setIfMissing(k, v) }
    val spark = SparkSession.builder().config(conf).getOrCreate
    f(spark)
  }

}
