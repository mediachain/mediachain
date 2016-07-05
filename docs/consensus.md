# Consensus and Mediachain

*Or, who's on first?*

## Introduction

Global consensus is critical to the function of Mediachain. At present, we're
using [Raft](https://raft.github.io/), an algorithm and protocol for federated
(quorum-based) consensus. We'd like to move away from a federated system towards
an eventually consistent, bitcoin-like consensus protocol to better deliver on
our promises of radical openness.

## Needs

Our transactor (responsible for maintaining consensus within the Mediachain
network) has a few drawbacks we'd like to address:

### Peer-to-peer/Partition Tolerant

We'd like the network to be peer-to-peer and independently discoverable. As a
shepherding entity, we'd like Mediachain Labs to run endpoints to facilitate
easie access to the transactor netowrk, but the network should be completely
discoverable through a known/trusted peer.

Raft gets us discoverability and an open network, though it's not resilient in
the face of partitions. Partitions can easily lead the quorum into states of
disagreement. Furthermore, certain types of partitions (particularly those
relating to NAT traversal) can result in complete halting of progress.

### Resilience to Attack

Raft was
[demonstrated to be vulnerable](https://www.cl.cam.ac.uk/techreports/UCAM-CL-TR-857.pdf)
to certain bad network conditions and attacks. While its performance is
fantastic, Raft simply lacks the durability we'd like to see in Mediachain.
Convergent consensus algorithms such as that of Bitcoin.

## Options

We're currently exploring a few options:
[Bitcoin](https://bitcoin.org/bitcoin.pdf),
[Stellar](https://www.stellar.org/papers/stellar-consensus-protocol.pdf), and
[Tendermint](http://tendermint.com/) offer compelling alternatives.

In the following sections, we'll examine some properties of these various
systems.

### Bitcoin

- Bitcoin has been active in the wild for a few years now and has demonstrated
  itself to be reasonably stable.
- Slow to converge on statements as the network grows.
- Difficult to scale to compensate this slow commit time. Potential solutions
  involve sharding, a la Ethereum.
- Depends on proof-of-work which is taxing on CPUs, but a robust method of
  preventing certain attacks.
- Incredibly simple base algorithm is easy to reason about.
- Multiple solid implementations exist.
- Proven.
  
### Stellar

- Fresh, only deployed by the Stellar non-profit, stellar.org.
- Doesn't rely on proof-of-work or proof-of-stake, making it an attractive
  alternative for use cases like Mediachain which look to manage datasets rather
  than financial assets.
- Faster transaction commits thanks to dynamic-programming feeling approach
  towards consensus based on trust networks.
- Currently deployed in the wild by stellar.org.
- Only has a single implementation, written in C++.
- Proven (unsure of peer-review status.)
- Incredibly complicated algorithm, difficult to implement or customize.
- Unsure of the "UX" of trust networks.

### Tendermint

- Fresh, in use by Tendermint.com.
- Relies on proof-of-stake.
- No proof present?
- Based off of research done at
  [MIT](http://groups.csail.mit.edu/tds/papers/Lynch/jacm88.pdf), with
  extensions to circumvent short-term forking attacks.
- Requires an underlying "coin" for function. Not necessarily a game-breaker,
  but certainly counter intuitive for a database.
  
## Current Thoughts

At present, Stellar feels like a good frontrunner. It's got a fair bit of
momentum and funding behind it, claims to approach some of Bitcoin's scalability
problems, and doesn't rely on proof-of-work which feels a bit unnecessary when
the only thing at stake is deciding an ordering for concurrent metadata updates
as opposed to preventing double spends in financial asset transactions.

I'm concerned about the lack of implementations or documentation for repurposing
the Stellar protocol, but am very impressed by the level of documentation for
everything else pertaining to it. It was also recently proven correct by some
researchers at Stellar.org, which is comforting.
