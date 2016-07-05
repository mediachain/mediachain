# Mediachain Sharding Exploration

Most decentralized systems striving towards consensus suffer from the same
problem: operating at scale. There are many approaches to scaling decentralized
systems, each with its own strenghts and drawbacks. We'll discuss them in a
series of 'case studies' found below.

## Case Studies

### Case Study: Hierarchical Decentralized Systems, DNS

The Domain Name System, DNS, is a famously decentralized system.

- Makes a common trade-off of slight centralizaiton for performance and
  consistency.
- Updates are sent to the top of the hierarchy, at which point they become truth
  and then propogate back down remaining branches of hierarchy.
- Top layer of the hierarchy is a consortium, communicating via the 
  [Border Gateway Protocol](https://en.wikipedia.org/wiki/Border_Gateway_Protocol).
- Consortium is laid out as a set of zones, each corresponding to a
  
DNS is an efficient, WAN-scale system. While it is subject to a variety of
attacks at the lower levels of the hierarchy, it has managed to serve the
purposes of the web for years now.

Unfortunately, the existence of a federation is a deal breaker for the time
being.

### Case Study: Gossip and Keyspace Sharding, CockroachDB

CockroachDB, developed by Cockroach Labs, is a compelling new entrant to the
distributed database space. 

In order to scale write speeds, CockroachDB shards (splits into many pieces) the
keyspace of its underlying key-value store. Each shard is replicated by some
tunable number of machines participating in the quourum, N. Those N servers form
a small quorum which ensure their views of that data stored in that share are
consistent.

One of Cockroach's interesting qualities is its lack of reliance on an external
atomic datastore, such as Zookeeper, to coordinate the cluster (Zookeeper is
commonly used for leader election.) Instead, CockroachDB features a gossip
protocol that allows new peers to enter a swarm, at which time they are added to
some subset of the database quorums. This peer discovery protocol allows the
system to self-manage through a meta-quorum. This peer discovery makes it
possible for the database network to grow without explicit centralization.

- Sharding improves write performance (scaling reads is simpler.)
- Gossip/peer discovery makes quorums more flexible than in a standard sharded
  database.
- Suffers from standard suite of Raft attacks.

### Case Study: Sharding, Ethereum

Ethereum has already encountered scaling issues and is looking to implement
infinite sharding for its version 3.0 network. This would fracture the single
blockchain into arbitrarily many block blockchains. Similar to CockroachDB,
individual peers would only participate in a certain subset of shards, relying
on other peers to manage transactions homed in shards out of their control.

The concept is still under works and the proposed implementation is quite
complicated, so it is yet to be determined how successful this can be in
practice. It's also quite possible that the shards will make interaction with
the Ethereum blockchain more difficult. Considering the high barrier to entry
already associated with blockchain technologies, this is concerning.

- Sharding improves write throughput as well as latency.
- Yet unproven, very complex.
- Potentially offputting UX.

## Relevance to Mediachain

Mediachain has one massive advantage over other blockchain technologies: since
Mediachain does not concern itself with asset transfer, transactionality is only
required to prevent data chains from diverging. The problem that makes
Ethereum's sharding solution so complex simply doesn't apply to Mediachain.

Following this, Mediachain can be relatively simply sharded over the key of the
object being modified. For clarity:

- Let K be the keyspace of objects in Mediachain
- Let S{n} forall n in Nat, be the set of all possible divisions of K into n
  intervals such that, when combined, these intervals form K
- Let Sx be a member of S{n}, n/x
- Let Sxi be the ith member of Sx, where 0 <= i < x
- For a given key, k, its data is to be committed to the node associated with
  the interval in Sx containing k
  
To minimize data churn when adding or removing shards, we use a technique known
as [consistent hashing](https://en.wikipedia.org/wiki/Consistent_hashing).
Consistent hashing also has an additional benefit of helping ensure data is
distributed more evenly amongst peers in a network. There exists a wealth of
resources on consistent hashing, so I will leave the explanation to them.
[This](http://www.paperplanes.de/2011/12/9/the-magic-of-consistent-hashing.html)
explanation seems solid!

With a sharding system like this in place, Mediachain should be able to support
an arbitrary number of shards, helping scale write throughput. There will
certainly be some bookkeeping required maintain consensus with regards to the
state of the consistent hash ring. This could potentially take the form of a
meta-consensus layer.

An alternate approach sometimes explored by realtime systems is pre-partitioning
the keyspace into some precomputed number of shards that should theoretically be
capable of handling the upper limits of the system's expected scale. Under this
approach, nodes participate in many very small shards. This significantly
simplifies rebalancing, which can be tricky under traditional consistent hashing
schemes in which shards can be added and removed.
