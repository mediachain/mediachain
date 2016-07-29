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
the worklog transactions compute an aggregate index, the working index.

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

### Steady State Operation

TBD

### Block Commit Protocol

TBD



