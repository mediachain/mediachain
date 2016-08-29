## mediachain.next api notes

Everything here is in flux and subject to change; this is just a sketch. 
The idea is to give an overview of the high-level api surface of an "online" node, that is, 
one that actively contributes to the network and is expected to remain connected to the network.

All the code examples below are in javascript, with flow-style annotations for return types.

### Peer ids & local state
A peer has a unique id, which is the hash of their public key.  They may also have a collection of 
certificates, signed by moderators of the namespaces they're authorized to participate in.

The peer-local state includes the peer's id, certificate store, private key, etc.  It also includes the 
index of connected peers, which allows us to route operations to peers with the correct roles.

For the examples below, the peer-local state is assumed to exist in an accesible place 
(like an object instance variable, etc), so it's omitted from the function arguments.

### Domain-specific ids (Well-Known-Identifiers)
The RFC-4 design uses the term Well-Known-Identifier (WKI) to refer to an existing domain specific id, 
for example `nypl:510d47e2-ef28-a3d9-e040-e00a18064a99` which refers to an object in the New York Public Library collection.

**question:** is there a shared directory of WKI prefixes? How does a user know to use `getty` instead 
of e.g. `gettyimages`, or that `getty` doesn't refer to http://www.getty.edu/?

### Connect / peer discovery
Ideally a single `connect()` method that optionally allows you to specify the address of a bootstrap peer / 
directory server. If not provided, picks a peer from a hard-coded set built into the app. 

```javascript
function connect(options): Promise<Unit> {
  let {bootstrapAddress} = options
  // ...
}
```

*mock design*: This is a noop, as we're mocking a fully connected node with a direct route to all (virtual) peers.

*p2p design*: Contacts a directory server to update the peer index and register any services we're capable of providing. 
May push updates that we've spooled locally to any peers that were previously subscribed to us.  
May register for periodic updates from the director server.

### Publication
A node can publish to any leaf namespace that it has write permission for.  If a namespace is not specified, statements 
are published to the user's personal namespace, which corresponds to the hash of their peer id, e.g. `universe.peers.QmF00123` 

**questions:** should we accept a list of namespaces, to publish to multiple namespaces at once?

```javascript
function publish(statement): Promise<StatementID> { ... }

publish({
  wkis: ["getty:530122144"],
  meta:{
    "orientation": "Horizontal",
    "keywords": ["..."],
  },
  namespace: "images.getty.entertainment"
})
```

*mock design*: inserts into a local sql db or similar. may check a permission table to ensure the local peer id 
is authorized for the given namespace (or just assume authorization for all namespaces).

*p2p design*: inserts statement into local sql db. may push updates to subscribers.  will provide updates 
in response to "poll" requests. 

#### Bulk publication
The bulk publication api is very similar to the single-statement api, but accepts a list of statement objects.

```javascript
function publish_bulk(statements): Promise<Array<StatementID>> { ... }
```

*mock design:* no significant difference from single-statement implementation.

*p2p design:* bulk publication differs in implementation.  Statements will be signed in batches and distributed 
in archives for efficiency.  This may complicate the implementation for reads, when a peer requests a small 
subset of the archive.


#### Note about ID extraction, translation, etc.
The `publish` functions above assume a pre-processing step which maps ids to the global namespace of Well-Known-Identifiers.  
For example, the getty image id `530122144` is mapped to `getty:530122144`.

The contents of the `meta` field may also need some amount of translation, but the specifics of that are still not fully worked out.

#### Moderation and publication-by-proxy
Some namespaces will only accept statements from peers whose keys have been signed by an authoritative key.

However, we also want to allow outside contribution without necessarily granting full write access to the namespace.  
This can be done by having an approved peer sign a statement from another user, effectively "vouching" for it as appropriate for the namespace.

It's not yet clear to me how the moderation process will work in practice.  It may be enough to simply have the 
submitter publish as usual, and if they're not authorized for the namespace they're trying to publish to, the 
statement will go into a moderation queue for review by a human.  This has the potential to be overwhelming if 
there's a large volume of statements that need review, especially since, in contrast to e.g. usenet, mediachain 
statements are not primarily designed to be human-readable. 

### Query
Simple queries based on WKI, peer id of creator, namespaces, etc are possible without parsing the contents of statements. 

```javascript
function query({
  wkis: ["getty:530122144"],
  namespaces: ["images.getty", "universe"],
  local_only: false,
}): Promise<QueryResult>
```

The above would return a composite of all statements about getty image `530122144` made in the official getty namespace, 
plus everything from the unmoderated "universe" namespace.

In the example, `local_only: false` indicates that we want to query the network as well as consult our local index.  
This may have a significant latency cost.  Local queries will be fast but will likely be incomplete, unless the local 
node has a large cache of the given namespaces that's frequently updated.

The `QueryResult` type includes the set of all statements retrieved, and provides an interface for viewing them in 
aggregate, as a single logical object.  Including the entire set of statements will let us track which parts of the 
composite object originated from which particular peers.

#### "semantic query"
Querying against the contents of statements themselves (e.g. keyword queries, perceptual image searches, etc) can be 
fulfilled by specialized indexer nodes.

The exact api for such searches is not yet specified.  When we do spec it out, it would be good to consider how we 
can advertise the query capabilities of an indexer node to the rest of the network.  For example, a 
"text search index" role could be registered with a directory server for a given namespace and associated with a node.  
Text searches against that namespace could then be routed there.
