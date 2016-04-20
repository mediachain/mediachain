# RFC 2: Design Considerations for the Mediachain at Scale

Status: DRAFT

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
updating the Journal, and providing data persistence for datastore
records.

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
 "entries" : [<JournalEntry> ...]
 "signatures" : <Signatures>
 }
```

The blockchain can be easily stored in the IPFS datastore, thus solving the
problem of Journal distribution and persistence. For additional integrity
protection and disaster recovery backups, peer nodes may elect to persist
the blockchain in an outside backing store like S3. 

A reference to the last block in the store is sufficient to boot a new
node that reconstructs an up-to-date index of the mediachain.

### The Genesis Block

At birth the datastore contains an initial dataset from public sources,
together with a single `Block` of transactions and a chain reference to `Nil`.
This Genesis block describes the bootstrap datastore and is the first block
in the Journal blockchain when the core network becomes operational.

### Extending the Blockchain

The blockchain is extended every time a client requests to commit a
write transaction. The core network may initially be quiescent, with
peers exchanging heartbeat messages. When a transaction is
requested by a client, the receiving peer verifies it and then
proceeds with the Block Commit Protocol.

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
 "chain" : <Reference>     ; referece to the new chain head
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

Another aspect of the commit would be to persist the blobs pointed by
the transactions being commited. Small records (less than 1KB) can be
stored directly in the DHT, but larger ones will need to be persisted
by the core network.

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
canonical with an incompatible chain pointer. They are not conflicting
if they can be linearized and chaining on top of one another, in which
case the `.chainLink` of one of the entries would point to the `.chain`
field of the other.

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
 "chain" : <Reference>      ; reference to tail of chain
 "chainMerge" : <Reference>  ; reference to merged orphan head
 "entity" : <Reference>
 "signatures" : <Signatures>
 }

ArtefactChainCell =
 ...
 | <ArtefactChainLinkCell>

ArtefactChainLinkCell = {
 "type" : "artefactChainLink"
 "chain" : <Reference>     ; reference to tail of chain
 "chainMerge" : <Reference> ; reference to merged orphan head
 "artefact" : <Reference>
 "signatures" " <Signatures>
 }
```

In addition, in order to track the conflicting update transactions 
and allow peers and clients to discern a merged chain we also need to 
extend the `JournalEntry` to include a `ChainMergeEntry`:
```
JournalEntry =
 ...
 | <ChainMergeEntry>

ChainMergeEntry = {
 "type" : "chainMerge"
 "ref"  : <Reference>
 "chain" : <Reference>      ; reference to the head of the chain
 "chainLink" : <Reference>  ; reference to the previous head of the chain
 "chainMerge" : <Reference> ; reference to merged orphan chain head
 } 
```

The following example shows a merge of two conflicting `ChainEntries`:
```
Qm001... = ChainEntry {
 "chain" : "QmAAA..."
 "chainLink" : "QmCCC..."
 ...
 }

Qm002... = ChainEntry {
 "chain" : "QmBBB..."
 "chainLink" : "QmCCC..."
 ...
 }

QmAAA... = ArtefactChainCell {
 "chain" : "QmCCC..."
 ...
 }

QmBBB... = ArtefactChainCell {
 "chain" : "QmCCC..."
 ...
 }

QmCCC... = ArtefactChainCell {
 "chain" : ...
}
```

When the conflicting `ChainEntries` for an artefact chain are merged
the result is a `ChainEntry` and a `ChainMergeEntry` in the Journal, and a 
new `ArtefactChainLinkCell` as the head of the chain:
```
Qm001 = ChainEntry {
 "chain" : "QmAAA..."
 "chainLink" : "QmCCC..."
 ...
 }

Qm003 = ChainMergeEntry {
 "chain" : "QmDDD..."
 "chainLink" : "QmAAA..."
 "chaiMerge" : "QmCCC..."
 ...
 }

QmDDD = ArtefactChainLinkCell {
 "chain" : "QmAAA..."
 "chainMerge" : "QmBBB..."
 ...
 }
```

It should be noted that a `ChainMergeEntry` can also conflict with a
`ChainEntry` when they refer to the same canonical but with a
conflicting `chainLink`; the conflict resolution can proceed similar
to `ChainEntry` conflicts.

## Fault Tolerance

### Failure Modes

Failure and churn is an unavoidable aspect of peer-to-peer system operating
in the internet. In general, we classify possible failure modes in
two types:
- Common Failures, such as node crashes and transient connectivity problems.
- Byzantine Failures, where nodes exhibit arbitrary behavior.

Common failures are expected with some mean time between failures.
They can be correlated, leading to connectivity problems within the core
network. In some occasions, crashes and internet-wide connectivity
problems can avalanche leading to core network paritions.

On the other hand, byzantine behaviors can appear because of software
bugs or adversarial actions. Bugs may cause nodes to send or suppress
arbitrary messages. Even worse, nodes exhibiting adversarial behavior
may try to poison or disrupt the system with malicious intent.
Our threat model is not concerned with state actors, but rather 
simply acknowledges common internet threats, where attacker-controled 
nodes can become adversarial to the system.

### Common Failures

In general, client crashes are immaterial to the core network.
Once the data have reached IPFS and a transaction has been inserted
to the core network, the originating client can crash without
affecting the commit.

On the other hand, all system protocols must be designed to accomodate
crashes at inopportune moments. Peers exchange regular heartbeat
messages, which allow nodes to health-check each other and the 
current membership to be propagated throughout the network.
Similarly, the block commit protocol must be fortified with 
reasonable timeouts so that commits can be restarted on leader
failures. 

To make the effects of crashes clearer, let's consider all the logical
steps in the Mediachain write protocol:
```
client -> ipfs: datastore write
client -> peer:  push transaction
peer -> peer: block commit protocol
peer -> client: commited block
client: confirm transaction
``` 

With each step in the protocol fortified for crash recovery, we
can be reasonably certain that once the client has written the data
and pushed a transaction, it will be persisted in the Mediachain.
The N-cut connectivity property of the core network allows the
system to withstand random failures without partitioning.
Transactions propagate using peer-to-peer broadcast, ensuring that
the integrity of the blockchain in the absence of severe network
events.

### Network Partitions

During the lifetime of the system, there will be inevitable severe
network events where correlated failures can parition the core
network.  In the face of network paritions, the CAP theorem dictactes
that the blockchain will either be Consistent or Available.

Common consenus protocols in the literature choose consistency in such
cases. This is an appropriate response for a cluster of tightly
controlled machines, but inappropriate for a decentralized system.
Hence, the Mediachain should be designed to be partition tolerant with
a Partition Healing Protocol.

In effect, a partition causes isolated islands in the core
network to fork the blockchain into two or more competing chains.
The partition healing protocol must merge these competing blockchains
once connectivity is reestablished.

The protocol can proceed by choosing the longest chain, breaking ties
with a deterministic scheme so that all nodes can make the same
choices independently.  Once choosing the new primary blockchain, the
orphaned chains can be merged by replaying necessary transactions and
rerunning the block commit protocol.

### Byzantine Failures

#### Bug or Malice?

Invariably, the system will experience bugs or malicious behavior with
arbitrary failure characteristics. We consider cases where the
adversarial process (be it bug, murphy or a threat actor) controls a
number of nodes in the system.

In general, the adversarial process is observable to the system
through message exchange. An adversarial action can be
a message injection, a broadcast message suppression or an arbitrary
long combination of the two.

#### Message Suppression Attacks

The peer-to-peer broadcast of transactions in the core network routes
through multiple paths. As such, it can withstand a number of
nodes trying to deny or disrupt the service by suppressing
broadcast messages. 

At worst, clients of adversarial nodes will be denied service.
Affected clients can easily recover by connecting to another Peer for
service. If too many nodes are dropping transactions so as to affect
the system as a whole, the disruption would be big enough for a metric
event visible to system monitoring. At which point, intervention
by tier-1 node operators can restore the problem,.

#### Message Injection Attacks

At a base level, protocol implementation can reject injection of all
obviously invalid messages that do not conform to rpc signatures.
Nodes injecting such messages can be immediately disconnected and be
considered failed. In the following we are concerned with the effects
of well-formed messages that can affect protocol behavior at the
algorithmic level.

We can further characterize such injections as poisoning
and flooding attacks. In a poisoning attack, the adversarial
process attempts to inject messages that can corrupt the state
of the Mediachain. In a flooding attack, the adversarial
process attempts to flood the network with messages that
hijack blockchain algorithms and disrupt service.

#### Client Injection Attacks

The network is open to all clients, thus we are more concerned
about malicious behavior than genuine bugs in client activity.

A client can attempt a poisoning attack by injecting invalid
transactions. Such transactions may refer to records not written
to the datastore or be duplicates of older transactions, etc.
The system can be rendered robust against transaction poisoning
attacks by means of transaction verification. All peers verify
client transactions before broadcasting them to the core network.

One or more clients can also attempt a flooding attack where they try
to overload the store and core network by pushing a flood of
transactions.  Such actions can be rendered ineffective by requiring
Proof of Work to be associated with every transaction.  In addition,
there should be a limit to the size of blobs acceptable blobs as part
of the transaction in order to avoid core network storage exhaustion
by such attacks. 

If the system is under too much write stress, additional measures can
be taken: difficulty for Proof of Work can be adjusted, CAPTCHAs may
be presented to clients, IP blacklisting etc.

#### Peer Injection Attacks

A Peer can attempt to poison the blockchain by injecting invalid
transactions with dangling pointers. Similary it can attempt
to flood the system with duplicate transactions. Both scenarios
can be simply defended by requiring peers to verify all transactions
as new, not just client transactions. 

A variation of the poisoning attack is for a peer to attempt to commit
invalid blocks, containing invalid journal entries or extending an
arbitrary blockchain. In order to be robust against such cases, peers
must also verify commited blocks before accepting them as the new
blockchain pointer.

Another flooding attack scenario is for a peer to attempt to 
generate a flood of valid transactions without originating
them from a client. This scenario is mitigated by the requirement
for all transactions to require Proof of Work regardless of origin.
Thus, a peer is naturally limited to its flooding rate by hash
power requirements.

A more subtle flooding attack is for a peer to attempt to hijack
the Leader Election protocol and become a perpetual commiter.
This is impossible to mitigate with a pure randomized Leader
Election protocol -- it can only be mitigated by using a Proof of 
Work competition for leader election. 

Similarly, a peer node may attempt to build a trivially long blockchain
to needlessly trigger partition healing protocols. These
attacks can be mitigated by treating a block as a transaction, and thus
requiring Proof of Work by the commiting node before accepting it.

However, given that core network membership is moderated by signing
Level-1 certificates, problems of this nature should be treated as
major events and be detectable by system monitoring processes.
This type of attack is indicative of either a serious software bug or
a possible compromise of the misbehaving nodes, in which case operator
intervention is required for resolution and key revocation.

### Scaling Limits

The system architecture described so far should not have problems
scaling to large number of nodes. Principals and organizations can
easily add more peers to the core network accomodating the
demands of client nodes as the system grows.

There is however on potential scaling bottleneck that can lead the
protocols to fail. This is the storage requirements for the blockchain
and the journal index. Peer nodes need access to the index to verify
transactions and the blockchain must be available for peers and clients
joining the network.

Running a peer node should not have exorbitant hardware requirements:
it should be possible to run a peer node with a medium instance from
public cloud providers. With a back of the envelope calculation, the
index should require some 100 bytes of storage for each canonical to
chain head mapping. The blockchain could easily be 10 times
larger, assuming an average of 10 updates per index head. Thus, a
a billion entities and artefacts in the Mediachain would require about
1TB of storage; this is readily available in today's public clouds.

A larger scale blockchain and index can be accomodated by sharding.
If the storage requirements become to large for a commonly available
cloud instance to handle the entire blockchain, the canonical space
can be sharded using the canonical identifier bits. In this scenario,
a different blockchain would be maintained for every shard, thus
allowing the core network to scale horizontally.

## References

1. [The Mediachain Blog](https://blog.mine.nyc/)
2. [Mediachain RFC1: The Mediachain Datastore](https://github.com/mediachain/mediachain/blob/master/rfc/mediachain-rfc-1.md)
3. [The Raft Consensus Algorithm](https://raft.github.io)
