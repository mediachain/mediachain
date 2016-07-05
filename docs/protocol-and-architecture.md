# Mediachain -- Protocol and Architecture

In this document, we'll review the distributed architecture of Mediachain and
describe the protocols and interfaces that connect the various pieces.

Conceptually, Mediachain is broken into three core pieces: the **transactors**,
responsible for maintaining global consensus regarding the
[journal](data-structures.md#the-journal) (to be described); the **clients**
that stream all operations (writes, updates, etc.) from the transactor quorum in
order to process them and/or provide unique projections of the dataset; and the
**storage layer** (at present DynamoDB, soon IPFS) responsible for housing the
blockchains in a publicly accessible manner.

## Transactors

The transactor quorum is currently implemented as a
[copycat](https://github.com/atomix/copycat) state machine. `copycat`
features its own serialization library and RPC techniques, so Mediachain
features a facade layer--implemented in [gRPC](http://grpc.io)--providing
API support for multiple languages.

### Facade

The facade, written in Scala, communicates to the `copycat` quorum via
`copycat`'s (JVM-only) API so end-users don't have to. Furthermore, the facade
can interact with the **storage layer** to facilitate common requests:

- **Stream blockchain updates**: Clients that have either disconnected from the
  **transactor quorum** or are connecting for the first time can catch up to the
  current state of the blockchain. Once subscribed to the facade, an application
  will receive a stream of all new blocks as they are generated. If a client
  needs to catch up on older entries, they can traverse the blockchain backwards
  until they reach the last block they had read.
- **Insert new artefacts/entities**: For convenience, applications can submit
  artefacts and entities through the facade. The facade will handle basic tasks:
  - Searching an index to ensure there isn't already a canonical entry for this
    artefact/entity present in Mediachain.
  - Inserting the canonical entry into the **storage layer**.
  - Submitting the canonical entry to the **transactor quorum** to be added to
    the blockchain.
- **Updating new artefacts/entities**: Applications may modify entries or
  establish new links (i.e. entity authored an artefact).
- **Lookup chain heads for entities/artefacts**: Applications can access the
  current head of individual asset chains for artefacts/entities in the system.
  
Note that the facade, in its current implementation, directly accesses the
decentralized **storage layer** on behalf of the user. At first blush, this
seems contradictory to our decentralization goals. The facade was designed to be
a lightweight proxy between application programmers and Mediachain. In fact,
it's totally acceptable to have a facade instance colocated with your
application. Other applications may prefer fewer, centralized facade instances
shared by a pool of applications instead. It's quite likely that Mediachain Labs
will host facade instances of its own for public consumption.

### Quorum Details

The `copycat` replicated state machine implements the
[Raft](https://raft.github.io/) distributed consensus algorithm. Raft, unlike
Bitcoin's consensus algorithm or
[Stellar](https://www.stellar.org/papers/stellar-consensus-protocol.pdf), does
not support quorum splits and merges--there can only be one quorum per state
machine, requiring 100% participation from all quorum members. New members may
join at any time.

## Clients

The clients form the most interesting tier of Mediachain. Clients are
third-party and services that stay tightly synchronized with the Mediachain
blockchains, but process the dataset in unique and interesting ways. A great
example of a Client in the Mediachain ecosystem is our
[indexer](https://github.com/mediachain/mediachain-indexer). Our indexer
monitors all insertions and updates to the Mediachain, indexing all canonical
entries based on their text-based metadata fields as well as the media itself
(if accessible.) This empowers compelling application-level APIs such as
reverse-image lookup and various metadata-based searches.

Clients empower infinite extensibility of the Mediachain, allowing third parties
to create completely new interfaces for acting with our global metadata
database.

Clients interact with the **transactors** via the **facade**, primarily through
its streaming API. The indexer, for example, receives a callback every time a
new entry is added or an existing entry is modified and updates its index
accordingly. Another concept we've explored is git-tree style storage of a given
entry's revision history.

## Storage Layer

The storage layer is a publicly accessible, independently scalable,
content-addressed key-value store. Mediachain aims to deploy atop
[IPFS](https://ipfs.io), a sprawling project looking to enable a distributed and
persistent web. For the time being, while is IPFS landing a few more features
we'd like, we're deploying over a publicly accessible instance of Amazon's
[DynamoDB](https://aws.amazon.com/dynamodb/).
