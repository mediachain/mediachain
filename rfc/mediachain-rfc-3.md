# RFC 3: The Mediachain Indexer

Status: DRAFT

Author: [autoencoder](github.com/autoencoder)


## Overview

This document outlines a proposed `Indexer` system for Mediachain[1]. Core functions of this system include: media ingestion, media search and media deduplication. Specifically, this document focuses on the 1st Generation of the `Indexer` system.

We will also outline a roadmap toward improved, `Future` Indexer generations. Further details of the `Future` Indexer generations are left to future RFCs.


## Terminology

Relevant Mediachain terminology background for this RFC. See RFC-1[2] and RFC-2[3] for more information.

- `Indexer` - Collection of Mediachain subsystems described in this RFC.
- `Datastore` - Persistent content-addressed storage system, used to store Mediachain media and blocks. Currently IPFS[4].
- `Journal` - Flattened and partially-reconciled view of the Mediachain knowledge base. Maintained by `Transactors`.
- `Folding` - Producing a single flattened representation of a chain of updates, reconciling any conflicts and discarding bad information in the process.
- `Clients` - Embedded API or external nodes, which are the interface for end-user nodes to receive a stream of updates to the `Journal` from `Transactor` nodes, and for communicating writes to the `Transactors`. They also conduct reads and writes to the `Datastore`.
- `Transactors` - Core of Mediachain. Accepts writes to the `Journal` from `Clients`, serves `Clients` with views of the `Journal`, and records transactions on their behalf. (Referred to as `Peer Nodes` in RFC-2.)
- `Media Identifier` - One of several types of strings that can be used to indicate the identity of a media work. Most Indexer subsystems will resolve this identifier into a canonical ID. See more in the `Media Identifiers` section.


## Roadmap

The following is an overview of the 1st Generation `Indexer`, versus enhancements that may be incorporated into `Future` Indexer generations:


   `1st Generation Indexer`                          | `Future`
  ---------------------------------------------------|------------------------------------------------------------------------
   Centralized.                                      | All components distributed across multiple machines.
   Manual feature engineering.                       | Learned features via training supervision.
   Single modality of query input (text metadata matching).    | Queries composed of both media and text of multiple intent types, with sophisticated cross-modality query composition.
   Basic search based primarily on text metadata.    | Deep semantic content-based media retrieval, jointly trained to interpret query intent.
   Generic, unsupervised models.                     | Custom supervised relevancy / dedupe training for specific tasks and datasets at hand.
   Off-the-shelf components.                         | Purpose-built components.
   Single perceptual matching goal.                  | Multiple semantic matching intents supported.
   No multi-object detection.                        | Multiple objects detectable per media object, via saliency detection / object localization.
   No explicit user relevancy feedback.              | Accepting explicit user feedback signals.
   No implicit user behavioral training.             | Training based on implicit user behavior signals.
   Basic attack mitigation and spam control.         | Sophisticated attack mitigation.
   Basic or no regression testing.                   | System to detect regressions caused by newly trained models.


In summary, the 1st Generation `Indexer` is a minimal self-hosted system, composed of off-the-shelf open source components, along with custom components for implementation abstraction. Custom components include a wrapping JSON/REST API for providing back-end abstraction, a gRPC API interface for ingesting media from Mediachain, and a gPRC API interface for writing deduplication results back out to the Mediachain blockchain. The 1st Generation `Indexer` will rely on unsupervised, engineered features for semantic matching, while the `Future` Indexer will evolve toward fully-supervised models for training and hyper-parameter tuning, and reducing the manual feature engineering.

The 1st Generation `Indexer` will make use of technologies such as: off-the-shelf full-text-search systems, engineered feature generators such as DHash, PHash, SIFT, or SURF for content-based retrieval and matching, and manual hyper-parameter tuning.

`Future` Indexer enhancements will rely on technologies including: convolutional neural networks for feature extraction, neural sequence-to-sequence semantic similarity models, neural attention mechanisms for high quality semantic similarity matching and query re-ranking, end-to-end supervised training, and a comprehensive scoring & regression-testing framework.

## REST API Overview

The following API calls are detailed in the sections below.

 Method | REST Call     | Description                                       | Returns
 -------|---------------|---------------------------------------------------|---------------------------------------------------
 POST   | `/search`     | Search for images based on text query, a media work, or a combination of both.   | List of image IDs, possibly with relevance scores.
 POST   | `/dupe_lookup`      | Find all known duplicates of a media work.  | List of IDs that are duplicates of the query media ID.
 POST   | `/score` | Tool for peering deeper into the similarity / relevance measurements that are the basis of dedupe / search calculations.  | List of similarities or duplicate probabilities, one per similarity or duplicate type.
 POST   | `/ping`             | System status check.                               | See `'error'` entry.
 POST   | `/record_dupe`      | User labels a set of images as dupes / non-dupes. This is added to training data. | Check for `'error'` entry.
 POST   | `/record_relevance` | Accept user relevance feedback for a search query. | See `'error'` entry.


## Subsystems Overview

Functions performed by the `Indexer` can be broken down into the following subsystems and lifecycles:

- **Ingestion** - `Transactors` feed the `Ingestion` subsystem with newly created media works. The media works are semantically indexed for later search and deduplication.
- **Search** -  End-user submits a media file or media ID, a list of most semantically relevant media IDs is returned.
- **Dedupe** - Determine which media in the index are duplicates / non-duplicates. Submit a subset of these results to the `Transactors`. This process can be initiated periodically to reprocess all media in the search index - for example after each time the dedupe models are considerably re-trained. Or, it can act only on new media from the `Journal`, received via the `Client`.
- **Model Training & Hyper-Parameter Tuning Lifecycle** - Background training and tuning process that updates the information retrieval / dedupe models and tunes hyper-parameters. After each re-training, models are regression tested, put into production where live-user regression metrics are again measured. If major regressions are detected, models and hyper-parameters are reverted to an earlier version.


## REST API Endpoints Overview

The following apply to all REST API endpoints.

##### Media Identifiers:

Media works can be identified by strings in any of the following formats:

- IPFS ID string. I.e. starting with `QmPX`.
- PNG or JPG file, with a maximum size of 28,900 pixels, encoded using the Data URI Scheme[6]. I.e. starting with `data:`.

##### Input format:

Body of POST request is a JSON-encoded string. 

##### Returned on success:

- `results`:       List of results.
- `next_page`:     Pagination link.
- `prev_page`:     Pagination link.

##### Returned on error:

- `error`:         Error code.
- `error_message`: Error message.


## Ingestion Subsystem and Endpoints

The `Ingestion` Subsystem accepts media works from the `Transactors` via the `Clients`, performs basic attack mitigation and media
prioritization, and then processes the media objects into image feature descriptors. These feature descriptors, along with the media IDs
are then inserted into the `KNN Index`, which will be queried by the `Search` and `Dedupe` systems.

User interfaces to the `Ingestion`  subsystem will in coordination with the `Dedupe` subsystem, to alert users if they are
about to create duplicate entries for the same work in the MediaChain blockchain, or allow users to provide a negative training
signal to the deduplication subsystem in the case where media works have been incorrectly flagged as duplicates.

In the case when an exact match of media content, which was not caught by the MediaChain core - for example images files with the same
image content but differing EXIF headers, the `Ingestion` subsystem may, at insertion time, attempt to force these media works to resolve
to the same blockchain ID.

Future `Ingestion` subsystem enhancements include receiving indexing data directly from users who are inserting media works, in
order to minimize indexing delays, and adding more sophisticated APIs for user feedback and querying.

TODO: Documenting gRPC ingestion feed API.


## Search Subsystem and Endpoints

The `Search` subsystem allows users to input a textual query or `media identifier`, and receive back a ranked list of most semantically-relevant media IDs.

#### Endpoint: /search

Description: Search for images based on text query, a media work, or a combination of both.

Inputs:

Key             | Value
----------------|--------------------------------------------------
  q             | Query text.
  q_id          | Query media. See `Media Identifiers`.
  limit         | Maximum number of results to return.
  inline_images | Whether to include base64-encoded thumbnails of images directly in the results.

Outputs: List of image IDs, possibly with relevance scores.


#### Endpoint: /record_relevance

Description: Accepts explicit user relevance feedback for a search query. Not present in 1st Generation `Indexer`.

Inputs:

Key              | Value
-----------------|-------------------------------------------------
  q              | Query text.
  q_id           | Query media. See `Media Identifiers`.
  r_ids          | Result media ids with scores.


## Dedupe Subsystem and Endpoints

Consumes already-ingested media from the `kNN Index`, and writes duplicate links back out to the blockchain. This creates a duplicates mapping which can be committed to the blockchain, queried by end users, or used to help end users resolve the best artefact identity of a media work. The `Dedupe` subsystem may consume training data from external resources and receive dedupe feedback from end users via the API Endpoints.

#### Endpoint: /dupe_lookup

Description: Find all known duplicates of a media work.

Inputs:

Key              | Value
-----------------|-------------------------------------------------
q_media          | Media file to query for.
duplicate_mode   | Semantic duplicate type or matching mode. For now, defaults to 'baseline'.
incremental      | Attempt to dedupe never-before-seen media file versus all pre-ingested media files.


Outputs: List of matching media IDs of the form: `[{'id':'MEDIA_ID'},...]`



#### Endpoint: /record_dupe

Description: Accept duplicate / non-duplicate feedback from an end-user.

Inputs:

Key              | Value
-----------------|-------------------------------------------------
  as_groups      | List of lists of media IDs, with grouped IDs all considered duplicates, and split IDs all considered non-duplicates.
  as_pairs       | Pairs of of media IDs, with a "same" or "different" labels. Pairs must be transitively consistent.

Outputs: See `'error'` entry.


## Other Endpoints

The following endpoints are common to multiple `Indexer` subsystems.


#### Endpoint: /score

Description: Tool for peering deeper into the similarity / relevance measurements that are the basis of dedupe / search calculations. Useful for e.g. getting a feel for why an image didn't show up in the top 100 results for a query, or why a pair of images weren't marked as duplicates. Takes a "query" and list of "candidate" media, and does 1-vs-all score calculations for all "candidate" media versus the "query".

Inputs:


Key     | Value
--------|--------------------------------------------------
q_text  | Query text.
q_id    | Query media. See `Media Identifiers`.
c_ids   | List of candidate media. See `Media Identifiers`.
mode    | Type of similarity to measure. Should be one of: 'search' - Search relevance score. 'dupe'   - Duplicate probability.
level   | Level of the model at which to measure the similarity. One of: 'similarity' - Similarity in the embedding space(s) (0.0 to 1.0). 'score' - Final relevance score for "search" mode (1.0 to 5.0) or final dupe probability score for "dupe" mode (0.0 to 1.0).

Outputs: List of similarities or duplicate probabilities, one per similarity or duplicate type.


#### Endpoint: /ping

Description: System status check.

Inputs: NONE

Outputs: The string "pong".


## Architecture

The following describes a high-level overview of the Ingestion, Search, and Dedupe operation modes for the 1st Generation `Indexer`.

Due to overlapping functionality, some components are shared between subsystems. Despite the overlap, the subsystems can be decoupled, such that each set of mode of operation can be done on separate nodes with infrequent communication needed among them.


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
  |         |                  (Query:)      |                      |                 |
  |         |                (Raw-Media) (Media-IDs)         (Artefact-Linkage)       |
  |         |                 (or text)      |                      |                 |
  |  +------v--------+      (or Media ID)    |           +----------+--------------+  |
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
  |  +------v--------+     +----v----------+    |        +----------+--------------+  |
  |  | Gen Features  |     | Gen Features  | (Media IDs) |  Dedupe All-vs-All NN   |  |
  |  +------+--------+     +----+----------+    |        +-----+----------^--------+  |
  |         |                   |               ^              |          |           |
  |     (Features)          (Features)          |          (Media ID) (Media IDs)     |
  |         |                   |               |              |          |           |
  |  +------v-----------+  +----v---------------+-+      +-----v----------+--------+  |
  |  |    KNN Index     |  |       KNN Index      |      |        KNN Index        |  |
  |  +------------------+  +----------------------+      +-------------------------+  |
  |                                                                                   |
  |                           --1st Generation Indexer--                              |
  +-----------------------------------------------------------------------------------+

  Note: Repeated blocks of same name are the same block.
  Not shown: All components have access to IPFS database for media retrieval.

```

## Low-Level Components

Major low-level components of the `Indexer` are as follows. Those aspects marked `Future` do not apply to the 1st Generation `Indexer`.

+ `Media Ingester`
  - Listens for new media from the `Journal` via a `Client`. Passes results to `Gen Features`.

+ `HTTP Search API`:
    - Services user search queries.
    - Asynchronous batching enqueues tasks, until timeout is reached or queue reaches a specified size.

+ `Gen Features`:
    - In 1st Generation `Indexer`, this generates simple, engineered features. In `Future` Indexer, this generates features
      learned via training supervision.
    - Tuned for high-recall, versus the `Search Re-Ranking` or `Dedupe Pairwise` stages, which are slower and tuned for precision.
    - `Future`: support indexing of multiple objects per media content, possibly annotated with locations at which each object appears
      in the media.
    - `Future`: output multiple feature vectors / descriptors per media work / object in media work, each corresponding to a different
      type of semantic similarity.

+ `Feature Compacting`:
    - `Future`: depending on the models chosen and efficiency / accuracy tradeoffs, this additional component would be added to compact feature
       descriptors into more compact binary codes, while minimizing accuracy loss.

+ `KNN Index`:
    - Stores document feature vectors, and does k-nearest-neighbor lookups on those vectors.
    - 1st Generation `Indexer` will be distributed across worker machines using the "shards & replicas" approach.
      Queries will be routed to one of the replica sets, and then routed to all shards in that replica set. Results
      from all shards in the replica set are then combined, ranked, and returned upstream.

+ `Dedupe All-vs-All NN`:
    - Retrieve list of candidate nearest-neighbors media objects, for all media objects.

+ `Search Re-Ranking`
    - `Future`: High-precision, slower model that re-ranks all candidate query results output from the `KNN-Index` lookup.
    - `Future`: Typically involves in-depth attention to each pair of query vs candidate, in order to produce a relevancy
      score for each candidate media work.

+ `Search Override`
    - `Future`: Overrides potentially poor relevancy output of the underlying search system with known-good answers.
    - `Future`: Assists in mitigating certain types of attacks.
    - `Future`: Provides possibility for instant updates, versus requiring slow model-retraining before results are visible.

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
    - `Future` will have additional scoring and regression checks here.


## Model Training & Hyper-Parameter Tuning Lifecycle

Supervised training data derived from explicit training feedback may be obtained from sources including: 3rd party training data sources,
external manual annotators, and trusted in-house annotators. Implicit training data may also be derived from sources such as: user signals
such as click-through rates, dwell time, or other behavioral signals.

As the `Indexer` advances from being based on engineered features configured using standard settings to a system based on supervised
training data ingested data from potentially harmful sources and carefully-tuned hyper-parameters, an update lifecycle will be adopted. This
update lifecycle consumes a new batch of training data or system hyper-parameter updates, performs regression checks to ensure
the quality of the updates, and possibly reverts to an older batch of trained models and system settings, if necessary.


(1) 1st Generation `Indexer` will initially use unsupervised manual feature engineering, manual tuning of hyper-parameters, and no regression checking:

```
     /----<----(manual tuning)---<-----\
     |                                 |
     |                                 ^
+----v-----+  +--------------------+   |
|user query+->|run query on        +->-/
+----------+  |for search / dedupe |
              |system based on     |
              |unsupervised,       |
              |engineered features.|
              +--------------------+
```

(2) `Future:` In the next stage, supervised training data is used for hyper-parameter tuning of the featured-engineered components:

```
     /--------<------(updated models / hyper-parameters)-------<--------\
     |                                                                  |
+----v-----+  +-----------+  +-------------------+  +---------------+   ^
|user query+->| run query |  | annotate results  |  |hyper-parameter|   |
+----------+  | on search +->| w/ 1-5 relevancy, +->|   tuning      +->-/
              | or dedupe |  |or match/non-match.|  |hyper-parameter|
              +-----------+  +-------------------+  |    tuning.    |
                                                    +---------------+

```

(3) `Future:` Finally, supervised training data for supervised training, hyper-parameter optimization,
and regression monitoring with ability to revert model and hyper-parameter updates:

```
     /-------------<--------------(updated models / hyper-parameters)-----------<-------------------\
     |                                                                                              |
+----v-----+  +-----------+  +----------+  +---------------+  +----------------+  +-------------+   |
|user query+->| run query +->| annotate +->|  supervised   +->|Regression check+->| Regression  +->-/
+----------+  | on search |  |relevancy,|  | training and  |  |against holdout |  |check against|
              | or dedupe.|  |  match / |  |hyper-parameter|  |prior to pushing|  |  live user  |
              +-----------+  |non-match.|  |    tuning.    |  |live.           |  |   metrics.  |
                             +----------+  +-----^---^-----+  +---------+------+  +------+------+
                                                 |   |                  |                |
                                                 ^   \--<--(Revert)--<--/                v
                                                 |                                       |
                                                  \-------<-------(Revert)--------<------/
```

## Indexed Media Lifecycle

The 1st Generation `Indexer` indiscriminately indexes all media works relayed to it from the blockchain, caches media
works to accelerate possible future reindexing, and does not trim these caches and indexes.

`Future` Indexer generations may require systems for prioritizing the use of the caches and `KNN Index`, evicting spam or other undesirable records
from the Indexer, or otherwise compacting the indexes. Operators of Indexer nodes may also choose to dedicate their resources to specializing in media works of particular types or "freshness".

Effort is underway for enhancements allowing rapid eviction or re-prioritization of existing media works tracked by the `Indexer`. These include carefully-designed pre-sharding, optimizing efficiency of re-indexing, and advanced routing of media works and queries to nodes. We leave the details of these advancements to future RFCs.


## Future Architecture Overview

`Future` Indexer generations will feature an expanded architecture and enhancements to existing components:

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
  |  | Gen Features  |     | Gen Features |         (Media IDs)         |   Dedupe All-vs-All NN  |  |
  |  +------+--------+     +------+-------+             |               +-----+----------^--------+  |
  |         |                     |                     |                     |          |           |
  |   (Descriptors)         (Descriptors)               |                     |          |           |
  |         |                     |                     |                     |          |           |
  |  +------v-----------+  +------v-----------+         |                     |          |           |
  |  |Feature Compacting|  |Feature Compacting|         ^                     v          ^           |
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

Content-based media matching & retrieval is a rapidly evolving space. As listed in the `Roadmap` section above, the `Future` Indexer
may adopt technological enhancements such as:

+ Shifting away from engineered features to supervised training and minimizing manual feature engineering.

+ Multi-object localization for detecting multiple salient objects in media works.

+ Support for natural language object localization description.

+ Support for natural language descriptions of semantic-similarity intent.

+ Eviction of images from the index and caches.

+ Co-location of media content in indexes and cache systems for faster retrieval and bulk eviction.

Details of these enhancements are left to future RFCs.


## References

1. [Mediachain Website](http://www.mediachain.io/)
2. [Mediachain RFC 1](https://github.com/mediachain/mediachain/blob/master/rfc/mediachain-rfc-1.md)
3. [Mediachain RFC 1](https://github.com/mediachain/mediachain/blob/master/rfc/mediachain-rfc-2.md)
4. [IPFS](https://ipfs.io/)
5. [IPLD](https://github.com/ipfs/specs/tree/master/ipld)
6. [Data URI Scheme](https://en.wikipedia.org/wiki/Data_URI_scheme)
