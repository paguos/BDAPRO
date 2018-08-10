package partitioners


import java.io.File

import EntityMatchers.EntityMatch
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import utilities.{LookupGenerator, Utilities}

import scala.collection.JavaConversions._

class DeletePermutations extends Serializable {
  def produceSimilarity(row1:Array[String],row2:Array[String],compAlg:String,threshold:Double):EntityMatch={
    lazy val dm= Utilities.getDistanceMeasure(compAlg)
    var sim=0.0;
    val dataColsLen= row1.length-1;
//    if (row1.length != row2.length){
//      EntityMatch(row1(0)+" len"+row1.length,row2(0)+ " len "+row2.length,100.0)
//    }else {
      var simNormal = dataColsLen

      def decSim(): Unit = {
        simNormal -= 1
      }

      for (i <- 1 to dataColsLen) {
        sim += (if (row1(i) != null && row2(i) != null) 1 - dm.apply(row1(i), row2(i)) else {
          decSim();
          0;
        })
      }
      EntityMatch(row1(0), row2(0), sim / simNormal)
//    }
  }


  def matchEntities(input1:String,input2:String,output:String,idcols:Array[String],threshold:Double,compAlg:String,spark:SparkSession)={
    val file1 = new File(input1)
    val file2 = new File(input2)
    var hashfile = input2
    var iterateFile = input1
    if (file1.length()<= input2.length()){ hashfile=input1;iterateFile=input2};

    val hashtable = LookupGenerator.buildHash(hashfile);
    val bcHash = spark.sparkContext.broadcast(hashtable)

    val secondDS = spark.sparkContext.textFile(iterateFile)
    val dups= secondDS.map(x=>{
      (LookupGenerator.getProbableMatches(x,bcHash.value), x)
      //{val slc = x.split("\\t");x.replace(slc(0),"")})
    }).filter(dup => dup._1.length>=1)

    val flattedDups:RDD[(String,String)] = dups.flatMap(row => row._1.map((_,row._2)))

    val dfB:RDD[(String,String)] = spark.sparkContext.textFile(hashfile).map(x=>{
      val slc = x.split("\\t")
      (slc(0),x)})

    val joined = dfB.join(flattedDups)

    val actualDups = joined.mapValues(x=>{val ds1=x._1.split("\\t"); val ds2=x._2.split("\\t");produceSimilarity(ds1,ds2,compAlg,threshold)})
    actualDups.map(x=>Array(x._2.id1,x._2.id1,x._2.similarity).mkString(",")).saveAsTextFile(output)
  }
}
