# Idea Sketches:

Rough sketches of some high-level ideas. Some sections to be re-drafted into RFCs. Contents:

- General questions & ideas.
- Various term definitions.
- WIP outline for the V1 of search & dedupe architecture(s).
- Beginnings of iteration roadmap toward distributed search & dedupe.


## GENERAL MC QUESTIONS / IDEAS:

+ In addition to edges that support a relationship, also support edges that deny a relationship?
  - E.g. `NotArtefactDerivationCell`, `NotArtefactCreationCell`, etc.
  - Or, adding an "invert_relationship" field for the EntityLinkCell. Not to be confused with
    inverting the direction of the relationship.
  
+ Support a denser format for specifying edges, other than just one pair at a time?
  - E.g. `EntityLinkCell = {"group_entity_link":[(ref1,ref2,ref3),(ref4,ref5),(ref6)]}`
  - Meaning that all grouped entities in that list should have the indicated relationship, and all separated
    entities should have the inverse relationship. I.e. is_derivation vs is_not_derivation.
  - Could be easily converted to all-pairs of links internally.

+ Object-localization for video / audio / images?
  - System identifies bounding-boxes for multiple objects in an image / time ranges for multiple known audio clips
    in an audio broadcast / time ranges for known video clips.
  - System allows user to focus on just one of those detected objects in their search / dedupe query.
  - Relevant to the search / dedupe architecture, because this likely would require for those multiple objects
    to be indexed separately, along with their location in the media.

+ Non-monetary incentives for resource providers? I.e. User donates resources because he likes particular content.
  - Force resource providers to support all content equally (Freenet).
  - Allow full control over the content you're supporting (Bittorrent).
  - Something in the middle (Tor exit nodes)?

+ Resource usage considerations?
  - In non-P2P systems, the equation to optimize for tends to be simple. E.g. maximizing utilization of already-owned hardware,
    or maximizing per-dollar results.
  - Costs can be different in P2P systems. Some users can get upset and leave, if all of their resources are
    immediately maxed out.
  - Considering that P2P networks tend to have a power-law distribution of resources.

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


## V1 SEARCH / DEDUPE API:


- POST `/add`        - Add image. Image passed as base64-encoded text. Returns image ID, or, images ID'd by cryptographic hash.
- POST `/remove`     - Mark image for removal from index.
- POST /search     - Search by base64-encoded image or image ID. Returns list of image IDs, possibly with relevancy scores.
- POST `/distance`   - Calculate metric-space distance between 2 images.
- POST `/dupe_check` - Return calibrated-probability that a pair of images are duplicates.
- GET  `/ping`       - Uptime check.
- POST `/record_dupe`      - User labels a set of images as dupes / non-dupes. This is added to training data.
- POST `/record_relevancy` - User labels the relevancy of a set of search results for a query. This is added to training data.


TODO:

- Fill in exact details of JSON parameters for above endpoints.
- `/dupe_add` and `/relevancy_rate` - these are training feedback endpoints. Other types of training data and hyper-parameter settings will be also fed into the system elsewhere, but these two endpoints are included here because they may be end-user-facing?
-   Perhaps these should be an immediate feedback tool, whose input supersedes that from the slower index re-training process?


## SEARCH / DEDUPE ROADMAP:

Version 1:

  - Centralized.
  - Off-the-shelf components.
  - No focus on provider incentives.
  - Unsupervised / distantly-supervised models.
  - No custom relevancy / dedupe training.
  - Skipping some components in V1, e.g. the "high-accuracy-model". See below.
  - Generic wrapping APIs for each component, so they can be easily swapped out for others.

Long-term:

  - Distributed across machines.
  - Distributed across users. Anyone can run an indexing node helps serve someone else's index(?)
  - More attention to optimizing incentives among resource-providers / service-providers.
  - Custom-trained for these particular relevancy / dedupe tasks & datasets.
  - Accepting explicit user feedback signals(?)
  - Mitigating attacks: resource exhaustion, bad feedback, high-trust actors gone bad, etc.
  - Other possible ideas, see below.


## SCALING ANN INDEXES:

Distributed computing for approximate nearest neighbor (ANN) indexes requires some special consideration.

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


## SEARCH / DEDUPE HIGH-LEVEL ARCHITECTURE:

Note: Some long-term components included. Distributed / clustered components shown as a single block.

```
    [user-querying-or-chain-interaction]                
          |                   ^                              
          V                   |                                    
       [HTTP-search-or-dedupe-API]--------> [stats-logs]
          |                   ^
          V                   |
   [fast-override-and-anti-attack-system]
          |                   ^                     
          |                   |                                  
          |           [clusterer-for-dedupe]                                  
          |                   ^                                  
          |                   |                                 
  [high-recall-model] [high-accuracy-model(s)]                                
          |                   |                    
          |                   |                     
  [vector-compaction]         |
          |                   |
          V                   |                               
  [ann-database] -->----------/
         ^
         |
         V
  [storage-subsystem]
```

Note: V1 will start off heavily truncated and with some blocks doing multiple functions, e.g.:

```
    [user-querying-or-chain-interaction]                
          |                   ^                              
          V                   |                                    
       [HTTP-search-or-dedupe-API]--------> [stats-logs]
          |                   ^
          V                   |                               
  [high-recall-model-and-ann-database]
         ^
         |
         V
  [storage-subsystem]
```

### Components:

+ `user-querying-or-chain-interaction`
  - Reading from / writing to the chain graph.
  - Processing responses from end users.

+ `HTTP-search-or-dedupe-API`:
    - Asynchronous batching enqueues tasks until timeout or queue reaches a specified size.
    - Wraps underlying components in a generic interface, so they can be swapped out.

+ `high-recall-model`:
    - Metric-space model(s) that converts media content -> vectors, and is tuned for high recall. 
    - Example training approaches: unsupervised, triplet-based i.e. more-similar pair / less-similar pair.
    - If multiple query intents are possible, or multiple objects per media content, then each
      media object may produce multiple vectors, possibly corresponding to particular locations
      in the content or particular types of semantic similarity.

+ `vector-compaction`
    - Skipped in V1.
    - Converts higher-dimensional vectors to lower-dimensional vectors while preserving metric space
      relationships. E.g. 1024d -> 128d.

+ `ann-database`:
    - Stores document vectors and does nearest-neighbor lookups on the vectors.
    - These are typically clustered via the "shards & replicas" style of ElasticSearch.

+ `high-accuracy-model` (for search):
    - Skipped in V1.
    - Much slower than the high-recall-model, tuned for e.g. NDCG score instead of recall.
    - Looks at the original media object for each (query vs result) media object pair.
    - Does in-depth attention, comparing each media object, to produce relevancy scores.
    - Re-ranks by these scores.

+ `high-accuracy-model` (for dedupe):
    - For dedupe mode only.
    - Similar to the search high-accuracy model above, but produces "same" or "different" classifications
      for pairs of media objects, instead of producing relevancy scores.

+ `clusterer-for-dedupe`:
    - For dedupe mode only.
    - Turns the pairs of same / different classifications into flat clusters.
    
+ `fast-override-and-anti-attack-system`
    - Skipped or minimal version for V1.
    - For anti-spam, anti-resource exhaustion, quick improvement of results.
    - Models mentioned above can be slow to re-train. This component can immediately re-rank / filter query results
      based on e.g. user feedback, operator feedback, spam control, etc.

+ `stats-logs`
    - Store system and usage stats somewhere.
    

## MODES OF OPERATION:

+ Training:
  - Train the models to rank semantically-similar media / identify duplicates.
  - Unsupervised / distantly supervised for V1.
  - Long-term, trained on large variety of training sources. More details below.
  
+ Indexing:
  - Trained models consume media & media metadata, convert to vectors for storage in [ann-database].

+ Offline Dedupe:
  - Offline calculation of all of the flat dedupe clusters.
  - Note that dedupe clustering can also be done online, in the "Querying" mode, but some
    models may perform worse when run in online mode. Sometimes ways to get the best of both.
  - Since dedupe can be slow / less efficient to do online in the Querying mode, likely
    best to do it in offline mode.

+ Querying:
  - Retrieve scored list of media most semantically similar to a particular media.
  - Retrieve semantic duplicates of a particular media.


## TRAINING SETUP:

+ In V1 - Mostly unsupervised or distantly-supervised methods used. At most, crude hyper-parameter optimization.

+ Later versions - More rigorous hyper-parameter optimization, layer-wise pre-training, fine tuning,
  end-to-end training where possible, etc.

+ Training data can come from many sources:
   - Explicit user feedback.
   - Implicit user signals, e.g. click-through log patterns.
   - Paid annotators (with possibly misaligned incentives).
   - Highly-trusted in-house annotators (much smaller group but assumed to have perfectly-aligned incentives).
   - Automated / distantly supervised training set construction techniques.

+ Developing an effective training pipeline can be a sizable process. Some 3rd party services may be of use here.


## TRAINING DATA LIFECYCLE:

```
 [new-training-data]
           |
           V
 [user-answers-validated-against-trusted-answers-on-multiple-time-scales.]
           |
           V
 [inter-annotator-agreement-&-outlier-detection]
           |
           V
 [force-a-resolution-among-users-for-conflicting-answers]
           |
           V
 [training-data-accepted.-retrain-models]
    ^      |
    |      V
    |    [validate-improvement-on-holdout-data]
    |      |
    |      V
    |   [deploy-new-models]
    |      |
    |      V
 [drop-in-user-metrics?-revert-to-previous-batch-of-accepted-training data.]
```
