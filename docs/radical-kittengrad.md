# Radical Kittengrad: Beyond Consensus

DRAFT

## The Mediachain as a Blockchain

In our approach so far we have treated the Mediachain as a Blockchain.
The blockchain is maintained by the transactors by consensus, and it
provides an ordered view of all operations (insert or update) in the
mediachain.

The attraction of a blockchain is that it facilitates Open Data and
simplifies indexing and syndication. Indeed, by publishing the
blockchain and storing the metadata on IPFS, anyone can retrieve the
entire mediachain. Similarly, syndication is simplified by streaming
the blockchain, which allows processors to index the mediachain
online.

However, the blockchain comes at a price: it requires a complicated
block commit protocol and transaction cost semantics. And once you add
financial incentives to the system, you also need to provision for
Byzantine Fault Tolerance. And neither PoS nor PoW schemes are
attractive for our system; there is nothing at stake to implement PoS,
and implementing PoW is energy-hungry with a baseline cost of
operations and lots of complexity.

## Inverting the Blockchain

What if we inverted the point of view?

We don't really care about ordering operations, and we want to avoid
the complexity of consensus. What we would like to preserve is the
open data and online syndication properties of the blockchain.

So let's drop the requirement of global consensus and instead focus on
individual Peers -- and let's stop calling them Transactors while at
it.  We modify the data model so that updates no longer carry a chain
pointer.  In this view, the mediachain is a map of canonicals to sets
of updates; the sets can be ordered with an approximate timestamp, but
there is no total order per se and updates commute.

The role of Mediachain Peers has not changed: they are responsible for
storing and updating the mediachain. But they don't have to store the
entire mediachain or provide a total order of operations. This allows
us to partition the canonical space among peers and scale
horizontally.

In order to partition the canonical space, we utilize Consistent
Hashing of Peer identities, in the form of public keys. This creates
an n-bit modular field, where n is number of bits in the hash output.
Each Peer identity represents a point in the circle and each Peer is
responsible for storing and updating canonicals closest to it.  This
is the datastore design utilized by Dynamo, while routing can be
performed with the Chord algorithm.

In order to replicate the mediachain and add fault tolerance, we
utilize CFS-style replication where the space of each Peer is
replicated by its adjacent peers. This provides a minimum 3x
replication factor; larger replication factors can be achieved with
more hops.

## Mediachain Operations

The primary Mediachain operations are insert, update, and lookup.
In terms of interface, only lookup changes -- instead of returning a
chain pointer, it returns a set of reference to all updates.

In terms of implementation however, all operations need to be routed
to a Peer that handles the canonical reference. Operations can be
routed to any replica; replicas share updates and synchronize. The map
of sets is a CRDT data structure, so no need to impose global order.

If an operation is applied to a Peer that does not handle the
particular canonical refered by the operation, then it fails.
Clients are responsible for routing to the correct Peer, so a
routing support interface will be provided. 

## Overlay Dynamics

Over time, the overlay network constructed by the Peers will
change. New Peers will be added to accomodate an increase in scale,
while other Peers may depart either by failure or decomissioning.

Overlay maintenance is straightforward.

In order to join, a new Peer is be placed between its two closest
Peers in the Key space. The Peer then proceeds to build its slice of
the Mediachain, by retrieving its share of the data by its adjacent
Peers.

In order for a Peer to leave the overlay, we perform the inverse operation.
The canonical space corresponding to the departing is now split between its
two adjacent Peers. The Peers already have the data due to replication, but
they need to start replicating their new share of the canonical space further
to their adjacent Peers.

Each Peer maintains a Journal (oplog) containing all the operations it has applied
or replicated. The Journal is structured in blocks linked together through hashing,
and timestamped with ntp-synchronized clocks. This Journal structure as a blockchain
allows individual Peers to externalize its share of the mediachain.

The Journal allows individual Peers to synchronize their state and recover from
transient failures or restarts. A Peer can temporarily go offline, and when it rejoins
the network it need only receive new journal blocks from its adjacent Peers.

## Streaming, Aggregation and Online Indexing

The Journal also allows Peers to stream their operations to interested clients.
We distinguish two classes of clients that are particularly important for the
system interface: Aggregators and Indexers.

Aggregators subscribe to journal streams from multiple Peers, and aggregate them
together to emit a composite view of the mediachain. In order to obtain a complete
view, an aggregator needs to subscribe to streams from ceil(N/R) Peers, where r
is the replication factor (3 by default).

Indexers receive streams of Journal updates and retrieve associated data from IPFS for
ingestion. Indexer implementation may poll Peers, act as aggregators or utilize the
services of an aggregator for online operation.

## Namespaces

This so far pertains to a flat namespace, but the design allows us to trivially
implement multiple namespaces. Each namespace has its own key space, implemented
as a separate overlay and served by a different set of Peers. Note that individual
Peers may participate in multiple overlays serving different namespaces.
