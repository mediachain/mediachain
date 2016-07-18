## Installing the Mediachain Services

While the [public testnet](testnet.md) is a great resource for developers looking
to understand the system and experiment with client apps, it's also possible to
run the mediachain services on your own hardware.  This is a good way to get
a deeper understanding of how the components interact, and/or set up a development
environment if you're interested in contributing to the core protocol and network design.

### Transactor
If you'd like to run your own testnet, you'll need
to compile the main [mediachain project](https://github.com/mediachain/mediachain),
which contains the Transactor server, the RPC facade that clients will use to
connect, and an RPC service that mediates access to DynamoDB.

The mediachain transactor and related services are written in Scala and use
[sbt](http://www.scala-sbt.org/) for project management.  While you don't need
to have sbt installed on the machines that are running the services, you will
need to [install sbt](http://www.scala-sbt.org/download.html) to compile the
project into an executable jar file.

Once sbt is installed, clone the [mediachain project](https://github.com/mediachain/mediachain)
if you haven't already, and change your directory to the project root.

#### Compiling the transactor jar

The simplest way to deploy and run the transactor services is to compile a
"fat jar" that bundles the mediachain code with all of its runtime dependencies.
In the project root directory, run:


```bash
$ sbt transactor/assembly
```

The first time you run sbt, you may have to wait a while for it to update and
download the scala dependencies it needs to manage the project.  This will only
happen once per-machine, and subsequent sbt commands will run much faster.

After running the `sbt transactor/assembly` command, you should see a lot of
console output with something similar to this near the end:
```
[info] SHA-1: 09ac655306fc13c06f9d652b48dec90a2109587e
[info] Packaging /home/yusef/mediachain/transactor/target/scala-2.11/transactor-assembly.jar ...
[info] Done packaging.
[success] Total time: 75 s, completed Jul 18, 2016 4:48:38 PM
```

Take note of the file path to the `transactor-assembly.jar` file.  That file is
what we'll use to run the services in the subsequent commands.  If you want,
you can leave it in place, or copy it to a remote machine or a safe place on
your filesystem.

 For the following examples, we'll set the `TRANSACTOR_JAR` environment variable
 to the path to the `transactor-assembly.jar`:

 ```bash
 $ export TRANSACTOR_JAR=/path/to/transactor-assembly.jar
 ```

 Please note that the actual file path will be different on your machine!  Use
 whatever path was printed out in the `sbt transactor/assembly` command.

#### Configuration

The transactor services require a configuration file, which is in the
Java property file format.  The [transactor README](https://github.com/mediachain/mediachain/tree/master/transactor#transactor-configuration)
has details on the required and optional configuration keys and their expected
values.  You can combine the configuration variables for the transactor and
the RPC facade into one file, and pass it into all commands below.

For the examples, let's set the `TRANSACTOR_CONFIG` environment variable
to the path to the config file:

```bash
$ export TRANSACTOR_CONFIG=/path/to/config-file.properties
```

##### AWS configuration
This phase of the transactor uses a "mock IPFS" implemented with Amazon DynamoDB.
You can either [use a local DynamoDB service](https://github.com/mediachain/mediachain/tree/master/transactor#local-dynamodb-setup),
or provision a paid DynamoDB service with AWS.  Either way, the transactor
services will use the default AWS credential discovery mechanism to authenticate.

Before running the services below, you should either install the [aws cli tool](https://github.com/aws/aws-cli) and
run `aws configure` to enter your AWS credentials, or ensure that the `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`
environment variables are correctly set in your environment.

One caveat when running the local DynamoDB service: if you do not pass the `-sharedDb` flag when starting the service,
you _must_ use the same credentials when accessing the service as you used when [creating the mediachain tables](https://github.com/mediachain/mediachain/tree/master/transactor#prepare-dynamo-db-tables).
If you _do_ run with `-sharedDb`, you can use any credentials when accessing the service.

#### Running the services

##### The Transactor Journal cluster

The main transactor servers can be run with the following command
(assuming you set the `TRANSACTOR_JAR` and `TRANSACTOR_CONFIG` environment
variables as described above):

```bash
$ java -cp $TRANSACTOR_JAR io.mediachain.transactor.JournalServer $TRANSACTOR_CONFIG
```

That will start the initial "bootstrap" node of the transactor cluster, which
will listen on the the address and port specified in the configuration file.

To start subsequent nodes, pass in the location of previously-started transactor
server, using the `address:port` format.  For example, to connect to the
bootstrap server at `localhost:10000`, run:

```bash
$ java -cp $TRANSACTOR_JAR io.mediachain.transactor.JournalServer $TRANSACTOR_CONFIG localhost:10000
```

You can pass multiple addresses if you have already started multiple servers.

##### The RPC facade
To support a wide variety of clients and abstract out the implementation details
of the transactor network, clients communicate with the cluster using an RPC
"facade" service.

To start the RPC service:

```bash
$ java -cp $TRANSACTOR_JAR io.mediachain.transactor.RpcService $TRANSACTOR_CONFIG <journal-server-address>
```

Make sure to replace `<journal-server-address>` with the location of a running
transactor server, in `address:port` format.


##### The Datastore RPC service
The DynamoDB datastore is made accessible to clients with an RPC service that
exposes a simple `put`/`get` interface.  To run it:

```bash
$ java -cp $TRANSACTOR_JAR io.mediachain.transactor.DatastoreRpcService $TRANSACTOR_CONFIG
```

#### Stopping the services

The services can be cleanly shutdown using killfiles.  For each service,
a special directory is created and watched for changes.  If a file named
`shutdown` is created, or it's modification time is updated, the service
will shut itself down.

The location of the control directory depends on the values set in the config
file.  Each service has its own control directory, set with the following
config properties:

- `RpcService`: `io.mediachain.transactor.rpc.control`
- `DatastoreRpcService`: `io.mediachain.datastore.rpc.control`
- `JournalServer`:
  - The value of `io.mediachain.transactor.server.rootdir` + `/ctl/JournalServer`

So, if you've set `io.mediachain.transactor.rpc.control` to, e.g.
`/var/run/mediachain/ctl/RpcService`, you can shut it down with

```bash
$ touch /var/run/mediachain/ctl/RpcService/shutdown
```


The `JournalServer` also has a `leave` command, in addition to `shutdown`.
By `touch`ing the `leave` file, you will cause the server to leave the cluster
without shutting down completely.