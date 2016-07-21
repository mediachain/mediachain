# Creating and using a translator

A key concept in Mediachain is _translators_: rather than imposing the use of a standard schema, or using complex, authoritative XSLT transforms, we support lightweight, idempotent, versioned field mappings that allow as much (or as little) of the underlying data to be processed at ingestion time. Later updates to the translators can be easily applied to previously ingested data because the originals are always retained. See [this (somewhat outdated) post](https://blog.mediachain.io/mediachain-developer-update-supplemental-translators-6abe3707030a) on the thinking behind this.

The long-term vision for this is a pure, declarative DSL, but [at the time of this writing](https://github.com/mediachain/mediachain-client/issues/70) we are using python a "black box" standin, to work out the mechanics of versioning, distributing and loading the translators.

---
**WARNING:** This means that translators are currently arbitrary code that runs in the main process. Never execute translators you don't trust! (We are currently considering jailing the execution with `pysandbox`)
---

### The translator's job
A translator extracts information of interest from a source document like [this](https://raw.githubusercontent.com/mediachain/schema-translators/master/mediachain/translation/getty/sample/JD6484-001.json) and emits a simple (r=1, i.e. hub and spokes) graph that represents the derived mediachain objects. 

```python
{
    'canonical': {
        '__mediachain_object__': True,
        'type': 'artefact',
        'meta': {'data': {
            "title": "Alfred Hitchcock",
            "_id": "getty_451356503"
        }}
    },
    'chain': [
        {
            '__mediachain_object__': True,
            'type': 'artefactCreatedBy',
            'meta': {},
            'entity': {
                "meta": {
                    "translator": "GettyTranslator/@Qm...",
                    "data": {
                        "name": "Michael Ochs Archives"
                    }
                },
                "type": "entity"
            }
        }
    ]
}
```
This particular example has extracted the `_id` and `title` fields, as well as the author organization.

### Writing a translator 

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

The important bit is in `process_data`, which maps the `id` field of the original json into our metaschema's external `_id`. Everything else in the `translate` method (which overrides a `NotImplementedError`-returning method in the parent `Translator`) is just some plumbing to create the graph.


### Translator sub-package structure
The base repository for schema translators is https://github.com/mediachain/schema-translators. This contains sub-packages for individual translators:

```bash
$ tree mediachain/translation/getty
mediachain/translation/getty
├── README.md
├── __init__.py
├── sample
│   └── JD6484-001.json
└── translator.py

1 directory, 4 files
```

* `translator.py`: main entrypoint and logic, should contain a class named `Getty` that extends `mediachain.translation.translator.Translator`
* `sample`: a directory containing one or more samples of input records (more on this below)
* `__init__.py`: standard python init file (empty)
* `README.md`: readme (optional)

### Translator lifecycle
We're experimenting with using IPFS to version and distribute translators. In order to make one available to the system:

* `git clone https://github.com/mediachain/schema-translators.git`
* add your translator (following "sub-package structure above") and the respective sample file(s)
* install the updated files into an active venv
```bash
$ virtualenv venv
$ source venv/bin/activate
$ pip install -U -I --no-deps --no-cache-dir .
```
* run the tests with `python setup.py test`: this will make sure the output from your translators, based on the test files, is valid
(if you see `E   ImportError: cannot import name Types_pb2`, try `pip install mediachain-client` directly)
* if the tests pass, run `python setup.py publish_translators`:

```js
Publishing to IPFS!
{'example': 'QmV5okrrLhEqBBhh13TESZABQNa9Hvg5mhxK7fg4QSRvm3',
 'getty': 'QmWsU4MrF28SrYz5Kwdd2i1d1fUozakdUUuHaSUHjNrBcT',
 'simple': 'QmUcs18y8c7bGTRMRVsZNuk62QcJV4Qdj9r7qRTRE6Hibx'}
 ```

 
 * this publishes the translator code to IPFS (any that you haven't changed are basically no-ops)
 * you can now access the translator during ingestion by the IPFS multihash:
 
```bash
 $ mediachain ingest getty@QmWsU4MrF28SrYz5Kwdd2i1d1fUozakdUUuHaSUHjNrBcT $some_dir
 ```
 
 * please open a PR against the parent repo so we can see your work! (in the future, we plan to automatically IPFS publish through a PR/CI process)


### Want to write a translator?
Submit a pull request to [mediachain/schema-translators](https://github.com/mediachain/schema-translators). See [example](https://github.com/mediachain/schema-translators/pull/7/files). If you have questions, come by #tech in the [Mediachain Slack](http://slack.mediachain.io/) and we'll gladly give you a hand!
