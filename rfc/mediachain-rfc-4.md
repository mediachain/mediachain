# RFC 4: New Directions in Mediachain: Protocols and Architecture

Status: WIP WIP WIP WIP

Author: vyzo

## Lessons from Phase I: It's not a Blockchain

The blockchain is an attractive solution: the public ledger promotes
open data and discovery, while also simplifying streaming for online
processing.

But it comes with significant costs:

* Complexities of maintaining consensus in a peer to peer network.
* PoS/PoW economics that don't quite work in our domain.
* Inefficient querying, requires the entire blockchain for read operations.
* Difficult to scale to large datasets with bulk and firehose ingestion, the blockchain becomes the bottleneck.
* Ultimately trying to solve a problem we don't have: double spending.

In reality, we don't need a singular linearly ordered view of the world.
And we want to allow the system to grow to very large datasets (billions
of objects) supporting bulk and firehose ingestion models.

Fundamentally, what we need is a map of media identifiers to sets
of statements that allows us to track and relate media objects.
This has the hallmarks of a CRDT data structure, which allows a system to
achieve eventually consistent state without the need for consensus. 

So what we want to build is a distributed database that supports
upserts, connecting statements to one or more domain specific identifiers.
It is intended to provide a low level plumbing data structure, as dumb
as possible.  Rich relations between objects can be expressed with
merkle DAGs in object content, allowing the application layer to
evolve according to user needs.

### Domain-Specific Identifiers Instead of Opaque Pointers

In the Phase I data model, objects were assigned a unique identifier
based on their content hash upon ingestion. This is an opaque pointer
which conveys no information about the object. 

The majority of artefacts ingested by the mediachain will already
exist in some database or Digital Asset Management system. Thus, it
makes sense to externalize the preexisting identifiers, with some
per-organization prefix. This allows metadata sources to maintain
their own internal schema, simplifying integration of the mediachain
in their systems, while also conveying meaningful information to
humans. It is also an unobtrusive policy, as it avoids asserting
authority over names and identifiers.

### Data Structure Operations

The Phase I data structure interface provided 3 basic operations:
insert, update, and lookup. The insert method ingested an object into
the system and returned its canonical identifier. For each identifier,
the system maintained a chain pointer linking the object to a chain of
updates. The update method updated the chain pointer by consing a new
update object on top of the current chain, while lookup retrieved the
head of the chain for a canonical.

In practice, inserts are idempotent and the chained update model only
serves to constrain the system by imposing order of operations.
By dropping ordering and utilizing domain specific identifiers, we can
eliminate the distinction between insert and update.

The write interface is thus an upsert method, which combines insert
and update, and adds a statement to the set of objects associated with
an identifier. If the system has no prior knowledge of the identifier,
then it creates a new entry in the identifier mapping with a singleton
set containing the object.

The lookup method can be retained as a basic read operation, with a
change at its return value. Since there are no chained updates any
more, the method returns the known set of updates relating to an
identifier. 

### Namespace Partitioning

The Phase I namespace was flat: all objects were part of the same
space. This may be suitable for a small scale prototype, but it does
not fit well with a vision of a decentralized system at scale.

At large scale, the namespace must be partitioned for scaling
purposes, but also for reasons of administrative control. In terms of
scaling, the partitioning must be semantic so as to allow peers to
commit resources only in topics they are interested in. In terms of
administrative control, the partitioning must be hierarchical so that
we can easily delegate management and moderation to organizational
stake holders. 

We thus adopt a hierarchical structure similar to unix paths. Leaves
in the hierarchy map to specific datasets and provide the primary
entry points for writes. Namespaces higher in the hierarchy aggregate
namespaces below and provide a progressively expanded read view of the
data structure.

### Moderation and Publishing Model

Namespaces allow us fine grained control on publishing and
participation level. At one end of the spectrum, we want to
encourage public namespaces which allow lightweight permissionless
participation. At the other end, we want to support curated namespaces
to control spam and allow authoritative sources and commercial media
providers to maintain control of their own data.

In order to implement this model, each participating peer has an
associated identity corresponding to a public key. The identities
are intended to be long-lived and eventually have an attached reputation
score.

All statements published in the system are signed with the key of the
source of the statement. In order to publish in a moderate namespace,
the originating peer must present a certificate which allows it to
publish there. Alternatively, it can convince a peer with the right
certificate to publish on its behalf. Certificates can be obtained by
moderators and owners of the namespace. 

This moderation scheme, with delegated administrative control,
attaches a cost to peer identities because certificates can be
revoked. This encourages cooperative behavior by individual peers,
thus avoiding the social cost of cheap identities [1]. At the same
time, individual peers with publishing rights are free to implement
their own authentication for their clients.


## A Heterogeneous Network of Cooperative Peers

The database is maintained by heterogeneous peers; they are operated by
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
  directory servers:
   provide registration and discovery services for namespaces
 clients:
   web frontends for human users
   advanced users with CLI or bespoke program access

Peers can have multiple roles in each namespace they participate.

data distribution model: push -vs- pull
ingestion model: bulk, firehose, casual contrib

operational semantics of data ingestion:
single update:
 client insert statement metadata to IPLD -> object pointer
 create and sign statement linking the object to one or more identifiers
  within some namespace
 push statement to a publisher; publisher verifies permissions, adds the
 statement to its local spool and asynchronously broadcasts it to the namespace

## The Mediachain Protocol

### Identities

Public Key identities for peers and clients
Key signing -- p2p certificates

P2P PKI -- DHT overlay for key publication and sharing;
 all peers participate in the DHT

### Namespaces

hierarchical namespace structure
namespace creation and discovery:  directory servers
group membership, moderation, permission model

some bootstrap namespaces: /u, /contrib, /glam
/u -- unmoderated, free for all
/contrib -- individual contributor space in /contrib/user: personal publication space,
/glam -- curated namespace for organizational data sources: museums, art galleries, etc

### Permissions

moderated namespaces:
peer participation role: reader, publisher, moderator
 reader: has no publish permissions, reads within the namespace
  default public role for moderated namespaces
 publisher: has publish permissions
  can manage its own end users, ultimately signs statement blocks
  propagating in the network, but cannot grant direct publishing
  permissions
 moderator:
  can grant publish permissions
  can revoke publish permissions it can granted
 ownership:
  namespace owners can grant modrerator rights
  group initially owned by creator
  co-ownership by granting ownership
  in co-owned namespaces, moderation rights can be granted by any owner
  or some majority of them, depending on policy
  moderation revocation by similar policy

Namepsace hierarchy:
 namespace ownership:
  can create subnamespaces, and grant ownership
 permission:
  grant create namespace

makes identities for curated namespaces expensive to obtain;
certificate revocation mechanisms

scaling up: governance issue -- we'll be happy to have this problem.

### Statements

statement structure
 ns, src, signature, ids, timestamp, ipld object reference
 it's the header for the object

publishing statements: namespace permissions, authentication by the publishing
peer mediating the target namespace(s)

### Updates

publish statements
blocks and archives

block envelope: group together statements signed by users,
 altogether signed by publishing peer:
 multi-statement message
it allows light-weight certificate infrastructure, end user certificates are
 managed at the leaves.

update propagation:
 pubsub for online processors
 poll option for disconnected clients:
  queries by timestamp

resilient overlay multicast
backbone (long lived, connected peers) maintaining overlay network for streaming
namespace updates

republishing with extended identifier sets or expanded namespace scope

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

## Examples

TBD

## References

1. [The Social Cost of Cheap Pseudonyms](http://www.haas.berkeley.edu/Courses/Spring2000/BA269D/FriedmanResnick99.pdf)
