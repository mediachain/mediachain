package io.mediachain.translation

import cats.data.Xor
import com.fasterxml.jackson.core.JsonFactory
import io.mediachain.XorMatchers
import org.specs2.Specification
import org.json4s.JObject


object JsonLoaderSpec extends Specification with XorMatchers {

  def is =
    s2"""
        Loads json from a URL into an AST $loadsFromURL
        Parses simple $parsesSimple
        Parses key after object $parsesAfterObject
        Parses tate $parsesTate
      """
  val jf = new JsonFactory

  def loadsFromURL = {
    val json = JsonLoader.loadObjectFromURL(SpecResources.simpleTestResourceUrl)
    json.toEither must beRight
  }

  val simple =
    """
      {
        "a": 1
      }
    """.stripMargin
  def parsesSimple = {
    val parser = jf.createParser(simple)
    parser.nextToken()
    val parsed = JsonLoader.parseJOBject(parser)

    parsed must beRightXor
  }

  val afterObject =
    """
  {
    "a": {
      "b": "d"
    },
    "c": "o"
  }
  """.stripMargin

  def parsesAfterObject = {
    val parser = jf.createParser(afterObject)
    parser.nextToken()
    val parsed = JsonLoader.parseJOBject(parser)

    parsed must beRightXor
  }

  def tate =
  """
  {
    "acno": "D12803",
    "acquisitionYear": 1856,
    "additionalImages": [
    {
      "copyright": null,
      "creativeCommons": null,
      "filenameBase": "D12803",
      "sizes": [
      {
        "caption": "Enhanced image",
        "cleared": true,
        "file": "enhanced_images/D128/D12803_E.jpg",
        "height": 358,
        "resolution": 512,
        "size": "large",
        "width": 512
      }
      ]
    }
    ],
    "all_artists": "Joseph Mallord William Turner",
    "catalogueGroup": {
      "accessionRanges": "D12702-D12883; D40663-D40665",
      "completeStatus": "COMPLETE",
      "finbergNumber": "CLX",
      "groupType": "Turner Sketchbook",
      "id": 65802,
      "shortTitle": "Waterloo and Rhine Sketchbook"
    },
    "classification": "on paper, unique",
    "contributorCount": 1,
    "contributors": [
    {
      "birthYear": 1775,
      "date": "1775\u20131851",
      "displayOrder": 1,
      "fc": "Joseph Mallord William Turner",
      "gender": "Male",
      "id": 558,
      "mda": "Turner, Joseph Mallord William",
      "role": "artist",
      "startLetter": "T"
    }
    ],
    "creditLine": "Accepted by the nation as part of the Turner Bequest 1856",
    "dateRange": {
      "endYear": 1817,
      "startYear": 1817,
      "text": "1817"
    },
    "dateText": "1817",
    "depth": "",
    "dimensions": "support: 150 x 94 mm",
    "finberg": "CLX 53",
    "foreignTitle": null,
    "groupTitle": "Waterloo and Rhine Sketchbook",
    "height": "94",
    "id": 40171,
    "inscription": null,
    "medium": "Graphite on paper",
    "movementCount": 0,
    "pageNumber": 109,
    "subjectCount": 8,
    "subjects": {
      "children": [
    {
      "children": [
    {
      "children": [
    {
      "id": 9067,
      "name": "Coblenz, Ehrenbreitstein"
    }
      ],
      "id": 107,
      "name": "cities, towns, villages (non-UK)"
    },
    {
      "children": [
    {
      "id": 3561,
      "name": "Germany"
    }
      ],
      "id": 108,
      "name": "countries and continents"
    }
      ],
      "id": 106,
      "name": "places"
    },
    {
      "children": [
    {
      "children": [
    {
      "id": 1138,
      "name": "castle"
    }
      ],
      "id": 20,
      "name": "military"
    },
    {
      "children": [
    {
      "id": 465,
      "name": "church"
    }
      ],
      "id": 25,
      "name": "religious"
    },
    {
      "children": [
    {
      "id": 1151,
      "name": "dome"
    },
    {
      "id": 1065,
      "name": "tower"
    }
      ],
      "id": 17,
      "name": "features"
    }
      ],
      "id": 13,
      "name": "architecture"
    },
    {
      "children": [
    {
      "children": [
    {
      "id": 880,
      "name": "mountain"
    },
    {
      "id": 563,
      "name": "rocky"
    }
      ],
      "id": 71,
      "name": "landscape"
    }
      ],
      "id": 60,
      "name": "nature"
    }
      ],
      "id": 1,
      "name": "subject"
    },
    "thumbnailCopyright": null,
    "thumbnailUrl": "http://www.tate.org.uk/art/images/work/D/D12/D12803_8.jpg",
    "title": "The Fortress of Ehrenbreitstein, from the South, next to the Church of the Holy Cross and the Heribertturm",
    "units": "mm",
    "url": "http://www.tate.org.uk/art/artworks/turner-the-fortress-of-ehrenbreitstein-from-the-south-next-to-the-church-of-the-holy-cross-d12803",
    "width": "150"
  }
  """

  def parsesTate = {
    val parser = jf.createParser(tate)
    parser.nextToken()
    val parsed = JsonLoader.parseJOBject(parser)

    parsed must beRightXor
  }
}
