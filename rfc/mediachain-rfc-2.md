# RFC2: Design Considerations for Mediachain at Scale

Status: WIP

Author: vyzo

## Overview

This document is concerned with the Mediachain[*] architecture
as it grows from a small prototype to a large peer-to-peer network.
Specifically, we consider the system architecture and protocol
requirements in order to support the system as it organically
scales.
As such, we sketch a tiered architecture that can operate
both at small and large scale. Then we discuss protocols for
maintaining the Mediachain Journal in peer-to-peer fashion
and the fault tolerance characteristics of the system.
The starting point is the Mediachain Datastore as described in [*].

## A Tiered System Architecture

### Mediachain Peers and Clients

Conceptually we distinguish two types of nodes:
- tier-1 nodes or Mediachain _Peers_.
- tier-2 nodes or Mediachain _Clients_.

Peer nodes form the Mediachain Core Network responsible for maintaining
the Mediachain Journal, which represents the canonical view of the Mediachain
datastore.
Furthermore, they serve clients with views of the journal and record
transactions on their behalf.

Client nodes are the users of the system. They can retrieve and track
the Journal through a Peer node, and they can perform reads and
writes to the datastore.

### Node Keys and Certificates

Each node in the network has a public-private key pair, with its
public key forming it identity.
Conceptually, the various keys form a tower of trust, connected through
certificates.

When the system is bootstrapped, a root key pair must be created which can
sign Level-0 certificates. These are certificates that grant signing
rights for principal entities that can admit Peer nodes to the network.
The key is used to generate such certificates for the principals of the
system and is otherwise always stored encrypted. The public part
is considered well-known and embedded directly in the software so that all
nodes can verify certificates.

Peer nodes require a Level-1 certificate for their keys. In order to
join the core network, a Peer node generates a key pair and asks
a Level-0 certificate holder to sign it a Level-1 certificate.
Thus, membership in core network is moderated for trusted organizations
and individuals.

In contrast, the network is open for all clients. A Client can generate
a key pair and have a Level-2 certificate signed automatically by a Peer
node. In order to reduce the attack surface for spammers, Proof of Work
must be required by a Client before a Peer will sign it a Level-2 certificate
which grants it access to the network.

### The Core Network

The Core Network is responsible for cooperatively maintaining and
updating the Journal.  In order to function, a number of Peer nodes
bootstrap a connected overlay network, and maintain it live by
exchanging regular heartbeats and peer identity messages.  Once booted,
Peer nodes are expected to be long-lived, with a lifespan of months
and longer except for intermittent failures.

The overlay can start fully connected with a small number of bootsrap
nodes, but over time relax the connectivity to an N-cut connected
model.  That is, with a suitable peer selection strategy, the network
remains connected for up to N peer failures without forming a
partition.  The peer identity exchange messages propagate the
constituency of the network, allowing nodes to learn of all other
nodes without a direct connection to each one of them.

New peers can join the network at any time: after obtaining a Level-1
certificate the new Peer fetches a public list of known tier-1 nodes
and connects to them. Upon connecting, it receives fresh list of peers
and reconfigures its connectivity. Then it proceeds to obtain the Journal
from other peers, synchronizes its state and starts serving clients.

### Client Interface

The function of the core network is to provide continuous views of the
Journal to clients and allow them to modify it by performing write
transactions.  In order to insert data to the Mediachain, a connected
Client first writes the requisite data directly to the datastore.
Given that IPFS is an open network, clients naturally maintain the
ability to write directly to the store.

However, in order to _persist_ its data in the Mediachain, the Client
must also update the Journal to reflect the new data. So after writing
to the datastore the Client creates a _Transaction_ consisting of
`JournalEntry` cells and submits it to a Peer node for merging
into the Journal. It then awaits confirmation for its transaction
in the form of a view update.


## The Journal Blockchain


## Fault Tolerance


## References
