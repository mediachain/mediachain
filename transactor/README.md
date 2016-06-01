# mediachain-transactor

Consensus/journal cluster server for the mediachain network. To run a test node:

* `brew install dynamodb-local && brew services start dynamodb-local`
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
```
io.mediachain.transactor.server.rootdir: /path/to/transactor-directory
io.mediachain.transactor.server.address: 127.0.0.1:10000
io.mediachain.transactor.dynamo.awscreds.access: $AWSACCESS
io.mediachain.transactor.dynamo.awscreds.secret: $AWSSECRET
io.mediachain.transactor.dynamo.baseTable: Test
io.mediachain.transactor.dynamo.endpoint: http://localhost:8000
```

If you want to enable SSL, add the following to the config file:
```
io.mediachain.transactor.ssl.enabled: true
io.mediachain.transactor.ssl.keyStorePath: /path/to/keystore.jks
io.mediachain.transactor.ssl.keyStorePassword: $KEYSTOREPASS
io.mediachain.transactor.ssl.keyStoreKeyPassword: $KEYSTOREKEYPASS
```

* `sbt transactor/assembly`
* `scala -cp $BIG_JAR_FROM_ABOVE io.mediachain.transactor.JournalServer path/to/config.conf`

# Keystore - OpenSSL Mini HOWTO
In order to use SSL with copycat, we need to use java keystores.
Java comes with a key management tool called `keytool`, but you probably want to be
using OpenSSL for creating keys and managing certificates.

Here are some quick instructions for generating java keystores with `openssl` and `keytool`:
```
# Create a root key to use as CA
openssl req -new -x509 -extensions v3_ca -keyout root.key -out root.crt -days 1001

# Create a server key and certificate
openssl req -new -nodes -out server.csr -keyout server.key -days 365 
openssl x509 -req -in server.csr -out server.crt -CA root.crt -CAkey root.key -CAcreateserial

# Create a java keystore server.jks
openssl pkcs12 -export -in server.crt -inkey server.key \
        -certfile root.crt -name server -out server.p12 \
        -password pass:changeme

keytool -importkeystore \
        -deststorepass changeme -destkeypass changeme -destkeystore server.jks \
        -srckeystore server.p12 -srcstoretype PKCS12 -srcstorepass changeme \
        -alias server
keytool -importcert -noprompt -file root.crt -alias root -keystore server.jks\
        -storepass changeme
```


