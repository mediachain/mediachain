# mediachain-transactor

Consensus/journal cluster server for the mediachain network. To run a test node:

* Install local DynamoDB http://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBLocal.html
* Prep DynamoDB tables
```scala
import com.amazonaws.auth.BasicAWSCredentials
val awscreds = new BasicAWSCredentials($AWSACCESS, $AWSSECRET)
​
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import scala.collection.JavaConversions._
import com.amazonaws.services.dynamodbv2.model._
​
val dynamo = new AmazonDynamoDBClient(awscreds)
dynamo.setEndpoint("http://localhost:8000")
​
val mainTableAttrs = List(new AttributeDefinition("multihash", "S"))
val mainTableSchema = List(new KeySchemaElement("multihash", KeyType.HASH))
val pth = new ProvisionedThroughput(10, 10)
dynamo.createTable(mainTableAttrs, "Test", mainTableSchema, pth)
​
val chunkTableAttrs = List(new AttributeDefinition("chunkId", "S"))
val chunkTableSchema = List(new KeySchemaElement("chunkId", KeyType.HASH))
dynamo.createTable(chunkTableAttrs, "TestChunks", chunkTableSchema, pth)
```
* Create config file
```scala
io.mediachain.transactor.server.rootdir: /tmp/transactor-test/tx0
io.mediachain.transactor.server.address: 127.0.0.1:10000
io.mediachain.transactor.dynamo.awscreds.access: $AWSACCESS
io.mediachain.transactor.dynamo.awscreds.secret: $AWSSECRET
io.mediachain.transactor.dynamo.baseTable: Test
io.mediachain.transactor.dynamo.endpoint: http://localhost:8000
```
* sbt transactor/assembly
* `scala -cp $BIG_JAR_FROM_ABOVE io.mediachain.transactor.JournalServer path/to/config.conf`
