# The Kittengrad Transactor Network

WIP DRAFT

## Introduction

In this draft we discuss transactor network architecture and protocols for the
next phase in the development of the Mediachain. The goal is to provide a baseline
implemention that can scale to 100MM artefacts and beyond as we further develop and
decentralize the system.

The low level Mediachain architecture as it has evolved so far has two main components:
the Transactor network, responsible for performing reads and writes to the
mediachain, and the Datastore, responsible for storing mediachain data.
Mediachain clients interact with the transactors through an rpc service interfaces.

In the first phase, the transactor network was implemented as a Raft cluster
using the copycat framework, providing a synchronous strongly linearized
view of the mediachain. This was appropriate for prototyping purposes, but not
applicable to the decentralized peer-to-peer architecture envisioned for the
mediachain at scale.

The Kittengrad architecture replaces the Copycat cluster with a peer-to-peer
network of transactors, who independently accept and propagate transactions
and synchronize with the generation of new blocks.
This architecture no longer provides a synchronous view of the mediachain journal.
Instead transactors initiate asynchronous transactions on behalf of clients,
with eventual commit in the blockchain through a Block Commit Protocol.

## Transactor Interface

The transactor interface for clients is implemented through a standard rpc protocol
(GRPC) with the following interface:

```
interface Transactor
 insertCanonical(data): (TransactionId, CanonicalReference)
 updateChain(CanonicalReference, data): TransactionId
 lookupChain(CanonicalReference): ChainReference
 currentBlockchain(): BlockReference
 journalStream(): stream BlockReference
```

The read operations (`lookupChain`, `currentBlockchain`) are served locally by
transactors. The write operations (`insertCanonical`, `updateChain`) initiate
transactions that are completed asynchronously with confirmation streamed through
the `journalStream` interface.

When the transactor has built a backlog of transactions, it may
reject an operation as temporarily unavailable. The client is expected to
backoff and retry with a randomized exponential backoff or similar mechanism.

The transactor will see through the completion of accepted transactions on a best
effort basis, which in the absence of transactor failure should result in the eventual
commit of an accepted transaction.
The client may wait to confirm a transaction through a streamed block,
and potentially retry transactions aborted because of transactor failure.

The interface changes from the v0.1 interface are related to the asynchronous nature
of write operations. Previously, the operations returned a commit receipt in the
form of a `JournalEntry` immediately.
Now they return a transaction id, which will be included in some `JournalEntry` in
a future block streamed through the `journalStream` interface.
The stream interface itself changes to only emit commited block events instead
of individual entries.

## Transactor Implementation

### Network Primitives

Transactors form a peer-to-peer network with moderated membership. The network
manages membership and provides a broadcast primitive used for propagating
transactions and protocol messages to all transactors.

In a small-scale network, the broadcast primitive can be implemented as direct
multicast, with each transactor fully connected to all other transactors.
In a larger partially-connected network, broadcast can be implemented through
a p2p gossip protocol.

### Transactor State

Each transactor maintains a local copy of the blockchain on disk, and a pointer
to the last commited block. The transactor also maintains on disk the mediachain
index which corresponds to the blockchain, mapping canonical to chain references.
The index is used for creating update transactions and providing read access
to the mediachain.

In addition to transactions commited in the blockchain, each transactor
maintains a worklog of pending transactions. The blockchain index together with
the worklog compute an aggregate index, the working index.

Read requests are serviced by the working index, while write operations are
initiated on the working index and completed through the transaction protocol.

### Transaction Protocol
#### Transaction Initiation

In order to perform a write operation, the transactor checks the validity
of the operation against the working index. Duplicate inserts are rejected,
as are updates for unknown canonicals. It also checks its worklog backlog,
against some safety threshold.

The transactor then generates a transaction id and adds the data to the
datastore and the transaction to the worklog. It then brodcasts the
transaction to the network, and returns the transaction id to the client.

If the transaction is the first one in the worklog, the transactor sets
a timer that ensures a block confirming the transaction will eventually
be commited with the Block Commit Protocol.
If the worklog size exceeds the maximum block size, and there is no
commit in progress, the transactor initiates the Block Commit Protocol
in order to commit some transactions off its worklog.

#### Transaction Propagation

Each transactor receiving a transaction, validates it against its
working index and, if valid, adds it to the worklog. This ensures
that each transactor has a valid working index from which to generate
a block. Conflicting writes will result in invalid transactions, which
will be rejected and not added to any block proposed by the rejecting
transactor during block commit.

#### Transaction Commit

When the Block Commit Protocol completes, it results into a block
ready for commit. The block is scanned for confirming pending transactions,
which are removed from the worklog.

The block is stored and becomes the current blockchain pointer, while
the blockchain index is updated in place. The worklog of remaining pending
transactions is then revalidated against the blockchain index.
Invalid transactions are rejected, and it is the responsibility of the originating
transactor to replay them.

In this way, the block commit provides a conflict resolution mechanism
for concurrent updates. Inserts may use a deterministic transaction id,
based on the canonical reference, which allows merging and confirmation
of duplicate inserts without a replay. Updates however are dependent
on the previous chain pointer, which requires a new datastore write
and replay of the transaction.

### Block Commit Protocol

The objective of the block commit protocol is to establish consensus
among transactors for the next block. In the long term, we want to
utilize a PoW or PoS system that is robust to byzantine failures
(eg tendermint). At this stage of development however, there is nothing
at stake; we simply want a simple and robust protocol that can scale to some
100MM artefacts. 

So we treat the Block Commit Protocol as a pluggable module and we call
the baseline implementation BCP0.

The BCP0 protocol operates in rounds, with each round having three phases:

1. A Proposal phase, where one or more new blocks are proposed, validated and propagated by transactors
2. A voting phase, where transactors vote for the next block
3. A commit phase, where a block is commited.

#### Block Proposal

During the block proposal phase, one or more transactors with stake in the
form of ownership of pending transactios,  generate a block and brodcast a
signed block proposal together with the block data.

Upon receiving a block proposal, a transactor that has not
yet proposed a block can validate and endorse the block by broadcasting
a signed endorsement message.

The proposal phase ends for a transactor once it has received proposals or
endorsements by all other transactors. Transactors that have failed to propose
or endorse within a reasonable timeout, are considered failed and ignored
for the rest of the round. This is a local decision that is made independently
by each transactor.

#### Voting

Once the proposal phase has finished, each transactor chooses a valid block and votes
for it.

The choice should be deterministic, so that all correct transactors should
make the same decision in the absence of network failures:
- If there is only one block proposed, the choice is obvious.
- If there are more, then the transactor votes for the block with the highest index (most transactions).
- If two blocks have the same height, then the transactor chooses by comparing block hashes.

The transactor then broadcasts a Vote message, indicating the chosen block and awaits
to hear from other live transactors. Once again, transactors that are not heard
of within a time limit are considered failed for the rest of the round.

#### Block Commit

Each transactor tallies the votes, and if the majority of transactors that were part
of the round have voted for the same block, the transactor goes forward and commits
the block to disk.
The transactor then broadcasts a commit message and waits to hear
from the live majority before ending the protocol once enough commit
messages are received. 

If there are not enough votes for a block, then there must be some failed nodes.
The transactor broadcasts an abort message and enters a reconfiguration
phase where transactors are probed for liveness. Once the network membership
has been re-established, the transactors start another protocol round from
the proposal phase.

The protocol ends when enough commit messages have been received.
If there is no majority commit at the end, the protocol again re-establishes
network membership are starts another round. Partial failures that don't
affect block commit are also handled by network reconfiguration, but
this time in the background.

As an optimization, it may be possible to elide synchronous commit
confirmation and immediately commit the block. Commit messages can
then be collected in the background for failure detection purposes.
However, this may widen the possibility of blockchain divergence
between transactors in the presence of partial failures, which must
be reconciled with the blockchain merging approaches discussed later.

### Steady State Operation

When the system is operating at steady state, it is constantly under
some write load.  While the system is commiting a block with the block
commit protocol, it is also accumulating newer transactions that will
need to be commited in a subsequent block.

Once a block has been commited, transactors examine their resulting
worklog, which is the queue built up while the block was being
commited.  Depending on the load, this may be less a full block, in
which case the next block commit execution follows the timer of the
first pending transaction.

Under higher write load however, this will exceed the block size,
necessitating an immediate re-execution of the block commit
protocol. In order to minimze contention in block proposal, which
consumes bandwidth for block propagation, we want to utilize a
randomized wait period before a transactor initiates block commit by
proposing a new block. On the same time we want to minimize the time
it takes for the first proposed block, which initiates protocol
action.  We can modulate the delay by determining the wait period with
a distribution dependent on transactor stake. In this way, transactors
with many pending transactions are more likely to propose a block
quickly. If a transactor has a dominant stake, a viable strategy would
also have it initiate protocol without waiting at all.

Ultimately, if the write load is too heavy for the block commit
pipeline to keep up, transactors will hit their backlog limit. When
this occurs, transactors will start to reject transactions as
temporarily unavailable and initiate client back off retries.

The scaling envelope for the performance of the system in the absence
of failures is dominated by the block commit time. We observed peak
performance of 1 block of 512 write ops per second with the copycat
prototype with an m3.large cluster.  It is not an unreasonable design
goal to expect similar or better performance with the baseline block
commit protocol.

### Delayed Writes

So far we have developed the protocol using strict writes: transactors
immediately write to the datastore and propagate transactions that
refer to objects by references. This is an optimistic approach to
concurrency, as it assumes little contention from concurrent writes,
which are then resolved by transaction invalidation.

An alternative approach is to treat updates as writes into partially
ordered sets. There is no total order for updates, but each transactor's
natural order is preserved. In effect, we treat the working index as
a CRDT data structure mapping canonicals to posets of updates. The updates
commute as long as each transactor's order of transaction is preserved.

To utilize the CRDT approach, transactors delay writes to the
datastore until block proposal, and hence don't include
chain pointers in transaction data. This allows the protocol to absorb
arbitrary concurrent transaction contention in a block, without
invalidating any.

The downside is that transaction object data must be propagated in
the network, increasing bandwidth cost and protocol latency. It also
whitens and complicates the block commit protocol interface, as it
must know handle data writes.

### Failure Recovery and Blockchain Merging

At this phase we are concerned with common failure scenarios:
- transactor crash and recovery, where a transactor fails and later recovers to rejoin the network.
- transient network failures, where a transactor becomes temporarily unreachable.
- network parititions, where the transactor network splits into two or more components that independently process transactions for a time.

The common characteristic of failures that last for non-trivial
amounts of time, is that the blockchain diverges among
transactors. When transactors recover and rejoin the network, they
must synchronize their blockchain with the network.

In the more common case of a node crash or transient network
issues, the failed transactors will simply fall behind in the
blockchain. On recovery they can synchronize by fast-forwarding the
missing blocks.

In the case of a network partition, the blockchains of the various
components will diverge from some common ancestor. On recovery, the
network will merge the blockchains by selecting the higher blockchain
and replaying the shorter ones from the point of divergence, resolving
ties by hash comparison. Transaction playback can follow through the
block commit protocol pipeline by queueing all the transactions of the
shorter blockchains into the worklog.

[TBD: More detail in merge protocol]
