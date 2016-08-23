### Peer roles

Peers can play several roles in the network, accordinging to their
capabilities and resources they are willing to contribute. They can
play multiple roles simultaneously, and assume different rolesets for
each namespace they are following.

#### Metadata Sources

Sources are the primary actors that ingest metadata into the system:
they range from casual contributors to authoritative sources of large
datasets. Sources have publishing rights for one or more namespaces,
and can push updates casually, in bulk or in firehose fashion.

Casual sources typically connect irregularly to the network in order
to publish new statements and synchronize their local state for their
namespaces. They can represent individual contributors or sources with
small but evolving datasets.

Authoritative sources already own large swaths of metadata, which they
make available by publishing in the mediachain.  This oftentimes takes
the form of a large metadata dump, which they push in the network when
they first come online.

Firehoses provide a constant stream of updates to the system. Typically
this is the byproduct of an external system which generates media
objects with a high rate, such as a social media platform. Firehoses
are probably not interested in receiving updates from the system at
all, so they can be classified as pure writers.

#### Publishers

Publishers are stable, connected peers who have publishing permissions
for some namespaces. As such, they can accept and publish statements
on behalf of other peers and clients, according to their own standards
of valid behavior.

Because of their stability, they enroll and relay in pubsub overlays
for their namespaces. Thus they provide write anchors for sources to
quickly propagate statements in the network.

#### Caches

Caches are stable peers who provide read anchors into the mediachain.
They follow some namespaces by enrolling as leaves in the relevant
pubsub overlays, keep local copies of published statements, and seed
associated metadata.

They have finite storage capacity, but for low volume namespaces it
should be sufficient to store and seed a complete copy of all
published statements. For higher volume namespaces, they can only
store and seed the most recent updates.

Caches are intended to help clients and other peers bootstrap and
synchronize state. They also provide a direct read interface for the
database by supporting basic queries on their local store. They don't
need publishing permissions, so they can be classified as pure
readers.

#### Aggregators

Aggregators are peers who occupy interior points in the namespace
hierarchy and aggregate together child namespaces.  Their primary
purpose is to provide bandwidth and relay services for pubsub
overlays. As such, they enroll in child namespace overlays and relay
published statements in an aggregate overlay. Per course of operation,
they also cache recent statements in the aggregate for polling
clients.

The services of aggregators are essential for implementing processors
(indexers, archivers, etc) who require an expanded view of the
mediachain. By using aggregators, these peers don't have to track
every leaf namespce in the parts of the hierarchy they are following.
This allows the hierarchy to evolve without affecting existing
processors, who only need to track the aggregate namespace, while also
reducing load in leaf overlays.

#### Indexers

Indexers are peers who collect statements as they propagate in the
network and reconstruct the mapping of media identifiers to metadata
objects. This enables them to provide the query interface for clients
of the database.

At the most basic level, the query language can be limited to
searching for metadata objects by id and namespace parameters. More
advanced indexers will parse the metadata objects and provide a richer
interface which can resolve semantic associations expressed at the
application layer.

#### Archivers

Archivers provide metadata persistence for the mediachain. As such,
they subscribe as leaves to relevant namespace overlays and collect
statements as they propagate in the network, together with associated
metadata.

Periodically, they dump statements and metadata into an archive file,
which they can back up in a persistent volume (eg S3). Archive files are
subsequently published for efficient distribution with IPFS Bitswap or
some out of bands protocol (Bittorrent, HTTP, etc).

#### Directory Servers

In order to function effectively, peers require the means for discovering
other peers and pubsub overlays for their namespaces. This is the function
of directory servers, which provide a registration and lookup interface
that maps namespaces to sets of live peers.

Using well known public directory servers, perhaps hardcoded into the
software, any peer can bootstrap and join the network. Sources use
the directory to discover publishers that can propagate their
statements into the network. Publishers and aggregators use the
directory to announce their presence and discover other publishers to
coordinate their pubsub overlays. Caches, indexers and archivers use the
directory to discover pubsub overlays and register their services.

#### Clients

All peer roles described so far expect peers to be online and publicly
accessible. Clients on the other hand represent the users of the
system and operate in a mostly disconnected fashion. Nonetheless, they
are still proper peers, with their own identities and certificates
that allow them to publish in moderated namespaces.

The basic user interface for a client should support the two basic
mediachain operations: query and upsert. This can be easily
implemented with a command line program and provided from day
one. Over time clients will expand their functionality to express rich
object relationships and eventually provide a more accessible
interface (ie a fancy webapp) for broader adoption.
