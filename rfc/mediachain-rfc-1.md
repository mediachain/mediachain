# RFC 1: The Mediachain Datastore

Status: WIP

Author: @vyzo

## Overview

## The Mediachain Data Model

### Data Objects

The Mediachain is stored in a distributed append-only datastore with
content addressable access. This maps well to the IPFS peer-to-peer
network, which names and accesses data using the SHA256 hash of
the content. Objects stored in the network are immutable and persistent;
their hashes become their _Canonical_ identifiers through which they can
be referenced.

Mediachain objects follow the IPLD[*] data model,
allowing for storage of key-valued pairs where keys
are strings and values are other key-valued pairs or strings.

Thus we have:
```
DataObject = {
 "type" : <String>
 <Key> : <Value>  ...
 }

Key   = <String>
Value = <String> | <CompoundValue>
CompoundValue = {
 <Key> : <Value> ...
 }

```
All Mediachain data objects have a mandatory `type` field that represents
the data type of the object.
Links can be embedded between data objects by using the IPLD `@link`
special key, allowing us to construct Merkle DAGs.

### Entities and Artefacts

There are two principal types of objects in the mediachain:
entities and artefacts. An _Entity_ represents a person or organization
who may associate with artefacts, for example by creating or posting
a reference to the artefact. _Artefacts_ represent media works as tracked
by the Mediachain. They can be images, video, text, or any other
common media found in the Internet. 

In the Mediachain schema, entities are instances of data objects with
their type field set to `entity` and a mandatory `name` field.
Artefacts are instances of data objects with their type field set
to `artefact`, a mandatory name and optional `description` and creation
date fields.

Artefacts can also have associated their data stored in IPFS, so that media
can be directly accessed from references to their Canonicals.
If this is the case, then the artefact object will contain a link to
the IPFS datablob in its `data` field

Finally, both types carry a set of cryptographic signatures that assert their
validity. The signatures come from _signatories_ with known keys in the
system; any entity that contains a public key in its data can act
as a signatory.

We can make things more concrete with the following schema:
```
Entity = {
 "type" : "entity"
 "name" : <String>
 [key : <PublicKey>]
 "signatures" : <Signatures>
 <Key> : <Value> ... ; entity metadata
 }

Artefact = {
 "type" : "artefact"
 "name" : <String>
 ["created" : <Date>]
 ["description" : <String>]
 ["data" : <Reference>]
 "signatures" : <Signatures>
 <Key> : <Value> ... ; artefact metadata
 }

Reference = {
 "@link" = <Canonical>
 }

Signatures = {
 "type" : "signatures"
 <SignatoryCanonical> : <Blob> ...
 }
```

### Chains and Links

The fundamental operation of the Mediachain is to link entities and
artefacts with labeled relationships and metadata.  The data model
described so far is disconnected: it contains independent entities and
artefacts. In order to track the evolving relationships between
entities and artefacts we need to construct chains of updates for each
entity and artefact in the datastore.

The natural way to model chains in a distributed append-only store is
through linked lists. Thus for each entity and artefact in the
system, we also have an associated chain which initially points to the
canonical of a global `Nil` object:
```
Chain = <ChainCell> | <Nil>
ChainCell = {
 "type" : ...
 "chain" : <Reference>
 <Key> : <Value> ...
 }
Nil = { "type" : "nil" }
```

The entity and artefact chains differ on the type of relationships
they store. They both support metadata update cells, which simply
provide new metadata to the base object as arbitray key-value pairs.
However, the artefact chain further stores links to entities that
represent creation and potential Intellectual Property rights,
references in the Internet media-space, and derivative relations
to other artefacts.
Each chain cell carries also carries one or more signatures by
signatory entities in the system.

Note that the types of cells that can appear in either type of
chain it is not limited to the types of relationships described
above. These are merely the minimum schema to support the
functionality of the Mediachain.

Keeping this in mind, we can model the chains with the following
schema:
```
EntityChainCell = <EntityUpdateCell> | <Nil>

EntityUpdateCell = {
 "type" : "entityUpdate"
 "chain" : <Reference>
 "entity" : <Reference>
 "signatures" : <Signatures>
  <Key> : <Value> ... ; metadata updates
 }

ArtefactChainCell =
   <ArtefactUpdateCell>
 | <ArtefactCreationCell>
 | <ArtefactDerivationCell>
 | <ArtefactOwnershipCell>
 | <ArtefactReferenceCell>
 | <Nil>
 
ArtefactUpdateCell = {
 "type" : "artefactUpdate"
 "chain" : <Reference>
 "artefact" : <Reference>
 "signatures" : <Signatures>
 <Key> : <Value> ... ; metadata updates
 }

ArtefactCreationCell = {
 "type" : "artefactCreatedBy"
 "chain" : <Reference>
 "artefact" : <Reference>
 "entity" : <Reference>
 "signatures" : <Signatures>
 <Key> : <Value> ... ; creation metadata
 }

ArtefactDerivationCell = {
 "type" : "artefactDerivedBy"
 "chain" : <Reference>
 "artefact" : <Reference>
 "artefactOrigin" : <Reference>
 "signatures" : <Signatures>
 <Key> : <Value> ... ; creation metadata
 }

ArtefactOwnershipCell = {
 "type" : "artefactRightsOwnedBy"
 "chain" : <Reference>
 "artefact" : <Reference>
 "entity" : <Reference>
 "signatures" : <Signatures>
 <Key> : <Value> ... ; IP ownership metadata
 }

ArtefactReferenceCell = {
 "type" : "artefactReferencedBy"
 "chain" : <Reference>
 "artefact" : <Reference>
 "url" : <URL>
 <Key> : <Value> ... ; reference metadata
```

## Mediachain Data Access

## An Example Mediachain

## References
