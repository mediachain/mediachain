# The Mediachain testnet

Mediachain Labs has deployed a publicly accessible test network for use by the
general public.

The primary purpose of the testnet is to give developers an opportunity to
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
  as a stand-in. Unstructured data (raw metadata and thumbnails) is still published to IPFS,
   unless the CLI is passed the `--disable-ipfs` flag.

* Maximum write rate for the network is currently limited to ~20 concurrent writers
  (approx 150 artefact insertions/sec)

* Because Raft is not byzantine-tolerant, we're not accepting 3rd party transactors
  into the quorum at this time (blog post about this soon)

* Only creation and reference cells are supported in the client, so you can create new entities but can't update
  existing (this will be addressed very soon)

* Translators are currently "black boxes" in python, i.e. there's no DSL


## Client

You probably want to start here! The client is pip installable:

```bash
$ pip install mediachain-client
```

Make sure you have a recent pip, at least 8.x. You also probably want to [install IPFS](https://ipfs.io/docs/install/) and
run `ipfs daemon`.  If ipfs is not running on your machine, be sure to add the `--disable-ipfs` flag when running the `mediachain` command.

### Reading

You should then be able to immediately pull down the statement chain for an artefact:

```bash
$ mediachain get QmbSMhk4EBH7SN2W4EMXuytUVYarZd2gbLdDobeJRhbU6X

{
  "metaSource": {
    "@link": "Qmcbo67Ycv6rCREhQYoYeGJzgAJiCZDfyEdtHqdbmTsv6T"
  },
  "meta": {
    "translator": "GettyTranslator/0.1",
    "raw_ref": {
      "@link": "Qmcbo67Ycv6rCREhQYoYeGJzgAJiCZDfyEdtHqdbmTsv6T"
    },
    "data": {
      "artist": "Michael Ochs Archives",
      "collection_name": "Moviepix",
      "title": "Alfred Hitchcock",
      "caption": "LOS ANGELES -  MAY 22, 1956: Director Alfred Hitchcock with actor Jimmy Stewart and actress Doris Day at the premier of 'The Man Who Knew Too Much' in Los Angeles, California. (Photo by Earl Leaf/Michael Ochs Archives/Getty Images)",
      "editorial_source": "Moviepix",
      "keywords": [
        "Vertical",
        "Black And White",
        ...
      ],
      "date_created": "1956-05-22T00:00:00-07:00",
      "_id": "getty_451356503"
    }
  },
  "type": "artefact",
  "entity": {
    "meta": {
      "translator": "GettyTranslator/0.1",
      "data": {
        "name": "Michael Ochs Archives"
      }
    },
    "type": "entity"
  }
}
```

This resolves the chain head pointer, retrieves the parent cells and folds over them to give a complete
metadata representation as some nice JSON. Straightforward (note that this object uses an older translator version format).

### Writing

A core concept in Mediachain is the notion of versioned, lightweight, nondestructive schema translators
(see [this blog post](https://blog.mediachain.io/mediachain-developer-update-supplemental-translators-6abe3707030a#.260jpzr5c) for a somewhat outdated background treatment). This means that you can import data in arbitrary formats right away
without extensive markup or transformation, and then re-process it later with a new version of the translator that
"knows" more about the underlying data.

The translators are versioned by the IPFS multihash of the working tree, similar to git trees. This means translators can also be published and retrieved through IPFS.

**WARNING** right now, translators are simply python code that's imported and executed directly in the main process, without sandboxing. Never execute translators you don't trust! The long-term vision for translators is a [data-oriented DSL](https://github.com/mediachain/mediachain-client/issues/70) that can be safely executed as untrusted code, but we're not quite there yet.

```bash
$ mediachain ingest translator_name@Qm... target_directory
```

Please see [this page](...) for more on writing and using a translator.

## Indexer

The indexer is also on PyPI:

```bash
$ pip install mediachain-indexer
```

Depending on your OS, you may need to manually install development dependencies for numpy/scipy, see [here](https://www.scipy.org/install.html)

You will also need to install and start [Elasticsearch](https://www.elastic.co/downloads/elasticsearch)

It's also a good idea to install the [optional dependencies](https://github.com/mediachain/mediachain-client/blob/master/README.md#optional-dependencies)
for the mediachain-client project, which will enable more efficient updates from the blockchain.

The indexer is a kind of special client that receives streaming updates from the blockchain and generates
indexes stored in elasticsearch. You can start it with:

```bash
$ mediachain-indexer-ingest receive_blockchain_into_indexer
```

This will pull from the public testnet blockchain by default.  If you're running your own testnet,
you'll want to set the `MC_TRANSACTOR_HOST` and `MC_DATASTORE_HOST` environment variables
 to point to your rpc services.

This ingestion will catch up on the current block, then receive newly published objects.

To run queries, you'll also need to run the API server:

```bash
$ mediachain-indexer-web web
```

The index is then accessible via a REST interface.  Keyword searches can be
issued by sending a json payload to the `/search` endpoint:

```bash
$ curl localhost:23456/search -d '{"q": "film"}'
```

## Transactor

The truly adventurous can start their own testnet. This requires spinning up at least 3 transactors,
a facade server (copycat pojo -> gRPC translation) and a datastore. Check out the [overview](selfhosting.md)
for compilation and launch instructions.  You can also get a good feel for the process by
taking a look at the [provisioning](https://github.com/mediachain/mediachain/tree/release-0.1.0/provisioning/playbooks)
scripts.  Come to _#tech_ in [our Slack](//slack.mediachain.io) if you want to try, and we'll be happy to help!


