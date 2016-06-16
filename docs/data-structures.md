# Mediachain Data Architecture Review

While Mediachain's sophisticated and descriptive data model is one of its
biggest assets, its complexity can also make it difficult for new developers to
wrap their heads around. This document aims to explain Mediachain's data
architecture in order to simplify the process of contributing to and extending
Mediachain.

## Design Considerations

Before diving into details regarding Mediachain's data model, we must address
the considerations made during the design process.

Mediachain was designed to be built over [IPFS](https://ipfs.io). As a result,
all objects in Mediachain are content-addressed. This imposes a few constraints:

- All objects are immutable
- All chains must be implemented as reverse-linked lists, as is the case with
  Bitcoin
- All persisted objects must serialize to CBOR

Mediachain was designed to be permissionless, leveraging cryptographic
signatures as a method for determining provenance of data. All objects must
serialize deterministically so they can be hashed, signed, and verified in a
consistent manner.

## Object Types

### Artefacts

From our first RFC:

```
Artefact = {
 "type" : "artefact"
 ["data" : <Reference>]
 "signatures" : <Signatures>
 "meta" : {
  "name" : <String>
  ["created" : <Date>]
  ["description" : <String>]
  <Key> : <Value> ... ; artefact metadata
  }
 ["metaSource": <Reference>]
 }
```

Generally, all of our data types reserve top level properties for
Mediachain-specific attributes, e.g. references to other objects, cryptographic
signatures, etc. The `meta` field contains a sub-object wherein the actual
metadata is meant to live, so that our internal schema can grow safely, without
risk of colliding with attributes required by the system.

We use Protocol Labs' `multihash` self-describing hashing scheme for hashing the
contents of our objects. IPFS uses an object's `multihash` as its address within
the system--all objects are retrievable by their hash. IPFS also features a
built in faculty for linking objects by their content-addresses called IPLD.
Under IPLD, IPFS users may express traversals over arbitrary graphs of linked
objects via a pathing markup very similar to that of HTTP. Links are encoded
using a special property name/sub-object structure:

```
{myLink: {@link: <reference>}}
```

If this object had a hash of <foobar>, one could access the object it linkes to
by looking up the path `/ipfs/<foobar>/myLink`. Following this, all instances of
`<Reference>` in our first RFC correspond to objects of this structure.

### Entities

Entities bear a nearly identical structure:

```
Entity = {
 "type" : "entity"
 ["keychain" : <Reference>]
 "signatures" : <Signatures>
 "meta" : {
  "name" : <String>
  <Key> : <Value> ... ; entity metadata
  }
 ["metaSource": <Reference>]
 }
```

The only discrepancy between the two would be the expected `meta` fields,
"created" and "description".

## Chain Structures

Mediachain is comprised of an unbounded number of chains--reverse linked
lists--that represent the complete history of revisions of entities and
artefacts managed by the Mediachain. There are are two classes of chains, the
system-wide blockchain and the individual entity/artefact revision chains.

### Entity/Artefact Chains

When a new object is inserted into Mediachain for the first time, an object
following the schema detailed above is written to IPFS. The address of that
initial submission is what is referred to as its canonical identifier--this will
never change. All revisions to and extensions of this metadata are then written
into a distinct chain, indexed by this canonical identifier. The updates in this
chain are serialized via the consensus layer. Each update is signed by its
author, so that users can ignore updates authored by peers they don't know or
trust.

The heads of these individual chains are stored in the index, which is
serialized out to a journal much like that of a filesystem. Maintaining and
tracking this journal is the responsibility of the transactors. More on the
journal later!

### Blockchain

Whereas the individual chains in Mediachain are somewhat unique to its design,
Mediachain also features a blockchain that stores batches of updates in blocks
as one would expect. The purpose of this blockchain is to facilitate a simpler,
more efficient "catch-up" for new nodes joining the system. By adjusting the
block size to include multiple updates per block, applications wishing to ingest
the Mediachain history can do so with fewer queries.

## The Journal

As was mentioned just a moment ago, the transactors maintain an
index--syndicated in a journal--of all of the latest chain heads in Mediachain.
This takes the relatively simple form of a map where the keys are canonical
identifiers of objects in the system and the value is the IPFS `multihash`
address of the current chain head. By keeping this journal synchronized via
consensus, we avoid chain splitting, wherein two parties submit a new revision
to an update chain simultaneously, resulting in a situation where each party
believes their update is the head revision.

## Scala Implementations of Data Types

The data structures we've discussed in this document can all be found in
`Datastore.scala`, located in the `protocol` sub-project. These are the
representations used internally. They all implement a trait for CBOR
serialization and deserialization. This CBOR serialization is what we send over
the wire in our gRPC interface.

The gRPC services and data types, defined in `Transactor.proto`, contain `bytes`
fields (gRPC byte strings), all of which will contain serialized CBOR, to be
deserialized client-side.
