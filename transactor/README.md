Transactors provide read/write access to the Mediachain Journal and syndication.

# How to Run a test transactor node

## Compile the transactor code
```
sbt transactor/assembly
```
This will leave a fat jar in transactor/target/scala-2.11/, which we call
the `BIGJAR`

## Local DynamoDB setup

The current transactor implementation uses DynamoDB as the datastore in place
of IPFS (at least until IPLD is rolled out).

For testing you probably want to use the local implementation of DynamoDB.
See [https://aws.amazon.com/blogs/aws/dynamodb-local-for-desktop-development/](DynamoDB Local)

On a Mac you can install it with homebrew:
```
$ brew install dynamodb-local && brew services start dynamodb-local
```

## Prepare dynamo db tables
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

## Run a transactor server
To run a transactor server:
```
java -cp $BIGJAR io.mediachain.transactor.Main transactor -c <config-file> cluster-address ...
```

To run the RPC facade:
```
java -cp $BIGJAR io.mediachain.transactor.Main facade -c <config-file> cluster-address ...
```

Both servers can be shutdown by signalling a kill file. The control
directory is set in the configuration file; the transactor control directory
is at `${io.mediachain.transactor.server.rootdir}/ctl`, while the rpc service
control directory is `io.mediachain.transactor.rpc.control`.

### transactor configuration
The configuration is a properties file.

Required properties:
```
io.mediachain.transactor.server.rootdir: /path/to/transactor-dir # Server root directory
io.mediachain.transactor.server.address: 127.0.0.1:10000         # Server bind address
io.mediachain.transactor.dynamo.baseTable: Test                  # DynamoDB Table
io.mediachain.transactor.dynamo.endpoint: http://localhost:8000  # Optional
```

Optionally, if you want to enable SSL, add the following to the config file:
```
io.mediachain.transactor.ssl.enabled: true
io.mediachain.transactor.ssl.keyStorePath: /path/to/keystore.jks
io.mediachain.transactor.ssl.keyStorePassword: $KEYSTOREPASS
io.mediachain.transactor.ssl.keyStoreKeyPassword: $KEYSTOREKEYPASS
```

### RPC facade configuration
Required properties:
```
io.mediachain.transactor.rpc.port: 10001                         # RPC Port
io.mediachain.transactor.rpc.control: /path/to/rpc/killfile-dir  # Control directory
io.mediachain.transactor.dynamo.baseTable: Test                  # DynamoDB Table
io.mediachain.transactor.dynamo.endpoint: http://localhost:8000  # Optional
```

With optional SSL support configured the same way as the transactor.

# Keystore - OpenSSL Mini HOWTO
In order to use SSL with copycat, we need to use java keystores.
Java comes with a key management tool called `keytool`, but you probably want to be
using OpenSSL for creating keys and managing certificates.

Here are some quick instructions for generating java keystores with `openssl` and `keytool`:
```
# Create a root key and certificate to use as CA
openssl req -new -x509 -extensions v3_ca -keyout root.key -out root.crt -days 1001

# Create a server key and certificate
openssl req -new -nodes -out server.csr -keyout server.key -days 365 
openssl x509 -req -in server.csr -out server.crt -CA root.crt -CAkey root.key -CAcreateserial

# Create a java keystore for the server (server.jks)
openssl pkcs12 -export -in server.crt -inkey server.key \
        -certfile root.crt -name server -out server.p12 \
        -password pass:changeme

keytool -importkeystore \
        -deststorepass changeme -destkeypass changeme -destkeystore server.jks \
        -srckeystore server.p12 -srcstoretype PKCS12 -srcstorepass changeme \
        -alias server
keytool -importcert -noprompt -file root.crt -alias root -keystore server.jks \
        -storepass changeme
```


