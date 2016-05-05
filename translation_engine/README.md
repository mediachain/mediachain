## translation_engine

This sub-project contains code for translating between external data schemas and the
internal format used in the Mediachain ingestion process.

While the interface for schema translation is still in development, this project contains
an example translator for JSON data from the 
[Tate gallery collection](https://github.com/tategallery/collection).  We plan to move this and
other organization-specific translators to the 
[schema-translators repo](https://github.com/mediachain/schema-translators) in the near future.


### Testing
Unit test for the Tate translator will try to load sample data from the classpath at runtime.
SBT will add the contents of the `test-resources` directory to the classpath when testing,
and anything you put in `test-resources/datasets` will be ignored by Git.

To test the Tate translator, make a subdirectory named `test-resources/datasets/tate`, then
clone the [Tate collection repo](https://github.com/tategallery/collection)
and copy the `artworks` directory into `test-resources/datasets/tate`.  You'll probably only want
to copy a small subset of the total artworks, since the ingestion process is not yet efficient.

You should end up with something like this:

```
test-resources
└── datasets
    ├── README.md
    └── tate
        └── artworks
            └── a
                ├── 000
                ├── 001
                ├── 002
                ├── 003
                ├── 004
                ├── 005
                ├── 006
                ├── 007
                ├── 008
                ├── 009
                ├── 010
                ├── 011
                ├── 012
                ├── 013
                ├── 014
                ├── 015
                ├── 016
                └── 017
```
