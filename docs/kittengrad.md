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
form of pending transactios,  generate a block and brodcast a signed block proposal
together with the block data.

Upon receiving a block proposal, a transactor that has not
yet proposed a block can validate and endorse the block by broadcasting
a signed endorsement message.

The proposal phase ends for a transactor once it has received proposals or
endorsements by all other transactors. Transactors that have failed to propose
or endorse within a reasonable timeout, are considered failed and ignored
for the rest of the round. This is a local decision that is made independently
by each transactor.

#### Voting

Once the proposal phase has finished, each node chooses a valid block and votes
for it.

The choice should be deterministic, so that all correct transactors should
make the same decision in the absence of network failures:
- If there is only one block proposed, the choice is obvious.
- If there are more, then the transactor votes for the block with the higest index.
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

### Steady State Operation

TBD


### Delayed Writes

TBD

