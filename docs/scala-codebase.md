# Mediachain Scala Codebase

This document serves as a guide to the Mediachain scala codebase.

## Sub-projects

Mediachain is set up as an
[sbt multi-project](http://www.scala-sbt.org/0.13/docs/Multi-Project.html),
allowing us to separate and reuse pieces of the project. There are six
sub-projects in our multi-project:

- `core`: Provides shared error types.
- `translation_engine`: Provides schema-translation capabilities enabling
  Mediachain to interact seamlessly with data in disparate schemas. Currently in
  its infancy.
- `transactor`: The beating heart of the project! The transactor provides a
  [copycat](https://github.com/atomix/copycat)-backed replicated state machine,
  allowing Mediachain to operate with global consensus. This project also
  includes a gRPC-backed facade for the transactor, so applications and services
  written in a variety of languages can interact with the transactors.
- `protocol`: Provides all of the gRPC IDL files. Additionally provides all of
  the datatypes used in the transactor and a framework for serializing
  to/deserializing from CBOR.

In this document, we will only be concerned with the `transactor` and `protocol`
sub-projects.

### Protocol

As mentioned, this project is largely responsible for defining datatypes and
gRPC services to be used throughout the transactor. The directory hierarchy is
as follows:

```
protocol/
├── README.md
└── src
    └── main
        ├── protobuf
        │   ├── Services.proto
        │   ├── Transactor.proto
        │   └── Types.proto
        └── scala
            └── io
                └── mediachain
                    ├── protocol
                    │   ├── CborSerialization.scala
                    │   ├── Datastore.scala
                    │   └── Transactor.scala
                    └── util
                        └── cbor
                            ├── CborAST.scala
                            ├── CborCodec.scala
                            └── JValueConversions.scala

9 directories, 10 files
```

The `src/main/protobuf` directory contains all service and data type definitions
for our gRPC services.

The `src/main/scala` directory contains a few key packages:

- `io.mediachain.protocol`: Provides a `CborSerialization` trait; a trait for
  defining key-value datastores (`Datastore`) which also defines many of the
  types used internally; and a few traits for components of the transactor,
  including the journal.
- `io.mediachain.util.cbor`: Defines some utilities for parsing from and
  rendering to CBOR. This is the backbone of the `CborSerialization` trait.
  
### Transactor

The transactor contains a bulk of the Mediachain logic as of yet. It is
responsible for maintaining consensus amongst Mediachain participants, as well
as providing a programmer-friendly interface for interacting with this service.
The directory hierarchy is as follows:

```
transactor/
├── README.md
└── src
    └── main
        └── scala
            └── io
                └── mediachain
                    ├── copycat
                    │   ├── Client.scala
                    │   ├── Dummies.scala
                    │   ├── Serializers.scala
                    │   ├── Server.scala
                    │   ├── StateMachine.scala
                    │   ├── TransactorService.scala
                    │   └── Transport.scala
                    ├── datastore
                    │   ├── BinaryDatastore.scala
                    │   ├── DynamoDatastore.scala
                    │   ├── PersistentDatastore.scala
                    │   └── RocksDatastore.scala
                    └── transactor
                        ├── Config.scala
                        ├── JournalCurrentBlock.scala
                        ├── JournalLookup.scala
                        ├── JournalServer.scala
                        ├── Main.scala
                        └── RpcService.scala

8 directories, 18 files
```
 
 This project features three packages, `io.mediachain.copycat`,
 `io.mediachain.datastore`, and `io.mediachain.transactor`.
 
 - `io.mediachain.copycat`: This package provides an implementation of a copycat
   `StateMachine`, responsible for maintaining consensus within Mediachain. It
   maintains the Mediachain journal/index, the individual object chains, and the
   Mediachain-wide blockchain, as described in the data structures
   documentation. Additionally, it provides the gRPC facade enabling external
   applications and systems to interact with the transactor via a simpler,
   cross-language interface.
- `io.mediachain.datastore`: This package provides implementations of the
  `Datastore` trait described in the `protocol` sub-project. These include
  datastores backed by [RocksDB](http://rocksdb.org/) and
  [DynamoDB](https://aws.amazon.com/dynamodb/).
- `io.mediachain.transactor`: This package provides a variety of main classes,
  each of which helps end-users accomplish different tasks. `Main.scala` is the
  preferred entrypoint--it offers a variety of command-line options for
  running different parts of the transactor.
