package io.mediachain.datastore

object DynamoLocalTableMaker {
  def main(args: Array[String]): Unit = {
    val tableNameOpt = args.lift(0)
    val endpointOpt = args.lift(1).orElse(Some("http://localhost:8000"))

    println("setting up local dynamodb tables")
    DynamoTableMaker.makeTables("", "", tableNameOpt, endpointOpt)
  }
}

object DynamoTableMaker {
  def makeTables(
    accessKey: String,
    secretKey: String,
    baseTableName: Option[String] = None,
    endpoint: Option[String] = None,
    readThroughput: Long = 10,
    writeThroughput: Long = 10
  )
  : Unit = {
    import com.amazonaws.auth.BasicAWSCredentials
    val awscreds = new BasicAWSCredentials(accessKey, secretKey)

    import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
    val dynamo = endpoint match {
      case Some(endpointUrl) =>
        new AmazonDynamoDBClient(awscreds)
          .withEndpoint(endpointUrl)
          .asInstanceOf[AmazonDynamoDBClient]
      case _ =>
        new AmazonDynamoDBClient(awscreds)
    }

    import com.amazonaws.services.dynamodbv2.model._

    import scala.collection.JavaConversions._

    val baseTable = baseTableName.getOrElse("Mediachain")
    val chunkTable = baseTable + "Chunks"
    val mainTableAttrs = List(new AttributeDefinition("multihash", "S"))
    val mainTableSchema = List(new KeySchemaElement("multihash", KeyType.HASH))
    val pth = new ProvisionedThroughput(readThroughput, writeThroughput)
    dynamo.createTable(mainTableAttrs, baseTable, mainTableSchema, pth)

    val chunkTableAttrs = List(new AttributeDefinition("chunkId", "S"))
    val chunkTableSchema = List(new KeySchemaElement("chunkId", KeyType.HASH))
    dynamo.createTable(chunkTableAttrs, chunkTable, chunkTableSchema, pth)
  }

}
