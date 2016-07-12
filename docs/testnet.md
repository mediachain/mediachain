# The Mediachain testnet

Mediachain Labs has deployed a publicly accessible test network for use by the
general public.

The primary purpose of the TestNet is to give developers an opportunity to
get a feel for the broader architecture of Mediachain as a network and begin
experimenting with a shared metadata set.

## Component Breakdown

As was detailed in RFCs
[1](https://github.com/mediachain/mediachain/blob/master/rfc/mediachain-rfc-1.md)
and
[2](https://github.com/mediachain/mediachain/blob/master/rfc/mediachain-rfc-2.md),
as well as the [Mediachain Labs Blog](http://blog.mediachain.io/), Mediachain is
broken into a few core services:

- **Transactor**: Responsible for maintaining consensus on the journal
- **Indexer**: Responsible for making data within Mediachain explorable via text
  and media-based search
- **CLI**: Used to ingest and retrieve data

Each piece can be installed and used separately, as is detailed below.
Mediachain Labs will also be administrating its own instances of the Transactor
and Indexer for public use, so developers needn't worry about running them on
their own.

## Known Limitations

* Because [IPLD](https://github.com/ipfs/go-ipld) isn't quite production ready yet,
  we're using Amazon DynamoDB (with multiaddress, forward-compatible content-addressed keys)
  as a stand-in. Unstructured data (raw metadata and thumbnails) is still published to IPFS if CLI is
  passed the `--use-ipfs` flag)

* Maximum write rate for the network is currently limited to ~20 concurrent writers
  (approx 150 artefact insertions/sec)

* Because Raft is not byzantine-tolerant, we're not accepting 3rd party transactors
  into the qorum at this time (blog post about this soon)

* Only creation and reference cells are supported in the client, so you can create new entities but can't update
  existing (this will be addressed very soon)

* Translators are currently "black boxes" in python, i.e. there's no DSL


## Client

You probably want to start here! The client is pip installable:

```bash
$ pip install mediachain-client
```

Make sure you have a recent pip, at least 8.x. You also probably want to [install IPFS](https://ipfs.io/docs/install/) and
run `ipfs daemon`

### Reading

You should then be able to immediately pull down the statement chain for an artefact:

```bash
$ mediachain --use-ipfs get <some-id>
```

This resolves the chain head pointer, retrieves the parent cells and folds over them to give a complete
metadata representation as some nice JSON. Straightforward.

### Writing

A core concept in Mediachain is the notion of versioned, lightweight, nondestructive schema translators
(see [this blog post](https://blog.mediachain.io/mediachain-developer-update-supplemental-translators-6abe3707030a#.260jpzr5c) for a somewhat outdated treatment). This means that you can import data in arbitrary formats right away
without extensive markup or transformation, and then re-process it later with a new version of the translator that
"knows" more about the underlying data.

We're experimenting with asking users to contribute a minimal translator when importing data; this could be something
as simple as extracting just the external id (though for a more full-fledged example, see the [getty translator](...))

To make the translator module visible to the client tool, open a PR against https://github.com/mediachain/schema-translators
and specify `some-repository/schema-translators.git@sha_or_branch` as the translator id:

```bash
$ mediachain ingest some-repository/schema-translators.git@sha_or_branch target_directory
```

## Indexer

The indexer is also on PyPI:

```bash
$ pip install mediachain-indexer
```

Depending on your OS, you may need to manually install development dependencies for numpy/scipy, see [here](https://www.scipy.org/install.html)

You will also need to install and start [Elasticsearch](https://www.elastic.co/downloads/elasticsearch)

The indexer is a kind of special client that receives streaming updates from the blockchain and generates
indexes stored in elasticsearch. You can start it with:

```bash
$ mediachain-indexer... ingest...
```

This ingestion will catch up on the current block, then receive newly published objects. The index is
accessible via REST interface at

```bash
$ curl ...
```

## Transactor

The truly adventurous can start their own testnet. This requires spinning up at least 3 transactors,
a facade server (copycat pojo -> gRPC translation) and a datastore. Take a look at the [provisioning](https://github.com/mediachain/mediachain/tree/release-0.1.0/provisioning/playbooks)
scripts and come to _#tech_ in [our Slack](//slack.mediachain.io) if you want to try


