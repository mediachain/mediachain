# RFC 1: The Mediachain Datastore

Status: DRAFT

Author: [vyzo](https://github.com/vyzo)

## Overview

The Mediachain[1] is a decentralized system designed for tracking
metadata related to Creative Works and Internet Media.
It is being developed as an Open Source, Open Data system with
decentralized stakeholders.

This document serves as a  specification of the Mediachain data
model and the shape of the distributed store at the heart of
system.
It is intended to provide a stable basis for bootstrapping the
system from a small-scale proof of concept to a fully distributed,
Open Data Peer-to-Peer network.

## The Mediachain Data Model

### Data Objects

The Mediachain is stored in a distributed append-only datastore with
content addressable access. This maps well to the IPFS[2] peer-to-peer
network, which names and accesses data using the SHA256 hash of
the content. Objects stored in the network are immutable and persistent;
their hashes become their _Canonical_ identifiers through which they can
be referenced.

Mediachain objects follow the IPLD[3] data model,
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
 ["key" : <Reference>]
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
 "_1" : {
  "entity" : <Reference>
  "signature" : <IPRSSignature>
 }
 "_2" : ...
 ...
 }

IPRSSignature = {
 "value" : <Reference>
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

The entity chain also allows for linking two entities, for example
having a pseudonymous artist publicly revealing a true name that
points to another entity in the system.

The artefact chain further stores links to entities that
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
EntityChainCell =
   <EntityUpdateCell>
 | <EntityLinkCell>
 | <Nil>

EntityUpdateCell = {
 "type" : "entityUpdate"
 "chain" : <Reference>
 "entity" : <Reference>
 "signatures" : <Signatures>
  <Key> : <Value> ... ; metadata updates
 }

EntityLinkCell = {
 "type" : "entityLink"
 "chain" : <Reference>
 "entity" : <Reference>
 "entityLink" : <Reference>
 ["relationship" : <Reference>]
 "signatures" : <Signatures>
 <Key> : <Value> ... ; entity relationship metadata
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
 ["entity" : <Reference>]
 ["url" : <URL>]
 "signatures" : <Signatures>
 <Key> : <Value> ... ; reference metadata
  }
```

## Indexing and Querying the Mediachain

### The Journal

Mediachain data are persistent and available through the IPFS network.
However they are not _discoverable_ without the help of an index which
maintains references to entities, artefacts and chain heads.

In order to make the mediachain indexable and discoverable we need to
maintain a _Journal_ of updates to the data store. The journal is
very similar to a blockchain in that it acts as a public ledger of all
Mediachain data transactions in the system. By replaying the journal
and fetching the data from IPFS, any node in the Internet can bootstrap
an index that allows it to read and query the Mediachain.

### Appending Data in the Mediachain

The datastore initially is almost empty, containing only the `Nil` object
as the bottom of all chains 
For each entity and artefact added to the store, a corresponding entry
is added to the journal, connecting a canonical reference with its chain
pointing to the `Nil` object.
Similarly, for every cell added to a chain, a corresponding entry is
added to the journal, updating the head of the chain for a canonical
reference.

Thus the journal contents can be described as a sequence of entries
with the following schema:
```
Journal = <JournalEntry> ...

JournalEntry =
 <CanonicalEntry>
 <ChainEntry>

CanonicalEntry = {
 "type" = "insert"
 "ref"  = <Reference> ; canonical reference for an entity or artefact
 }
 
ChainEntry = {
 "type"  = "update"
 "ref"   = <Reference> ; canonical reference for entity or artefact
 "chain" = <Reference> ; head of chain for canonical reference
 }
```

### Journal Maintenance

The journal is the critical piece of metadata that connects the
Mediachain datastore.  So far we have made no mention of how to
maintain the Journal in a distributed fashion.
This is purposeful: the scope of this specification is limited to
describing the primitives of the Mediachain store in sufficient detail
to allow bootstrap and Open Data access.

The assumption is that during the system bootstrap and scaling, a
principal entity acts as a gatekeeper responsible for the development
of the system and maintenance of the journal.  When the system has
been scaled out enough to warrant distribution of the write a load, a
separate specification will address the requisite distributed
protocols for journal maintenance and distribution.

## An Example Mediachain

As an example, consider the situation described in [4].
In that case, a graphic artist created a GIF to commemorate the
passing of well known actor and musician David Bowie. The GIF
quickly went viral, mutating along the way, and resulting at a
complete loss of attribution for the cultural afterfact.

The Mediachain is designed to solve this kind of problems by
tracking the propagation and evolution of the artefact in the
datastore.
Thus, following the viral propagation as described in [4] up
to retrofuture's creation getting instagramed would result
to the following records in the datastore:
```
Qm000... = Nil

QmAAA... = Entity {
 "type" : "entity"
 "name" : "Hellen Green"
 "key"  : {"@link" : "QmAAABBB..."}
 "platform" : "~cargocollective"
 "cargocollective_user" : "+hellengreen"
 "signatures" : {...}
}

QmBBB... = Artefact {
 "type" : "artefact"
 "name" : "Time May Change Me"
 "created" : "01/20/2016"
 "url" : "http://helengreenillustration.com/Time-May-Change-Me"
 "data" : {"@link" = "QMBBBCCC..."} ; blob in IPFS
 "signatures" : {...}
 }

QmCCC... = ArtefactCreationCell {
 "type" : "artefactCreatedBy"
 "chain" : {"@link" : Qm000...}
 "artefact" : {"@link" : QmBBB...}
 "entity" : {"@link" : QmAAA...}
 "comment" : "I made this!"
 "signatures" : {...}
 }

QmDDD... = Entity {
 "type" : "entity"
 "name" : "Eva Tolkin"
 "key" : {"@link" : "QmDDDBBB..."}
 "platform" : "~pinterest"
 "pinterest_user" : "+evatolkin"
 "signatures" : {...}
 }

QmEEE... = ArtefactReferenceCell {
 "type" : "artifactReferencedBy"
 "chain" : {"@link" : "QmCCC..."}
 "artefact" : {"@link" : "QmBBB..."}
 "entity" : {"@link" : "QmDDD..."}
 "url" : "https://www.pinterest.com/pin/..."
 "comment" : "I pinned this!"
 "signatures" : {...}
 }

QmFFF... = Entity {
 "type" : "entity"
 "name" : "retrofuture"
 "key" : {"@link" : "QmFFFBBB..."}
 "platform" : "~tumblr"
 "tumblr_user" : "+retrofuture"
 "signatures" : {...}
 }

QmGGG... = Artefact {
 "type" : "artefact"
 "name" : "Cool!"
 "created" : "01/20/2016"
 "url" : "http://retrofuture.tumblr.com/cool.gif"
 "data" : {"@link" : "QMGGGCCC..."} ; blob in IPFS
 "signatures" : {...}
 }

QmHHH... = ArtefactCreationCell {
 "type" : "artefactCreatedBy"
 "chain" : {"@link" : "Qm000..."}
 "artefact" : {"@link" : "QmGGG..."}
 "entity" : {"@link" : "QmFFF..."}
 "comment" : "I created this!"
 "signatures" : {...}
 }

QmIII... = ArtefactDerivationCell {
 "type" : "artefactDerivedBy"
 "chain" : {"@link" : "QmHHH..."}
 "artefact" : {"@link" : "QmGGG..."}
 "artefactOrigin" : {"@link" : "QmBBB..."}
 "comment" : "I derived it from +hellengreen's GIF.!"
 "signatures" : {...}
 }

QmJJJ... = Entity {
 "type" : "entity"
 "name" : "christina99"
 "key" : {"@link" : "QmJJJBBB..."}
 "platform" : "~instagram"
 "instagram_user" : "+christina99"
 "signatures" : {...}
 }

QmKKK... = ArtefactReferenceCell {
 "type" : "artifactReferencedBy"
 "chain" : {"@link" : "QmIII..."}
 "artefact" : {"@link" : "QmGGG..."}
 "entity" : {"@link" : "QmJJJ..."}
 "url" : "https://instagram.com/..."
 "comment" : "I posted this!"
 "signatures" : {...}
 }
```

The rest of the propagation would add references to retrofuture's
artefact chain. The evolution of the GIF would be traced from
retrofuture's gif chain to hellegreen's original through the
`ArtefactDerivationCell`.

## References

1. [The Mediachain Blog](https://blog.mine.nyc/)
2. [IPFS](https://ipfs.io/)
3. [IPLD](https://github.com/ipfs/specs/tree/master/ipld)
4. [The GIF that Fell to Earth](https://blog.mine.nyc/the-gif-that-fell-to-earth-eae706c72f1f)
