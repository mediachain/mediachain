# Creating and using a Translator

A key concept in Mediachain is Translators: rather than imposing the use of a standard schema, or using complex, authoritative XSLT transforms, we support lightweight, idempotent, versioned field mappings that allow as much (or as little) of the underlying data to be processed at ingestion time. Later updates to the translators can be easily applied to previously ingested data because the originals are always retained. See [this (somewhat outdated) post](https://blog.mediachain.io/mediachain-developer-update-supplemental-translators-6abe3707030a) on the thinking behind this.

The long-term vision for this is a pure, declarative DSL, but [at the time of this writing](https://github.com/mediachain/mediachain-client/issues/70) we are using python a "black box" standin, to work out the mechanics of versioning, distributing and loading the translators.

**WARNING** This means that translators are currently arbitrary code that runs in the main process. Never execute translators you don't trust! (We are currently considering jailing the execution with `pysandbox`)

