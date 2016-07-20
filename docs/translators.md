# Creating and using a Translator

A key concept in Mediachain is Translators: rather than imposing the use of a standard schema, or using complex, authoritative XSLT transforms, we support lightweight, idempotent, versioned field mappings that allow as much (or as little) of the underlying data to be processed at ingestion time. Later updates to the translators can be easily applied to previously ingested data because the originals are always retained. See [this (somewhat outdated) post](https://blog.mediachain.io/mediachain-developer-update-supplemental-translators-6abe3707030a) on the thinking behind this.

The long-term vision for this is a pure, declarative DSL, but [at the time of this writing](https://github.com/mediachain/mediachain-client/issues/70) we are using python a "black box" standin, to work out the mechanics of versioning, distributing and loading the translators.

---
**WARNING:** This means that translators are currently arbitrary code that runs in the main process. Never execute translators you don't trust! (We are currently considering jailing the execution with `pysandbox`)
---


## Writing a translator 

Here's a minimal translator:

```python
from __future__ import unicode_literals
from mediachain.translation.translator import Translator


class Example(Translator):

    @staticmethod
    def translator_id():
        return 'example'

    @staticmethod
    def translate(example_json):

        artwork_artefact = {
            u'__mediachain_object__': True,
            u'type': u'artefact',
            u'meta': {'data': process_data(example_json)}
        }

        return {
            u'canonical': artwork_artefact,
            u'chain': []
        }

    @staticmethod
    def process_data(example_json):
        data = {
            '_id': example_json['id']
        }
        return data


    @staticmethod
    def can_translate_file(file_path):
        return True
```

The important bit is in `process_data`, which maps the `id` field of the original json into our metaschema's external `_id`. Everything else in the `translate` method (which overrides a `NotImplementedError`-returning method in the parent `Translator`) is just some plumbing to create a minimal graph.
