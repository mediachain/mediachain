# L-SPACE

[![Circle CI](https://circleci.com/gh/mediachain/L-SPACE.svg?style=svg)](https://circleci.com/gh/mediachain/L-SPACE)
[![Coverage Status](https://coveralls.io/repos/github/mediachain/L-SPACE/badge.svg?branch=master)](https://coveralls.io/github/mediachain/L-SPACE?branch=master)

L-SPACE[¹](http://wiki.lspace.org/mediawiki/L-space) is the [Mediachain](https://medium.com/mine-labs/introducing-mediachain-a696f8fd2035) server. Please take a look at the [roadmap](https://medium.com/mine-labs/mediachain-developer-update-v-a7f6006ad953) or stop by [#tech](https://mediachain.slack.com/messages/tech/) in our [slack](https://mediachain-slack.herokuapp.com/) for more details.

The server is at a proof of concept stage and not to be used for anything other than exploratory/development work.

### Usage
```
git clone git@github.com:parkan/collection-sans-turner.git
sbt compile
sbt console
```

```
scala> import io.mediachain.translation.TranslatorDispatcher
import io.mediachain.translation.TranslatorDispatcher

scala> TranslatorDispatcher.dispatch("tate", "path/to/tate/collection/artworks/")
...
scala> TranslatorDispatcher.dispatch("moma", "path/to/moma/Artworks.json")
...
```

Support so far is available for the [MoMA](https://github.com/MuseumofModernArt/collection) and [Tate](https://github.com/tategallery/collection) datasets (you may wish to use the [Tate collection, excepting works of Turner](https://github.com/parkan/collection-sans-turner) for development use, because the Turner cluster is very large)
