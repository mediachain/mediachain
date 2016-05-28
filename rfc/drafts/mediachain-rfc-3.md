<<<<<<< HEAD
# RFC 3: The Mediachain Indexer
=======

# RFC 3: The V1 Indexer
>>>>>>> b4c378d6c91b7b5ddb51c4d24b4913eda0c77cf8

Status: DRAFT

Author: [autoencoder](github.com/autoencoder)


## Overview

<<<<<<< HEAD
This document outlines a proposed `Indexer` system for Mediachain[1]. Core functions of this system include: media ingestion, media search and media deduplication. Specifically, this document focuses on the 1st Generation of the `Indexer` system.

We will also outline a roadmap toward improved, `Future` Indexer generations. Exact details of the `Future` Indexer generations are left to future RFCs.
=======
This document outlines a proposed `Indexer` system for Mediachain[1]. This system will perform media ingestion, media search and media deduplication. Specifically, this document focuses on the first version of the `Indexer` referred to here as `Indexer V1`.

We will also outline a roadmap toward an improved version, referred to here as `Future Indexer`.
>>>>>>> b4c378d6c91b7b5ddb51c4d24b4913eda0c77cf8


## Terminology

<<<<<<< HEAD
Relevant Mediachain terminology background for this RFC. See RFC-1[2] and RFC-2[3] for more information.

- `Indexer` - Collection of Mediachain subsystems described in this RFC.
- `Datastore` - Persistent content-addressed storage system, used to store Mediachain media and blocks. Currently IPFS[4].
- `Journal` - Flattened and partially-reconciled view of the Mediachain knowledge base. Maintained by `Transactors`.
- `Folding` - Producing a single flattened representation of a chain of updates, reconciling any conflicts and discarding bad information in the process.
- `Clients` - Embedded API or external nodes, which are the interface for end-user nodes to receive a stream of updates to the `Journal` from `Transactor` nodes, and for communicating writes to the `Transactors`. They also conduct reads and writes to the `Datastore`.
- `Transactors` - Core of Mediachain. Accepts writes to the `Journal` from `Clients`, serves `Clients` with views of the `Journal`, and records transactions on their behalf. (Referred to as `Peer Nodes` in RFC-2.)
=======
Relevant Mediachain terminolgy background for this RFC. See RFC-1[2] and RFC-2[3] for more information.

- `Indexer` - Mediachain subsystem described in this RFC.
- `Datastore` - Persistent content-addressed storage system, used to store Mediachain media and blocks. Currently IPFS[4].
- `Journal` - Flattened and partially-reconciled view of the Mediachain knowledge base. Maintained by `Transactors`.
- `Folding`: Producing a single flattened representation of a chain of updates, reconciling any conflicts and discarding bad information in the process.
- `Clients` - Embedded API or external nodes, which are the interface for end-user nodes to receive a stream of updates to the `Journal` from `Transactor` nodes, and for communicating writes to the `Transactors`. They also conduct reads and writes to the `Datastore`.
- `Transactors` - Core of Mediachain. Accepts writes to the `Journal` from `Clients`, serves `Clients` with views of the `Journal`, and records transactions on their behalf. (Formerly known as `Peers`.)
>>>>>>> b4c378d6c91b7b5ddb51c4d24b4913eda0c77cf8


## Roadmap

<<<<<<< HEAD
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
=======
`V1 Indexer` will be a minimal proof of concept, while `Future Indexer` represents a set of future enhancements. Key differences are outlined below:


   `V1 Indexer`                                      |  `Future Indexer`
  ---------------------------------------------------|------------------------------------------------------------------------
   Centralized.                                      | All components distributed across multiple machines.
   Off-the-shelf components.                         | Purpose-built components.
   Single perceptual matching goal.                  | Multiple semantic matching intents supported.
   No supervised training.                           | Custom supervised relevancy / dedupe training for specific task(s) and dataset at hand.
>>>>>>> b4c378d6c91b7b5ddb51c4d24b4913eda0c77cf8
   No multi-object detection.                        | Multiple objects detectable per media object, via saliency detection / object localization.
   No explicit user relevancy feedback.              | Accepting explicit user feedback signals.
   No implicit user behavioral training.             | Training based on implicit user behavior signals.
   Basic attack mitigation and spam control.         | Sophisticated attack mitigation.
   Basic or no regression testing.                   | System to detect regressions caused by newly trained models.


<<<<<<< HEAD
In summary, the 1st Generation `Indexer` is a minimal self-hosted system, composed of off-the-shelf open source components, along with custom components for implementation abstraction. Custom components include a wrapping JSON/REST API for providing back-end abstraction, a gRPC API interface for ingesting media from Mediachain, and a gPRC API interface for writing deduplication results back out to the Mediachain blockchain. The 1st Generation `Indexer` will rely on unsupervised, engineered features for semantic matching, while the `Future` Indexer will evolve toward fully-supervised models for training and hyper-parameter tuning, and reducing the manual feature engineering.

The 1st Generation `Indexer` will make use of technologies such as: off-the-shelf full-text-search systems, engineered feature generators such as DHash, PHash, SIFT, or SURF for content-based retrieval and matching, and manual hyper-parameter tuning.

`Future` Indexer enhancements will rely on technologies including: convolutional neural networks for feature extraction, neural sequence-to-sequence semantic similarity models, neural attention mechanisms for high quality semantic similarity matching and query re-ranking, end-to-end supervised training, and a comprehensive scoring & regression-testing framework.


## Subsystems Overview

Functions performed by the `Indexer` can be broken down into the following subsystems and lifecycles:

- **Ingestion** - `Transactors` feed the `Ingestion` subsystem with newly created media works. The media works are semantically indexed for later search and deduplication.
- **Search** -  End-user submits a media file or media ID, a list of most semantically relevant media IDs is returned.
- **Dedupe** - Determine which media in the index are duplicates / non-duplicates. Submit a subset of these results to the `Transactors`. This process can be initiated periodically to reprocess all media in the search index - for example after each time the dedupe models are considerably re-trained. Or, it can act only on new media from the `Journal`, received via the `Client`.
- **Model Training & Hyper-Parameter Tuning Lifecycle** - Background training and tuning process that updates the information retrieval / dedupe models and tunes hyper-parameters. After each re-training, models are regression tested, put into production where live-user regression metrics are again measured. If major regressions are detected, models and hyper-parameters are reverted to an earlier version.


## Ingestion Subsystem

The `Ingestion` subsystem accepts media works from the `Transactors` via the `Clients`, performs basic attack
mitigation and media prioritization, and then processes the media objects into image feature descriptors. These
feature descriptors, along with the media IDs are then inserted into the `KNN Index`, which will be queried by
the `Search` and `Dedupe` systems.

User interfaces to the `Ingestion`  subsystem will in coordination with the `Dedupe` subsystem, to alert users if they are
about to create duplicate entries for the same work in the MediaChain blockchain, or allow users to provide a negative training
signal to the deduplication subsystem in the case where media works have been incorrectly flagged as duplicates.

In the case when an exact match of media content, which was not caught by the MediaChain core - for example images files with the same
image content but differing EXIF headers, the `Ingestion` subsystem may, at insertion time, attempt to force these media works to resolve
to the same blockchain ID.

Future `Ingestion` subsystem enhancements include receiving indexing data directly from users who are inserting media works, in
order to minimize indexing delays, and adding more sophisticated APIs for user feedback and querying.


## Search Subsystem

The `Search` system is exposed to end-users via a REST/JSON API. The address of the REST API will be communicated to end-users via the
Mediachain Core. This API allows users to input a query or media ID, and receive a ranked list of most semantically-relevant media IDs.


 Method | REST Call     | Description                                       | Returns
 -------|---------------|---------------------------------------------------|---------------------------------------------------
 POST   | `/search`     | Search by base64-encoded image or image ID.       | List of image IDs, possibly with relevancy scores.
 POST   | `/distance`   | Calculate metric-space distance between 2 images. | Numeric distance between 0.0 and 1.0.
 POST   | `/ping`       | Uptime check.                                     | See `'error'` entry.


At a later stage, this subsystem may also accept relevancy feedback from end-users:


 Method | REST Call           | Description                                        | Returns
 -------|---------------------|----------------------------------------------------|-------------------------------
 POST   | `/record_relevancy` | Accept user relevancy feedback for a search query. | See `'error'` entry.


#### Endpoint: `/search`

Description: Search by base64-encoded image or image ID.

Input: JSON-encoded payload, with keys and values of the following types:


Key             | Value
----------------|--------------------------------------------------
q_text          | String - text query for text-based query.
q_id            | String - IFPS media content address.
q_base64        | String - Base64-encoded media object.
q_facets        | Dictionary - Key-value dictionary of query requirements. For `Future`.
limit           | Integer - Maximum number of results to return.


Outputs: List of relevant media IDs in the `'data'` entry. See value of `'error'` entry in case of error.


#### Endpoint: `/distance`

Description: Calculate metric-space distance between 2 images.

Input: JSON-encoded payload, with keys and values of the following types:


Key              | Value
-----------------|--------------------------------------------------
q_media_ids      | List of Strings - IFPS media content addresses.
q_media_base64s  | List of Strings - Base64-encoded media objects.


Outputs: List of relevant media IDs. See value of `'error'` entry in case of error.


#### Endpoint: `/record_relevancy`

Description: Not present in 1st Generation `Indexer`. Accept user relevancy feedback for a search query. Possibly provides instant attack mitigation for certain types of rapidly-emerging attacks.

Input: JSON-encoded payload, indicating


Key              | Value
-----------------|-------------------------------------------------
q_media_ids      | List of Strings - IFPS media content addresses.
q_media_base64s  | List of Strings - Base64-encoded media objects.


Outputs: List of relevant media IDs. See value of `'error'` entry in case of error.


## Dedupe Subsystem

The 1st Generation `Indexer` `Dedupe` subsystem will initially consume media from the search index, and write duplicates using `ArtefactLinkCell` commands sent to `Transactors`.

At a later stage, it may consume training data from external resources including `ArtefactLinkCell` commands created by other nodes, and receive linking information from JSON/REST API functions to allow end users to indicate duplicate media.


Method calls, to be expanded in `Future` Indexer generations:


 Method | REST Call | Description | Returns
 -----|---------------------|---------------------------------------------------------------------|-----------------------------------------
 POST | `/dupe_check`       | Return calibrated-probability that a pair of images are duplicates. | Estimated probability, between 0.0 and 1.0.
 POST | `/record_dupe`      | User labels a set of images as dupes / non-dupes. This is added to training data. | Check for `'error'` entry.
 POST | `/record_relevancy` | User labels the relevancy of a set of search results for a query.   | Check for `'error'` entry.


## Architecture

The following describes a high-level overview of the Ingestion, Search, and Dedupe operation modes for the 1st Generation `Indexer`.

Due to overlapping functionality, some components are shared between subsystems. Despite the overlap, the subsystems can be decoupled, such that each set of mode of operation can be done on separate nodes with infrequent communication needed among them.
=======
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
>>>>>>> b4c378d6c91b7b5ddb51c4d24b4913eda0c77cf8


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
<<<<<<< HEAD
  |         |                  (Query:)      |                      |                 |
  |         |                (Raw-Media) (Media-IDs)         (Artefact-Linkage)       |
  |         |                 (or text)      |                      |                 |
  |  +------v--------+      (or Media ID)    |           +----------+--------------+  |
=======
  |         |                     |          |                      |                 |
  |         |                (Raw-Media) (Media-IDs)         (Artefact-Linkage)       |
  |         |                     |          |                      |                 |
  |  +------v--------+            |          |           +----------+--------------+  |
>>>>>>> b4c378d6c91b7b5ddb51c4d24b4913eda0c77cf8
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
<<<<<<< HEAD
  |  +------v--------+     +----v----------+    |        +----------+--------------+  |
  |  | Gen Features  |     | Gen Features  | (Media IDs) |  Dedupe All-vs-All NN   |  |
  |  +------+--------+     +----+----------+    |        +-----+----------^--------+  |
  |         |                   |               ^              |          |           |
  |     (Features)          (Features)          |          (Media ID) (Media IDs)     |
=======
  |  +------v--------+     +----v--------+      |        +----------+--------------+  |
  |  |  Vectorizer   |     |  Vectorizer |  (Media IDs)  |  Dedupe All-vs-All NN   |  |
  |  +------+--------+     +----+--------+      |        +-----+----------^--------+  |
  |         |                   |               ^              |          |           |
  |     (Vectors)           (Vectors)           |          (Media ID) (Media IDs)     |
>>>>>>> b4c378d6c91b7b5ddb51c4d24b4913eda0c77cf8
  |         |                   |               |              |          |           |
  |  +------v-----------+  +----v---------------+-+      +-----v----------+--------+  |
  |  |    KNN Index     |  |       KNN Index      |      |        KNN Index        |  |
  |  +------------------+  +----------------------+      +-------------------------+  |
  |                                                                                   |
<<<<<<< HEAD
  |                           --1st Generation Indexer--                              |
=======
  |                                --V1 Indexer--                                     |
>>>>>>> b4c378d6c91b7b5ddb51c4d24b4913eda0c77cf8
  +-----------------------------------------------------------------------------------+

  Note: Repeated blocks of same name are the same block.
  Not shown: All components have access to IPFS database for media retrieval.

```

<<<<<<< HEAD
## Low-level Indexer Components

Major low-level components of the `Indexer` are as follows. Those aspects marked `Future` do not apply to the 1st Generation `Indexer`.

+ `Media Ingester`
  - Listens for new media from the `Journal` via a `Client`. Passes results to `Gen Features`.
=======
## Indexer Components

The following are the major components of the `Indexer`. Those marked `Future Indexer` only apply to that version.

+ `Media Ingester`
  - Listens for new media from the `Journal` via a `Client`. Passes results to `Vectorizer`.
>>>>>>> b4c378d6c91b7b5ddb51c4d24b4913eda0c77cf8

+ `HTTP Search API`:
    - Services user search queries.
    - Asynchronous batching enqueues tasks, until timeout is reached or queue reaches a specified size.

<<<<<<< HEAD
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
=======
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
>>>>>>> b4c378d6c91b7b5ddb51c4d24b4913eda0c77cf8

+ `Dedupe All-vs-All NN`:
    - Retrieve list of candidate nearest-neighbors media objects, for all media objects.

+ `Search Re-Ranking`
<<<<<<< HEAD
    - `Future`: High-precision, slower model that re-ranks all candidate query results output from the `KNN-Index` lookup.
    - `Future`: Typically involves in-depth attention to each pair of query vs candidate, in order to produce a relevancy
      score for each candidate media work.

+ `Search Override`
    - `Future`: Overrides potentially poor relevancy output of the underlying search system with known-good answers.
    - `Future`: Assists in mitigating certain types of attacks.
    - `Future`: Provides possibility for instant updates, versus requiring slow model-retraining before results are visible.
=======
    - `Futuer Indexer` only:
    - Slower model that ranks all candidate media from the `KNN-Index` lookup, based on relevancy to the query.
    - Typically involves in-depth attention to each pair of query vs candidate, in order to produce a relevancy score for each candidate media work.

+ `Search Override`
    - `Future Indexer` only:
    - Able to override potentially poor relevancy output of the underlying search system with known-good answers.
    - Assists in mitigating certain types of attacks.
    - Provides possibility for instant updates, versus requiring slow model-retraining before results are visible.
>>>>>>> b4c378d6c91b7b5ddb51c4d24b4913eda0c77cf8

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
<<<<<<< HEAD
    - `Future` will have additional scoring and regression checks here.


## Model Training & Hyper-Parameter Tuning Lifecycle

Supervised training data derived from explicit training feedback may be obtained from sources including: 3rd party training data sources,
external manual annotators, and trusted in-house annotators. Implicit training data may also be derived from sources such as: user signals
such as click-through rates, dwell time, or other behavioral signals.

As the `Indexer` advances from being based on engineered features configured using standard settings to a system based on supervised
training data ingested data from potentially harmful sources and carefully-tuned hyper-parameters, an update lifecycle will be adopted. This
update lifecycle consumes a new batch of training data or system hyper-parameter updates, performs regression checks to ensure
the quality of the updates, and possibly reverts to an older batch of trained models and system settings, if necessary.


(1) 1st Generation `Indexer` will initially use unsupervised, engineered features, with no labeled training data present and no regression checking:

```
     /------<----(no tuning)----<------\
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
=======
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
>>>>>>> b4c378d6c91b7b5ddb51c4d24b4913eda0c77cf8

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
<<<<<<< HEAD
  |  | Gen Features  |     | Gen Features |         (Media IDs)         |   Dedupe All-vs-All NN  |  |
  |  +------+--------+     +------+-------+             |               +-----+----------^--------+  |
  |         |                     |                     |                     |          |           |
  |   (Descriptors)         (Descriptors)               |                     |          |           |
  |         |                     |                     |                     |          |           |
  |  +------v-----------+  +------v-----------+         |                     |          |           |
  |  |Feature Compacting|  |Feature Compacting|         ^                     v          ^           |
=======
  |  |  Vectorizer   |     |  Vectorizer  |         (Media IDs)         |   Dedupe All-vs-All NN  |  |
  |  +------+--------+     +------+-------+             |               +-----+----------^--------+  |
  |         |                     |                     |                     |          |           |
  |       (Vectors)           (Vectors)                 |                     |          |           |
  |         |                     |                     |                     |          |           |
  |  +------v-----------+  +------v-----------+         |                     |          |           |
  |  |Vector Compacting |  |Vector Compacting |         ^                     v          ^           |
>>>>>>> b4c378d6c91b7b5ddb51c4d24b4913eda0c77cf8
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
<<<<<<< HEAD

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
=======
  
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

>>>>>>> b4c378d6c91b7b5ddb51c4d24b4913eda0c77cf8


## References

1. [Mediachain Website](http://www.mediachain.io/)
2. [Mediachain RFC 1](https://github.com/mediachain/mediachain/blob/master/rfc/mediachain-rfc-1.md)
3. [Mediachain RFC 1](https://github.com/mediachain/mediachain/blob/master/rfc/mediachain-rfc-2.md)
4. [IPFS](https://ipfs.io/)
<<<<<<< HEAD
=======
5. [IPLD](https://github.com/ipfs/specs/tree/master/ipld)
>>>>>>> b4c378d6c91b7b5ddb51c4d24b4913eda0c77cf8
