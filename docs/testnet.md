# The Mediachain testnet

Mediachain Labs has deployed a publicly accessible test network for use by the
general public.

The primary purpose of the testnet is to give developers an opportunity to
get a feel for the broader architecture of Mediachain as a network and begin
experimenting with a shared metadata set.

We encourage all contributions, especially questions!  As the components of the
mediachain network are under constant development, it can be hard to know where
to begin.  Please [reach out to us on Slack](//slack.mediachain.io) if you
have questions about how the pieces fit together, or if you have trouble
interacting with the testnet.


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

Mediachain Labs will be administrating its own instances of the Transactor
and Indexer for public use, so developers needn't worry about running them on
their own.

For those interested in running their own testnet, please see the [self-hosting documentation](selfhosting.md).


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

You probably want to start here! The client is installable with [pip](https://pip.pypa.io/en/stable/),
preferably into a [virtualenv](https://virtualenv.pypa.io/en/stable/userguide/)

```bash
$ virtualenv venv
$ source venv/bin/activate
(venv) $ pip install mediachain-client
```

Make sure you have a recent pip, at least 8.x. If `pip --version` reports a lower version,
you can update pip with itself: `pip install -U pip`.

You will also need to [install IPFS](https://ipfs.io/docs/install/) and
run `ipfs daemon`.

```bash
# OS X
$ wget https://dist.ipfs.io/go-ipfs/v0.4.2/go-ipfs_v0.4.2_darwin-amd64.tar.gz
# Linux
$ wget https://dist.ipfs.io/go-ipfs/v0.4.2/go-ipfs_v0.4.2_linux-amd64.tar.gz

$ tar xvfz go-ipfs.tar.gz
$ mv go-ipfs/ipfs /usr/local/bin/ipfs
$ ipfs daemon
Initializing daemon...
Swarm listening on /ip4/127.0.0.1/tcp/4001
Swarm listening on /ip4/192.168.0.2/tcp/38463
Swarm listening on /ip4/192.168.1.248/tcp/4001
Swarm listening on /ip6/::1/tcp/4001
API server listening on /ip4/127.0.0.1/tcp/5001
Gateway (readonly) server listening on /ip4/127.0.0.1/tcp/8080
Daemon is ready
```

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

A set of sample ids to query is available on ipfs, and can be retrieved with `ipfs get QmYw3BGZhmhYtR7iUNeuUXEiAdGGrL4c91WsHoCzEo2jyU`,
or via the http gateway at [https://ipfs.io/ipfs/QmYw3BGZhmhYtR7iUNeuUXEiAdGGrL4c91WsHoCzEo2jyU](https://ipfs.io/ipfs/QmYw3BGZhmhYtR7iUNeuUXEiAdGGrL4c91WsHoCzEo2jyU)

For a much larger set, use `ipfs get QmRBnvwUosXssWPUMYw9Syqt6hZUA5ZVokJAiWAdwkGRV6` - this contains roughly 1 million
record ids, and due to its size it's much more efficient to retrieve it with the ipfs tool than via the gateway.

Both files contain one id per-line, resolvable with the `mediachain get <id>` command.

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

Please see [this page](translators.md) for more on writing and using a translator.

## Indexer

The testnet includes a special client known as the Indexer, which ingests mediachain
records as they're written to the blockchain and creates a query index that's
accessible via a web API.  A web-based UI is in progress, but in the meantime,
you can issue queries directly by sending json data to `http://indexer.mediachain.io/search`:

```bash
$ curl indexer.mediachain.io/search -d '{"q": "film"}'
```

As you write new records, they should appear in the search results when you search
for keywords contained in their metadata.

If you're interested in running the indexer locally, please see the [self-hosting instructions](selfhosting.md#indexer).
Note that you don't have to run your own testnet to have a local indexer.  The default configuration
will connect to the public testnet and create a local index that you can query.

## Transactor

The truly adventurous can start their own testnet. This requires spinning up at least 3 transactors,
a facade server (copycat pojo -> gRPC translation) and a datastore. Check out the [overview](selfhosting.md)
for compilation and launch instructions.  You can also get a good feel for the process by
taking a look at the [provisioning](https://github.com/mediachain/mediachain/tree/release-0.1.0/provisioning/playbooks)
scripts.  Come to _#tech_ in [our Slack](//slack.mediachain.io) if you want to try, and we'll be happy to help!


