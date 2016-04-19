# RFC 2: Design Considerations for the Mediachain at Scale

Status: WIP

Author: vyzo

## Overview

This document is concerned with the Mediachain[1] architecture
as it grows from a small prototype to a large peer-to-peer network.

Specifically, we consider the system architecture and protocol
requirements in order to support the system as it organically
scales.
As such, we sketch a tiered architecture that can operate
both at small and large scale. Then we discuss protocols for
maintaining the Mediachain Journal in peer-to-peer fashion
and the fault tolerance characteristics of the system.

The starting point is the Mediachain Datastore as described in [2].

## A Tiered System Architecture

### Mediachain Peers and Clients

Conceptually we distinguish two types of nodes:
- tier-1 nodes or Mediachain _Peers_.
- tier-2 nodes or Mediachain _Clients_.

Peer nodes form the Mediachain _Core Network_ responsible for maintaining
the Mediachain Journal, which represents the canonical view of the Mediachain
datastore.
Furthermore, they serve clients with views of the journal and record
transactions on their behalf.

Client nodes are the users of the system. They can retrieve and track
the Journal through a Peer node, and they can perform reads and
writes to the datastore.

### Node Keys and Certificates

Each node in the network has a public-private key pair, with its
public key forming it identity.
Conceptually, the various keys form a tower of trust, connected through
certificates.

When the system is bootstrapped, a root key pair must be created which can
sign Level-0 certificates. These are certificates that grant signing
rights for principal entities that can admit Peer nodes to the network.
The key is used to generate such certificates for the principals of the
system and is otherwise always stored encrypted. The public part
is considered well-known and embedded directly in the software so that all
nodes can verify certificates.

Peer nodes require a Level-1 certificate for their keys. In order to
join the core network, a Peer node generates a key pair and asks
a Level-0 certificate holder to sign it a Level-1 certificate.
Thus, membership in core network is moderated for trusted organizations
and individuals.

In contrast, the network is open for all clients. A Client can generate
a key pair and have a Level-2 certificate signed automatically by a Peer
node. In order to reduce the attack surface for spammers, Proof of Work
must be required by a Client before a Peer will sign it a Level-2 certificate
which grants it access to the network.

### The Core Network

The Core Network is responsible for cooperatively maintaining and
updating the Journal.

In order to function, a number of Peer nodes
bootstrap a connected overlay network, and maintain it live by
exchanging regular heartbeats and peer identity messages.  Once booted,
Peer nodes are expected to be long-lived, with a lifespan of months
and longer except for intermittent failures.

The overlay can start fully connected with a small number of bootsrap
nodes, but over time relax the connectivity to an N-cut connected
model.  That is, with a suitable peer selection strategy, the network
can tolerate up to N peer failures without forming a
partition.  The peer identity exchange messages propagate the
constituency of the network, allowing nodes to learn of all other
nodes without a direct connection to each one of them.

New peers can join the network at any time: after obtaining a Level-1
certificate the new Peer fetches a public list of known tier-1 nodes
and connects to them. Upon connecting, it receives fresh list of peers
and reconfigures its connectivity. Then it proceeds to obtain the Journal
from other peers, synchronizes its state and starts serving clients.

### Client Interface

The function of the core network is to provide continuous views of the
Journal to clients and allow them to modify it by performing write
transactions.  In order to insert data to the Mediachain, a connected
Client first writes the requisite data directly to the datastore.
Given that IPFS is an open network, clients naturally maintain the
ability to write directly to the store.

However, in order to _persist_ its data in the Mediachain, the Client
must also update the Journal to reflect the new data. So after writing
to the datastore the Client creates a _Transaction_ consisting of
`JournalEntry` cells and submits it to a Peer node for merging
into the Journal.
In order to reduce the attack surface for spammers, the Client could
be required to submit a fresh Proof of Work together with the
transaction.
After submission, the Client awaits confirmation for its transaction
in the form of a view update.

## The Journal Blockchain

### The Journal as a Blockchain

At a fundamental level, the Journal is a sequence of `JournalEntries`
as described in [2]. In terms of storage and easy modification
however, the Journal is actually a sequence of chained blocks, each containing
one or more `JournalEntry` cells forming a block of concurrent transactions:

```
Block = {
 "type" : "block"
 "chain" : <Reference>
 "transactions" : [<JournalEntry> ...]
 "signatures" : <Signatures>
 }
```

The blockchain can be easily stored in the IPFS datastore, thus solving the
problem of Journal distribution and persistence. A reference to the last block
in the store is sufficient to boot a new node that reconstructs an up-to-date
index of the mediachain.

### The Genesis Block

At birth the datastore contains an initial dataset from public sources,
together with a single block of `JournalEntry` records that represent
the Genesis block of the Journal. This initial journal is stored in IPFS
and mirrored by all Peers forming the bootstrap core network.

### Extending the Blockchain

The blockchain is extended every time a client requests to commit a
write transaction. The core network may initially be quiescent, with
peers exchanging heartbeat messages. As soon as a transaction is
requested by a client, the receiving peer verifies it before
proceeding with the Block Commit Protocol.

The transaction consists of a sequence of `JournalEntries`, which
can be either an `CanonicalEntry` or a `ChainEntry`.
In order to verify a `CanonicalEntry`, the Peer handling the
transaction verifies that the canonical exists in the IPFS store and
it is not already in the Journal index.
In order to verify a `ChainEntry`, the Peer verifies that the canonical
references exist in the store, that the `ChainEntry.ref` reference points
to a canonical for an entity or artefact, and that the new chain is
consistent with the current chain head in the index.

Thus, `ChainEntry` verification requires an extension field in the base
structure defined in [2]:
```
ChainEntry = {
 "type" : "update"
 "ref"  : <Reference>
 "chain" : <Reference>
 "chainLink" : <Reference> ; reference to the old chain head
 "timestamp" : <Timestamp>
 }
```

After the Peer verifies the new transaction, it signs and broadcasts
it to its immediate peers in the core network and proceeds with the
Block Commit protocol.

### Block Commit Protocol

The protocol is initiated every time there is a new write transaction,
unless there is a commit already in progress. The protocol prescribes
an interval during which concurrent transactions are merged together
to form a new block. During the wait interval each Peer collects and
broadcasts new transactions by its clients and other peer nodes in the
core network.

At the end of the wait interval, a Peer node must be designated to commit
the block. This can be accomplished with a randomized Leader Election
algorithm similar to the Raft consensus protocol [3].
Alternatively, a Proof of Work mechanism can be used, similar to
bitcoin, where all Peers content on mining their currently accumulated
transaction block. The advantage of a Proof of Work altorithm is that it
fortifies the protocol against certain adversarial failures, albeit at
an energy cost.

Once a commiter has been designated, the Peer proceeds with writing the
block to the store and propagates the block canonical reference to all
its peers and clients. The new block is in turn broadcast by its peers to the
entire core network and out to all connected clients.
In the case of competing commits, either because of peer selection algorithm
or some pathology, one of the commits can be chosen deterministically.
The other commits can then be merged by replaying their differential transactions
on top of the selected commit.

### Transaction Merging and Conflict Resolution

In order to create a block, concurrent transactions must be merged together.
Merging is not simple concatenation however, as one or more transactions may
_conflict_.

Recall that client initiated transactions may contain `CanonicalEntry`
or `ChainEntry` cells. Two `CanonicalEntries` with the same canonical
reference are not in conflict, because the canonical points to a
single object in the store. If for some reason they refer to the same
object, they can be reduced to a single `CanonicalEntry`

Two `ChainEntries` however can conflict if they reference the same
canonical with a different chain pointer, unless the chainLink pointer
can be chained between the two.

In order to resolve the conflict, one of the two `ChainEntries` must
be deterministically chosen as the base entry. The other entry can be
reconciled by constructing a new chain head on top of the first
transaction containing a link to the competing chain head.
Traversing the chain link would fetch a reference to the metadata
contained in the abandoned head, thus preserving the transaction
metadata.

Supporting this operation requires a modification in the `ChainCell`
data models for entity and artefact chains in [2]:
```
EntityChainCell =
 ...
 | <EntityChainLinkCell> 

EntityChainLinkCell = {
 "type" : "entityChainLink"
 "chain" : <Reference>
 "entity" : <Reference>
 "link" : <Reference>
 "signatures" : <Signatures>
 }

ArtefactChainCell =
 ...
 | <ArtefactChainLinkCell>

ArtefactChainLinkCell = {
 "type" : "artefactChainLink"
 "chain" : <Reference>
 "artefact" : <Reference>
 "link" : <Reference>
 "signatures" " <Signatures>
 }
```

Chain link cells can conflict with updates or other link cells.
The conflict resolution proceeds in a similar manner, by selecting a base
and consing a link to the abandoned chain head.

## Fault Tolerance

TBD

## References

1. [The Mediachain Blog](https://blog.mine.nyc/)
2. [Mediachain RFC1: The Mediachain Datastore](https://github.com/mediachain/mediachain/blob/master/rfc/mediachain-rfc-1.md)
3. [The Raft Consensus Algorithm](https://raft.github.io)
