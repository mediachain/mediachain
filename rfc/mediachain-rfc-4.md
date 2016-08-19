# RFC 4: New Directions in Mediachain: Protocols and Architecture

Status: WIP WIP WIP WIP

Author: vyzo

## Lessons from Phase I: It's not a Blockchain

The blockchain is and attractive solution: public ledger promotes open data
and discovery, simplifies streaming for online processing.
But it comes with significant costs:

* complexities of maintaining consensus in a peer to peer network
* PoS/PoW economics that don't quite work in our domain
* Inefficient querying, requires the entire blockchain for read operations
* Difficult to scale to large datasets with bulk and firehose
 ingestion, the blockchain becomes the bottleneck
* Fundamentally trying to solve problem we don't have (double spend)

In reality, we don't need a singular linearly ordered view of the world.
And we want to allow the system to grow to very large datasets (billions
of objects) supporting bulk and firehose ingestion models.

Fundamentally, what we need is a map of media identifiers to sets
of statements that allows us to track and relate media objects.
This has the hallmarks of a CRDT data structure, which allows a system to
achieve eventually consistent state without the need for consensus. 

So what we want to build is a distributed database that supports upserts,
connecting statements to one or more domain specific identifiers.
Low level plumbing data structure, as dumb as possible; rich relations
between objects can be expressed with merkle DAGs in object content (userland)


### Domain-specific identifiers instead of opaque pointers
Allows sources to maintain their own internal schema meaningful to humans.
Avoids asserting authority over names and identifiers.
 
### Operations
Upsert, no distinction between insert and update.

### Namespace partitioning
Semantic namespaces and separation of concerns for participating peers.
Peers may be interested in only a subset of the mediachain, so they can
only participate in topics they are interested in. Natural mapping.

### Moderation and publishing models
Control spam, support authoritative sources who maintain control of their own data.
Public namespaces to support permissionless participation.


## A Heterogeneous Network of Cooperative Peers

The datastructure is maintained by heterogeneous peers; they are operated by
different organizations, and contribute different resources to the network.
Each peer maintains some parts of the dataset and has a limited view of the
mediachain. Peers can synchronize state with peer-to-peer interactions,
converging their view of the data structure.

Roles:
 sources:
   authoritative sources of datasets
   metadata dumps and firehoses
  publishers:
   nodes participating in the overly with permissions to publish statements
   on behalf of users in some namespaces
  caches:
   readers who seed data objects and support basic queries
  aggregators:
   readers who aggregate and stream multiple namespaces for their clients
  indexers:
   collect statements and create indexes mapping ids to statements
   parse metadata objects, built higher order models
   support querying the database
  archivers:
   collect statements and associated metadata and archive them for persistence
 clients:
   web frontends for human users
   advanced users with CLI or bespoke program access

Peers can have multiple roles in each namespace they participate.

data distribution model: push -vs- pull
ingestion model: bulk, firehose, casual contrib


## The Mediachain Protocol

### Identities

Public Key identities for peers and clients
Key signing -- p2p certificates
Identities are intended to be long lived with reputation attached

P2P PKI -- DHT overlay for key publication and sharing

### Namespaces

hierarchical namespace structure
namespace creation and discovery -- directory servers
group membership, moderation, permission model
/u -- unmoderated, free for all

peer participation model: reader -vs- publisher

trust model: namespaces are owned by their stake holders, initial peers
participating.
Need signatures (single member, majority, labs decree) to publish -- 
makes identities for highly curated namespaces expensive to obtain.
certificate revocation: provides spam deterrent.

Ref: [The Social Cost of Cheap Identities](http://www.haas.berkeley.edu/Courses/Spring2000/BA269D/FriedmanResnick99.pdf)

scaling up: governance issue -- we'll be happy to have this problem.

### Statements

statement structure
 ns, src, signature, ids, timestamp, ipld object reference
 it's the header for the object

publishing statements: namespace permissions, authentication by a publishing
peer mediating the target namespace(s)

### Updates

publish statements
blocks and archives
update propagation:
 pubsub for online processors
 poll option for disconnected clients:
  queries by timestamp

resilient overlay multicast
backbone (long lived, connected peers) maintaining overlay network for streaming
namespace updates

### Aggregation and Indexing

aggregate pubsub streams of multiple namespaces, clients subscribe to receive
update streams.
indexing: IDs -> statement mapping
republishing: adding ids, posting to multiple namespaces

### Queries

peers answer queries locally -- provide a subset view of the mediachain
query by time range --> [some] blocks since last update
query by peer id
query by id
query by id + set of namespaces

queries not necessary supported by all peers, index requirements.

### Data Persistence

caches: opportunistic replication, keep buffers of recent updates to
aid redistribution
commit some finite disk space for storage and seeding reference IPLD objects

specialized seeding clusters as service or courtesy to partners

archival peers: sinks that receive streaming updates of interested namespaces
and construct archives of the content; S3 backups.

