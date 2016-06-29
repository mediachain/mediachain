# Mediachain Sharding Exploration

Most decentralized systems striving towards consensus suffer from the same
problem: operating at scale. There are many approaches to scaling decentralized
systems, each with its own strenghts and drawbacks. We'll discuss them in a
series of 'case studies' found below.

## Case Study: Hierarchical Decentralized Systems, DNS

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

## Case Study: Gossip and Keyspace Sharding, CockroachDB

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

## Case Study: Sharding, Ethereum

Ethereum has already encountered scaling issues and is looking to implement
infinite sharding for its version 3.0 network. This would fracture the single
blockchain into arbitrarily many block blockchains. Similar to CockroachDB,
individual peers would only participate in a certain subset of shards, relying
on other peers to manage transactions homed in shards out of their control.

The concept is stilll under works and the proposed implementation is quite
complicated, so it is yet to be determined how successful this can be in
practice. It's also quite possible that the shards will make interaction with
the Ethereum blockchain more difficult. Considering the high barrier to entry
already associated with blockchain technologies, this is concerning.

- Sharding improves write throughput as well as latency.
- Yet unproven, very complex.
- Potentially offputting UX.


