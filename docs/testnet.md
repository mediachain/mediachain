# The Mediachain TestNet

Mediachain Labs has deployed a publicly accessible test network for use by the
general public. At present, it's not meant to be relied on in anything
resembling a production setting, as APIs are likely to break and the dataset
will be periodically wiped.

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

- **Transactor**: Responsible for maintaining consensus amongst nodes operating
  within the system.
- **Indexer**: Responsible for making data within Mediachain explorable via text
  and media-based search.
- **CLI**: Used to ingest data into and retrieve data from Mediachain.

Each one of these has its own installation process, as is detailed below.
Mediachain Labs will also be administrating its own instances of the Transactor
and Indexer for public use, so developers needn't worry about running them on
their own.

The current iteration of Mediachain is backed by Amazon's DynamoDB instead of
IPFS, as we wanted to maintain a certain ease of operation as we worked out the
initial kinks within the system.

## Installing the Mediachain Services

### Transactor
If you'd like to run your own TestNet using either
the DynamoDB local service or a paid DynamoDB instance of your own:

```bash
$ # TODO: jar install
```

and run it with:

```bash
$ # TODO
```

the server can be controlled using killfiles:

```bash
$ # TODO: killfiles
```

### Indexer

The indexer is a client that must connect to a transactor quorum in order to
function. You can download it from PyPI:

```bash
$ pip install mediachain-indexer
```

Then, from the indexer directory, run it with:

```bash
$ #something
```

### CLI

The CLI is pip installable. Install it with this command:

```bash
$ pip install mediachain-client
```

## Interacting With The TestNet

With those services installed, you're ready to start working with the TestNet.

### CLI

<Some basic information about ingesting images>

### Clients

