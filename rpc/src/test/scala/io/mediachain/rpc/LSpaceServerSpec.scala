package io.mediachain.rpc

import gremlin.scala._
import io.grpc.inprocess.InProcessChannelBuilder
import io.mediachain.Types._
import io.mediachain.rpc.Services._
import io.mediachain.rpc.client.LSpaceClient
import io.mediachain.rpc.{Types => RPCTypes}
import io.mediachain.rpc.TypeConversions._
import io.mediachain.{BaseSpec, GraphFixture}
import io.mediachain.util.orient.MigrationHelper
import org.specs2.execute.{AsResult, Result}
import org.specs2.matcher.{ContainWithResultSeq, Matcher}
import org.specs2.specification.BeforeAfterAll


object LSpaceServerSpec extends BaseSpec
  with BeforeAfterAll
{

  def is =
    s2"""
         - returns a list of all canonicals $returnsCanonicalList
         - returns a canonical with root revision $fetchesACanonicalById
         - returns a canonical's rev history $returnsASubtree
         - returns the works for an author $returnsWorks
         - merges two canonicals $mergesCanonicals
         - client returns None if canonical is not found $returnsNoneIfNoCanonical
      """

  //
  // Setup
  //

  val serviceName = "LSpace-Testing"
  lazy val clientChannel = InProcessChannelBuilder.forName(serviceName)
      .usePlaintext(true)
      .build

  lazy val client = new LSpaceClient(
    clientChannel,
    LSpaceServiceGrpc.blockingStub(clientChannel)
  )

  lazy val graphFactory = MigrationHelper.newInMemoryGraphFactory()
  lazy val server = new LSpaceServer(
    graphFactory,
    InProcessServerContext(name = serviceName)
  )

  var fixtures: GraphFixture.Objects = null


  def beforeAll: Unit = {
    val graph = graphFactory.getTx()
    fixtures = GraphFixture.Util.setupTree(graph)
    graph.close()

    server // force init of lazy val
  }

  def afterAll: Unit = {
    server.stop()
  }

  //
  // Matchers
  //

  private def matchCanonicalWithRootRev[B <: MetadataBlob]
  (expectedCanonicalID: String, expectedRootBlob: B)
  : Matcher[CanonicalWithRootRevision] = beLike {
    case c: CanonicalWithRootRevision =>
      c.canonicalID must_== expectedCanonicalID
      c.rootRevision must beSome(matchRevision(expectedRootBlob))
  }

  private def matchImageBlob(expected: ImageBlob)
  : Matcher[RPCTypes.ImageBlob] = beLike {
    case rpcImage: RPCTypes.ImageBlob =>
      rpcImage.title must_== expected.title
      rpcImage.description must_== expected.description
      rpcImage.date must_== expected.date
  }

  private def matchPerson(expected: Person)
  : Matcher[RPCTypes.Person] = beLike {
    case rpcPerson: RPCTypes.Person =>
      rpcPerson.name must_== expected.name
  }

  private def matchRaw(expected: RawMetadataBlob)
  : Matcher[RPCTypes.RawMetadataBlob] = beLike {
    case rpcRaw: RPCTypes.RawMetadataBlob =>
      rpcRaw.blob must_== expected.blob
  }

  private def matchRevision[B <: MetadataBlob](expected: B)
  : Matcher[RPCTypes.MetadataBlob] = expected match {
    case imageBlob: ImageBlob => beLike {
      case b: RPCTypes.MetadataBlob if b.blob.isImage =>
        b.blob.image must beSome(matchImageBlob(imageBlob))
    }
    case person: Person => beLike {
      case b: RPCTypes.MetadataBlob if b.blob.isPerson =>
        b.blob.person must beSome(matchPerson(person))
    }
    case raw: RawMetadataBlob => beLike {
      case b: RPCTypes.MetadataBlob if b.blob.isRawMetadata =>
        b.blob.rawMetadata must beSome(matchRaw(raw))
    }
  }


  private def matchRevisions[B <: MetadataBlob](expected: Seq[B])
  : ContainWithResultSeq[RPCTypes.MetadataBlob] = {
    val expectedMatchers = expected.map(matchRevision)
    ContainWithResultSeq(checks = expectedMatchers)
  }

  private def matchListOfCanonicalWithRootRev[B <: MetadataBlob]
  (expected: (String, B)*)
  : ContainWithResultSeq[CanonicalWithRootRevision] = {
    val f = matchCanonicalWithRootRev _
    val expectedMatchers = expected.map(f.tupled)
    ContainWithResultSeq(checks = expectedMatchers)
  }


  //
  // Examples
  //

  def returnsCanonicalList = {
    val expectedCanonicalID = fixtures.imageBlobCanonical.canonicalID
    val expectedImage = fixtures.imageBlob

    client.listCanonicals() must beSome { result: CanonicalList =>
      result.canonicals must haveLength(5)
      result.canonicals must contain(
        matchCanonicalWithRootRev(expectedCanonicalID, expectedImage))
    }
  }

  def fetchesACanonicalById = {
    val blob = fixtures.imageBlob
    val canonicalID = fixtures.imageBlobCanonical.canonicalID

    client.fetchCanonical(canonicalID) must beSome {
      response: CanonicalWithRootRevision =>
        response.canonicalID must_== canonicalID
        response.getRootRevision.getImage.title must_== blob.title
    }

    client.fetchCanonical(canonicalID, withRawMetadata = true) must beSome {
      response: CanonicalWithRootRevision =>
        response.canonicalID must_== canonicalID
        response.getRawMetadata must matchRaw(fixtures.rawMetadataBlob)
    }
  }



  def returnsASubtree = {
    val canonicalID = fixtures.imageBlobCanonical.canonicalID
    val expectedRevisions = List(
      fixtures.imageBlob, fixtures.modifiedImageBlob
    )

    client.fetchHistoryForCanonical(canonicalID) must beSome {
      response: CanonicalWithHistory =>
        response.canonicalID must_== canonicalID
        response.revisions must matchRevisions(expectedRevisions)
    }
  }


  def returnsWorks = {
    val personCanonicalID = fixtures.personCanonical.canonicalID

    client.listWorksForAuthorWithCanonicalID(personCanonicalID) must beSome {
      response: WorksForAuthor =>
        response.author must beSome(
          matchCanonicalWithRootRev(personCanonicalID, fixtures.person))

          response.works must matchListOfCanonicalWithRootRev(
            (fixtures.imageBlobCanonical.canonicalID, fixtures.imageBlob),
            (fixtures.imageByDuplicatePersonCanonical.canonicalID,
              fixtures.imageByDuplicatePerson)
          )
    }
  }


  def returnsNoneIfNoCanonical = {
    client.fetchCanonical(canonicalID = "Foo!") must beNone
  }


  def mergesCanonicals = {
    val imageCanonicalID = fixtures.imageBlobCanonical.canonicalID
    val extraImageCanonicalID = fixtures.extraImageBlobCanonical.canonicalID

    client.mergeCanonicals(extraImageCanonicalID, imageCanonicalID) must beSome {
      response: MergeCanonicalsResponse =>
        response.mergedCanonicalID must_== imageCanonicalID
    }

    val graph = graphFactory.getTx
    fixtures.extraImageBlobCanonical.vertex(graph) must beRightXor { v: Vertex =>
      v.outE(SupersededBy).exists() must beTrue
    }
  }
}
