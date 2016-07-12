# The Mediachain TestNet

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
we're using Amazon DynamoDB (with multiaddress content-addressed keys) as a stand-in.
  Unstructured data (raw metadata and thumbnails) is still published to IPFS if CLI is
passed the `--use-ipfs` flag)

* Maximum write rate for the network is currently limited to ~20 concurrent writers
  (approx 150 artefact insertions/sec)


## CLI

The CLI is pip installable:

```bash
$ pip install mediachain-client
```

Make sure you have a recent pip, at least 8.x. You also probably want to [install IPFS](https://ipfs.io/docs/install/) and
run `ipfs daemon`


## Indexer

The indexer is also on PyPI:

```bash
$ pip install mediachain-indexer
```

Depending on your OS, you may need to manually install development dependencies for numpy/scipy, see [here](https://www.scipy.org/install.html)

You will also need to install and start [Elasticsearch](https://www.elastic.co/downloads/elasticsearch)

## Transactor

TK

the server can be controlled using killfiles:

```bash
$ # TODO: killfiles
```


