
# RFC 3: The V1 Indexer

Status: DRAFT

Author: [autoencoder](github.com/autoencoder)


## Overview

This document outlines a proposed `Indexer` system for Mediachain[1]. This system will perform media ingestion, media search and media deduplication. Specifically, this document focuses on the first version of the `Indexer` referred to here as `Indexer V1`.

We will also outline a roadmap toward an improved version, referred to here as `Future Indexer`.


## Terminology

Relevant Mediachain terminolgy background for this RFC. See RFC-1[2] and RFC-2[3] for more information.

- `Indexer` - Mediachain subsystem described in this RFC.
- `Datastore` - Persistent content-addressed storage system, used to store Mediachain media and blocks. Currently IPFS[4].
- `Journal` - Flattened and partially-reconciled view of the Mediachain knowledge base. Maintained by `Transactors`.
- `Folding`: Producing a single flattened representation of a chain of updates, reconciling any conflicts and discarding bad information in the process.
- `Clients` - Embedded API or external nodes, which are the interface for end-user nodes to receive a stream of updates to the `Journal` from `Transactor` nodes, and for communicating writes to the `Transactors`. They also conduct reads and writes to the `Datastore`.
- `Transactors` - Core of Mediachain. Accepts writes to the `Journal` from `Clients`, serves `Clients` with views of the `Journal`, and records transactions on their behalf. (Formerly known as `Peers`.)


## Roadmap

`V1 Indexer` will be a minimal proof of concept, while `Future Indexer` represents a set of future enhancements. Key differences are outlined below:


   `V1 Indexer`                                      |  `Future Indexer`
  ---------------------------------------------------|------------------------------------------------------------------------
   Centralized.                                      | All components distributed across multiple machines.
   Off-the-shelf components.                         | Purpose-built components.
   Single perceptual matching goal.                  | Multiple semantic matching intents supported.
   No supervised training.                           | Custom supervised relevancy / dedupe training for specific task(s) and dataset at hand.
   No multi-object detection.                        | Multiple objects detectable per media object, via saliency detection / object localization.
   No explicit user relevancy feedback.              | Accepting explicit user feedback signals.
   No implicit user behavioral training.             | Training based on implicit user behavior signals.
   Basic attack mitigation and spam control.         | Sophisticated attack mitigation.
   Basic or no regression testing.                   | System to detect regressions caused by newly trained models.


In summary, `V1 Indexer` will begin as a simple self-hosted system, composed of off-the-shelf open source components, having only a minimal wrapping JSON/REST API to provide abstraction, and using a small importer component for feeding data to and from the blockchain. This version will use simple, unsupervised models of perceptual similarity, based on simple image and textual heuristics.


`Future Indexer` can be expanded to include features such as sophisticated neural CNN + attention models, training on supervised training corpuses setup to directly match the system's end-goals, comprehensive scoring and regression testing systems, and sophisticated handling of multi-modality mixed textual and media inputs. Last, a key challenge of `Future Indexer` will be in efficiently scaling the indexes over multiple machines in a P2P environment.


## Modes of Operation:

Functions performed by the `V1 Indexer` can be broken down into the following modes of operation:

- **Ingestion** - `Transactors` feed the `Ingestor` with newly created media. The media is semantically indexed for later search and dedupe needs.
- **Search** -  End-user submits a media file or media ID, a list of most semantically relevant media IDs is returned.
- **Dedupe** - Determine which media in the index are duplicates / non-duplicates. Submit a subset of these results to the `Transactors`. This process can be initiated periodically to reprocess all media in the search index - for example after each time the dedupe models are considerably re-trained. Or, it can act only on new media from the `Journal`, received via the `Client`.
- **Search Relevance Training** / **Dedupe Training** - Background training and tuning process that updates the models and tunes hyper-parameters. After each training, models are regression tested, put into production where live-user regression metrics are again measured. If major regressions are detected, models and hyper-parameters are reverted to an earlier version.


## Architecture Overview:

The following describes a high-level overview of the Ingestion, Search, and Dedupe operation modes for the `V1 Indexer`.

Due to overlapping functionality, these systems share some components. Despite the overlap, they can be decoupled, such that each set of mode of operation can be done on separate nodes with infrequent communication needed between them.


```
        INGESTION:                   SEARCH:                      DEDUPE:

     +---------------+        +------------------+          +---------------+
     |  Transactors  |        |     End-User     |          |  Transactors  |
     +------+--------+        +---+----------^---+          +-------^-------+
            |                     |          |                      |
       (copycat/gRPC)        (JSON/REST) (JSON/REST)          (copycat/gRPC)
            |                     |          |                      |
  +---------+---------------------+----------+----------------------+-----------------+
  |         |                     |          |                      |                 |
  |         |                     |          |           +----------+--------------+  |
  |         v                     v          ^           |       Client            |  |
  |         |                     |          |           +----------^--------------+  |
  |         |                     |          |                      |                 |
  |         |                     |          |               (Artefact-Linkage)       |
  |         |                     |          |                      |                 |
  |         |                     |          |           +----------+--------------+  |
  |         |                     |          |           |   Dedupe Staging        |  |
  |         |                     |          |           +----------^--------------+  |
  |         |                     |          |                      |                 |
  |         |                (Raw-Media) (Media-IDs)         (Artefact-Linkage)       |
  |         |                     |          |                      |                 |
  |  +------v--------+            |          |           +----------+--------------+  |
  |  |    Client     |            |          |           |    Dedupe Clustering    |  |
  |  +---------------+            v          ^           +----------^--------------+  |
  |         |                     |          |                      |                 |
  |     (Media IDs)               |          |              (Pair IDs+Split/Merge)    |
  |         |                     |          |                      |                 |
  |  +------v---------+     +-----v----------+----+      +----------+--------------+  |
  |  | Media Ingester |     |  HTTP Search API    |      |  Dedupe Pairwise Model  |  |
  |  +------+---------+     +---+---------------^-+      +----------^--------------+  |
  |         |                   |               |                   |                 |
  |    (Raw Media)         (Raw Media)          ^         (IDs for Candidate Groups)  |
  |         |                   |               |                   |                 |
  |  +------v--------+     +----v--------+      |        +----------+--------------+  |
  |  |  Vectorizer   |     |  Vectorizer |  (Media IDs)  |  Dedupe All-vs-All NN   |  |
  |  +------+--------+     +----+--------+      |        +-----+----------^--------+  |
  |         |                   |               ^              |          |           |
  |     (Vectors)           (Vectors)           |          (Media ID) (Media IDs)     |
  |         |                   |               |              |          |           |
  |  +------v-----------+  +----v---------------+-+      +-----v----------+--------+  |
  |  |    KNN Index     |  |       KNN Index      |      |        KNN Index        |  |
  |  +------------------+  +----------------------+      +-------------------------+  |
  |                                                                                   |
  |                                --V1 Indexer--                                     |
  +-----------------------------------------------------------------------------------+

  Note: Repeated blocks of same name are the same block.
  Not shown: All components have access to IPFS database for media retrieval.

```

## Indexer Components

The following are the major components of the `Indexer`. Those marked `Future Indexer` only apply to that version.

+ `Media Ingester`
  - Listens for new media from the `Journal` via a `Client`. Passes results to `Vectorizer`.

+ `HTTP Search API`:
    - Services user search queries.
    - Asynchronous batching enqueues tasks, until timeout is reached or queue reaches a specified size.

+ `Vectorizer`:
    - High-recall information retrieval model that converts media objects into feature vectors or compact binary codes.
    - Example training approaches: unsupervised, triplet-based i.e. more-similar pair / less-similar pair.
    - `Future Indexer` - detect multiple objects per media content and produce multiple feature vectors, possibly annotated with
       the particular locations at which each object appears in the media.
    - `Future Indexer` - output multiple vectors corresponding to multiple types of semantic similarity.

+ `Vector Compacting`:
    - `Future Indexer` only: depending on the models chosen, an additional step may be required to effectively transform the feature vectors
      into more compact binary codes.

+ `KNN Index`:
    - Stores document feature vectors, and does k-nearest-neighbor lookups on those vectors.
    - `V1 Indexer` will be distributed across worker machines using the "shards & replicas" approach. Queries will be routed to one of the replica sets, and then routed to all shards in that replica set. Results from all shards in the replica set are then combined, ranked, and returned upstream.

+ `Dedupe All-vs-All NN`:
    - Retrieve list of candidate nearest-neighbors media objects, for all media objects.

+ `Search Re-Ranking`
    - `Futuer Indexer` only:
    - Slower model that ranks all candidate media from the `KNN-Index` lookup, based on relevancy to the query.
    - Typically involves in-depth attention to each pair of query vs candidate, in order to produce a relevancy score for each candidate media work.

+ `Search Override`
    - `Future Indexer` only:
    - Able to override potentially poor relevancy output of the underlying search system with known-good answers.
    - Assists in mitigating certain types of attacks.
    - Provides possibility for instant updates, versus requiring slow model-retraining before results are visible.

+ `Dedupe Pairwise`:
    - Classification model that inspects all pairs of candidate duplicates returned from the `Dedupe All-vs-All NN` model.
    - Produces "same" or "different" classifications for all candidate pairs.
    - Passes classification results to `Dedupe Clustering`.

+ `Dedupe Clustering`:
    - Transforms pairs of same / different classifications from `Dedupe Pairwise` into a maximum-agreement flat clustering.

+ `Dedupe Staging`:
    - Allows bulk approval or denial of newly proposed sets of bulk deduplication updates, prior to being committed to the network.
    - Filters down the proposed changes to only those that are relevant - those links not already sent to the chain, or the refuting
      of incorrect links proposed by other entities.
    - `Future Indexer` will have additional scoring and regression checks here.



## `Ingestion` Subsystem

The `Ingestion` system accepts media works from the `Transactors` via the `Clients`, performs basic attack mitigation or prioritization,
and then processes the media objects into feature vectors or compact binary hashes. These features, along with the
media IDs are then inserted into the `KNN Index`. The generated index of stored media will be used by the `Search` and `Dedupe` systems.


## `Search` Subsystem

The `Search` system is exposed to end-users via a REST/JSON API. The address of the REST API will be communicated to end-users via the
Mediachain Core. This API allows users to input a query or media ID, and receive a ranked list of most semantically-relevant media IDs.


 Method | REST Call | Description | Returns
 -------|-----------|-------------|-------------------------------------------------------------------------------------------------
 POST | /search       | Search by base64-encoded image or image ID.     | List of image IDs, possibly with relevancy scores.
 POST | `/distance`   | Calculate metric-space distance between 2 images. | Numeric distance between 0.0 and 1.0.
 POST  | `/ping`       | Uptime check.                                     | See `'error'` value.


At a later stage, this subsystem may accept relevancy feedback from end-users.


 Method | REST Call | Description | Returns
 -------|-----------|-------------|-------------------------------------------------------------------------------------------------
 POST | `/record_relevancy` | Accept user relevancy feedback for a search query. | See `'error'` value.


#### Endpoint: `/search`

Description: Search by base64-encoded image or image ID.

Input: JSON-encoded payload, with keys and values of the following types:


Key                 | Value
--------------------|--------------------------------------------------
query_text          | String - Text of query.
query_media_id      | String - IFPS media content address.
query_media_base64  | String - Base64-encoded media object.
query_facets        | Dictionary - Key-value dictionary of query requirements. For `Future Indexer`.
limit               | Integer - Maximum number of results to return.


Output: List of relevant media IDs. See 'error' key in case of error::

```
{'data':['ipfs://1234', 'ipfs://1234',],
 'next_page':'/link...',
 'prev_page':'/link...',
 }
```

#### Endpoint: `/distance`

Description: Calculate metric-space distance between 2 images.

Input: JSON-encoded payload, with keys and values of the following types:


Key                  | Value
---------------------|--------------------------------------------------
query_media_ids      | List of Strings - IFPS media content addresses.
query_media_base64s  | List of Strings - Base64-encoded media objects.


Output: List of relevant media IDs. See 'error' key in case of error:

```
{'result':0.05}
```

#### Endpoint: `/record_relevancy`

Description: Not present in `V1 Indexer`. Accept user relevancy feedback for a search query. Possibly provides instant attack mitigation for certain types of rapidly-emerging attacks.

Input: JSON-encoded payload, indicating


Key                 | Value
-----------------------------------------------------------------------
query_media_ids      | List of Strings - IFPS media content addresses.
query_media_base64s  | List of Strings - Base64-encoded media objects.


Output: List of relevant media IDs. See 'error' key in case of error:

```
{'result':0.05}
```


## Training Data and Model Update Lifecycle:

`V1 Indexer` will not require supervised training.

`Future Indexer` will consume training data from a variety of sources including:

  - Explicit user feedback.
  - Implicit user signals.
  - External paid annotators.
  - Trusted in-house annotators.
  - Existing 3rd party training data sources.

The training process involves 2 layers of validation, with saved model checkpoints for quick reverting:

- Obtain training data from explicit supervised training, implicit user signals, and other sources.
- Train models.
- Validate against a hold-out set. If major regressions exist, attempt to correct and then go back to training.
- Deploy new models, while retaining old model files.
- Validate new models by watching for regressions or improvements in user metrics. If regression, revert live models to previous version.



## `Dedupe` Mode:

The `V1 Indexer` `Dedupe` subsystem will initially consume media from the search index, and write duplicates using `ArtefactLinkCell` commands sent to `Transactors`.

At a later stage, it may consume training data from external resources including `ArtefactLinkCell` commands created by other nodes, and receive linking information from JSON/REST API functions to allow end users to indicate duplicate media.


Method calls, to be expanded in `Future Indexer`:


 Method | REST Call | Description | Returns
 -------|-----------|-------------|-------------------------------------------------------------------------------------------------
 POST | `/dupe_check` | Return calibrated-probability that a pair of images are duplicates. | Estimated probability, between 0.0 and 1.0.
 POST | `/record_dupe`      | User labels a set of images as dupes / non-dupes. This is added to training data. | Check `'success'` `'error'` keys.
 POST | `/record_relevancy` | User labels the relevancy of a set of search results for a query. | Check `'success'` and `'error'` keys.


## Future Indexer Architecture


Future indexer will be structurally similar to the `V1 Indexer`, with components increasing in sophistication, and some new components added.

In addition to the expanded architecture, `Future Indexer` will contain more sophisticated training, instrumentation, and score evaluation systems which are described elsewhere.

```
        INGESTION:                        SEARCH:                               DEDUPE:

     +---------------+        +---------------------------------+          +---------------+
     |  Transactors  |        |          End-User               |          |  Transactors  |
     +------+--------+        +---+---------------------^-------+          +-------^-------+
            |                     |                     |                          |
       (copycat/gRPC)        (JSON/REST)           (JSON/REST)               (copycat/gRPC)
            |                     |                     |                          |
  +---------+---------------------+---------------------+--------------------------+-----------------+
  |         |                     |                     |                          |                 |
  |         |                     |                     |               +----------+--------------+  |
  |         v                     v                     ^               |       Client            |  |
  |         |                     |                     |               +----------^--------------+  |
  |         |                     |                     |                          |                 |
  |         |                (Raw Media)           (Media IDs)              (Artefact-Linkage)       |
  |         |                     |                     |                          |                 |
  |         |               +-----v---------------------+--------+      +----------+--------------+  |
  |         |               |         HTTP Search API            |      |   Dedupe Staging        |  |
  |         |               +--- -----------------------^--------+      +----------^--------------+  |
  |         |                     |                     |                          |                 |
  |         |                (Raw Media)           (Media IDs)              (Artefact-Linkage)       |
  |         |                     |                     |                          |                 |
  |  +------v--------+            |            +--------+----------+    +----------+--------------+  |
  |  |    Client     |            |            | Search Override   |    |    Dedupe Clustering    |  |
  |  +---------------+            |            +--------^----------+    +----------^--------------+  |
  |         |                     |                     |                          |                 |
  |     (Media IDs)               v                (Media IDs)             (Pair IDs+Split/Merge)    |
  |         |                     |                     |                          |                 |
  |  +------v---------+           |            +--------+----------+    +----------+--------------+  |
  |  | Media Ingester |           |            | Search Re-Ranking |    |  Dedupe Pairwise Model  |  |
  |  +------+---------+           |            +--------^----------+    +----------^--------------+  |
  |         |                     |                     |                          |                 |
  |    (Raw Media)           (Raw Media)                |                (IDs for Candidate Groups)  |
  |         |                     |                     ^                          |                 |
  |  +------v--------+     +------v-------+             |               +----------+--------------+  |
  |  |  Vectorizer   |     |  Vectorizer  |         (Media IDs)         |   Dedupe All-vs-All NN  |  |
  |  +------+--------+     +------+-------+             |               +-----+----------^--------+  |
  |         |                     |                     |                     |          |           |
  |       (Vectors)           (Vectors)                 |                     |          |           |
  |         |                     |                     |                     |          |           |
  |  +------v-----------+  +------v-----------+         |                     |          |           |
  |  |Vector Compacting |  |Vector Compacting |         ^                     v          ^           |
  |  +------------------+  +------------------+         |                     |          |           |
  |         |                     |                     |                     |          |           |
  |    (Binary Codes)       (Binary Codes)              |                 (Media ID) (Media IDs)     |
  |         |                     |                     |                     |          |           |
  |  +------v-----------+  +------v---------------------+--------+      +-----v----------+--------+  |
  |  |    KNN Index     |  |            KNN Index                |      |        KNN Index        |  |
  |  +------------------+  +-------------------------------------+      +-------------------------+  |
  |                                                                                                  |
  |                                       --Future Indexer--                                         |
  +--------------------------------------------------------------------------------------------------+

  Note: Repeated blocks of same name are the same block.
  Not shown: All components have access to IPFS database for media retrieval.
  Not shown: Training pipeline and statistics instrumentation.
  
```

## Scaling Search:

This section applies only to the `Future Indexer`.

Distributed computing for approximate nearest neighbor (ANN) indexes requires special consideration.

Sharding:

- Typical databases you heavily pre-shard to support future scaling, e.g. pre-sharded into 65000 shards.
- Big efficiency penalties for sharding of ANN indexes. Generally minimal-possible sharding per node is optimal.

- Balance between pre-sharding penalties and re-indexing penalties?
- Consideration needed.

GPU advances make smaller clusters, or even single-machine information retrieval go much further:

   - See the recent e.g. CVPR paper on GPU-based ANN lookup.
   - 1 GPU = ~300x CPU cores.
   - When immediate results are required, limited by dataset size due to GPU RAM.
   - Only worry about failure of a single machine, instead of many. Reduced 95th-percentile worse-case response times?

Typical distributed approach is something like the "shards & replicas" approach of ElasticSearch / Lucene:

   - Each shard sees a subset of the data.
   - Each replica set contains enough shards to fit entire dataset.
   - Each user query is run against all nodes in the replica set and then results are merged.
   - Not optimally efficient, but it simple and it works if you have the resources.
   - Higher 95th-percentile worse-case response times, the greater the number of nodes in your replica set?

More efficient query routing:

   - Similar ANN techniques can be applied at the cluster level, in order to only need to route queries to a smaller fraction of the nodes in a replica set.
   - Question - possible attacks here, since data no longer evenly distributed among nodes?
   - Note: It's a good assumption that perceptual vector-space models never evenly distribute the items across the
     vector metric space. Very clumpy. Not evenly distributed as with cryptographic hashes.


## Other Future Considerations:

+ Object-localization for video / audio / images?
  - System identifies bounding-boxes for multiple objects in an image / time ranges for multiple known audio clips
    in an audio broadcast / time ranges for known video clips.
  - System allows user to focus on just one of those detected objects in their search / dedupe query.
  - Relevant to the search / dedupe architecture, because this likely would require for those multiple objects
    to be indexed separately, along with their location in the media.

+ Allowing user to explicitly describe the type of semantic-similarity he's searching for?
  - Query interpretation was part of textual search engines since very early on - "do know go", but this isn't done for the major image search engines?
  - Allow users to directly tell the system what kind of semantic similarity they're searching for?
    - Some indexing systems don't natively deal with multiple types of semantic similarity, or worse, blur multiple types together into an average that doesn't resemble any meaningful type of semantic similarity.
    - One type of interface: user inputs image - enters a prose description of the type of similarity he's after?
      - pictures containing same brands and trademarks as this picture
      - more pictures in the style of this artist
      - people with same pose as this picture
      - same type of coffee mug as this picture
      - more pictures of from this location
      - more pictures of this person
      - mashups containing this picture
  - Or, we start with a small number of specific types of semantic similarity to start.
  - Range of ways to support this, some relatively simple.

+ Eviction of images from the index?

+ Co-location of images that should be stored together, for faster retrieval / bulk deletion / cache promotion or eviction?



## References

1. [Mediachain Website](http://www.mediachain.io/)
2. [Mediachain RFC 1](https://github.com/mediachain/mediachain/blob/master/rfc/mediachain-rfc-1.md)
3. [Mediachain RFC 1](https://github.com/mediachain/mediachain/blob/master/rfc/mediachain-rfc-2.md)
4. [IPFS](https://ipfs.io/)
5. [IPLD](https://github.com/ipfs/specs/tree/master/ipld)
