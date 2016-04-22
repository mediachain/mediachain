package io.mediachain

import java.util.Date

import scala.util.Random


object GraphFixture {
  import io.mediachain.Types._
  import gremlin.scala._

  case class Objects(
    person: Person,
    personCanonical: Canonical,
    imageBlob: ImageBlob,
    imageBlobCanonical: Canonical,
    modifiedImageBlob: ImageBlob,
    extraImageBlob: ImageBlob,
    extraImageBlobCanonical: Canonical,
    rawMetadataBlob: RawMetadataBlob,
    duplicatePerson: Person,
    duplicatePersonCanonical: Canonical,
    imageByDuplicatePerson: ImageBlob,
    imageByDuplicatePersonCanonical: Canonical
  )


  case class Context(graph: Graph, objects: Objects)
  object Context {
    def apply(graph: Graph): Context = {
      Context(graph, Util.setupTree(graph))
    }
  }

  object Util {
    // guarantees returned string is different from input
    // TODO: accept distance
    def mutate(s: String): String = {
      val idx = Random.nextInt(s.length)
      val chars = ('a' to 'z').toSet
      val replaced = s.charAt(idx)
      val replacing = (chars - replaced).toVector(Random.nextInt(chars.size - 1))
      s.updated(idx, replacing)
    }

    // from https://github.com/dariusk/corpora
    val stuff = Random.shuffle(List("can of peas",
      "wishbone", "pair of glasses", "spool of wire", "wrench", "baseball hat", "television", "food",
      "wallet", "jar of pickles", "tea cup", "sketch pad", "towel", "game CD", "steak knife", "slipper",
      "pants", "sand paper", "boom box", "plush unicorn"))

    val food = Random.shuffle(List("Preserved Peaches", "Brussels Sprouts", "Bananas", "Lettuce Salad",
      "Olives", "Broiled Ham", "Cigars", "Mixed Green Salad", "Oyster Bay Asparagus", "Roast Lamb, Mint Sauce",
      "Lemonade", "Consomme en Tasse", "Liqueurs", "Iced Tea", "Canadian Club", "Radis", "Escarole Salad",
      "Preserved figs", "Potatoes, baked", "Macedoine salad"))

    def getImageBlob: ImageBlob = {
      val title = stuff(Random.nextInt(stuff.length))
      val desc = food(Random.nextInt(stuff.length))
      val date = new Date(Random.nextLong).toString
      ImageBlob(None, title, desc, date)
    }

    def getModifiedImageBlob(b: ImageBlob): ImageBlob = {
      b.copy(description = mutate(b.description))
    }

    def getRawMetadataBlob: RawMetadataBlob = {
      val thing = stuff(Random.nextInt(stuff.length))
      val blobText = s""" {"thing": "$thing"} """
      RawMetadataBlob(None, blobText)
    }

    val bodhisattvas = Random.shuffle(List("Avalokitesvara",
      "Manjushri", "Samantabhadra", "Kshitigarbha", "Maitreya", "Mahasthamaprapta", "Ākāśagarbha"))
    def getPerson: Person = Person.create(bodhisattvas(Random.nextInt(bodhisattvas.length)))

    def setupTree(graph: Graph): Objects = {
      // add photo and canonical
      val imageBlob = getImageBlob
      val imageBlobV = graph + imageBlob
      val imageBlobCanonical = Canonical.create
      val canonicalV = graph + imageBlobCanonical
      canonicalV --- DescribedBy --> imageBlobV

      // add a revision to a photo
      val modifiedBlob = getModifiedImageBlob(imageBlob)
      val modifiedBlobV = graph + modifiedBlob
      imageBlobV --- ModifiedBy --> modifiedBlobV

      // add an author for the photo
      val person = getPerson
      val personV = graph + person
      val personCanonical = Canonical.create()
      val personCanonicalV = graph + personCanonical
      personCanonicalV --- DescribedBy --> personV
      imageBlobV --- AuthoredBy --> personCanonicalV


      // add a duplicate Person and a Canonical, and merge the
      // duplicate Canonical into `personCanonical`, so that
      // `duplicatePersonCanonical` is `SupersededBy` `personCanonical`
      val duplicatePerson = person.copy(name = Util.mutate(person.name))
      val duplicatePersonCanonical = Canonical.create()
      val duplicatePersonV = graph + duplicatePerson
      val duplicatePersonCanonicalV = graph + duplicatePersonCanonical
      personCanonicalV --- DescribedBy --> duplicatePersonV
      duplicatePersonCanonicalV --- (DescribedBy, Keys.Deprecated -> true) --> duplicatePersonV
      duplicatePersonCanonicalV --- SupersededBy --> personCanonicalV

      // add an image that was authored by the duplicate person
      // this should be returned from the "find works" query for
      // both `person` and `duplicatePerson`
      val imageByDuplicatePerson = getImageBlob
      val imageByDuplicatePersonCanonical = Canonical.create()
      val imageByDuplicatePersonV = graph + imageByDuplicatePerson
      val imageByDuplicatePersonCanonicalV = graph + imageByDuplicatePersonCanonical
      imageByDuplicatePersonCanonicalV --- DescribedBy --> imageByDuplicatePersonV
      imageByDuplicatePersonV --- AuthoredBy --> duplicatePersonCanonicalV

      // add decoy objects that we shouldn't see in a subtree
      val extraImageBlob = getImageBlob
      val extraImageBlobV = graph + extraImageBlob
      val extraImageBlobCanonical = Canonical.create()
      val extraImageBlobCanonicalV = graph + extraImageBlobCanonical
      extraImageBlobCanonicalV --- DescribedBy --> extraImageBlobV
      extraImageBlobV --- AuthoredBy --> personCanonicalV

      val rawMetadataBlob = getRawMetadataBlob
      val rawMetadataBlobV = graph + rawMetadataBlob
      imageBlobV --- TranslatedFrom --> rawMetadataBlobV


      Objects(
        personV.toCC[Person],
        personCanonicalV.toCC[Canonical],
        imageBlobV.toCC[ImageBlob],
        canonicalV.toCC[Canonical],
        modifiedBlobV.toCC[ImageBlob],
        extraImageBlobV.toCC[ImageBlob],
        extraImageBlobCanonicalV.toCC[Canonical],
        rawMetadataBlobV.toCC[RawMetadataBlob],
        duplicatePersonV.toCC[Person],
        duplicatePersonCanonicalV.toCC[Canonical],
        imageByDuplicatePersonV.toCC[ImageBlob],
        imageByDuplicatePersonCanonicalV.toCC[Canonical])
    }
  }

}
