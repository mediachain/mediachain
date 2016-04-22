package io.mediachain.rpc

import io.grpc.inprocess.InProcessChannelBuilder
import io.mediachain.Types._
import io.mediachain.rpc.Services.{CanonicalWithHistory, CanonicalWithRootRevision, LSpaceServiceGrpc}
import io.mediachain.rpc.client.LSpaceClient
import io.mediachain.rpc.{Types => RPCTypes}
import io.mediachain.rpc.TypeConversions._
import io.mediachain.{BaseSpec, GraphFixture}
import io.mediachain.util.orient.MigrationHelper
import org.specs2.execute.Result
import org.specs2.matcher.Matcher
import org.specs2.specification.BeforeAfterAll


object LSpaceServerSpec extends BaseSpec
  with BeforeAfterAll
{

  def is =
    s2"""
         - returns a canonical with root revision $fetchesACanonicalById
         - returns a canonical's rev history $returnsASubtree
      """

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

    server.start()
  }

  def afterAll: Unit = {
    server.stop()
  }


  def fetchesACanonicalById = {
    val blob = fixtures.imageBlob
    val canonicalID = fixtures.imageBlobCanonical.canonicalID

    client.fetchCanonical(canonicalID) must beSome {
      response: CanonicalWithRootRevision =>
        (response.canonicalID must_== canonicalID) and
          (response.getRootRevision.getImage.title must_== blob.title)
    }
  }

  private def matchImageBlob(blob: ImageBlob): Matcher[ImageBlob] =
    beLike {
      case imageBlob: ImageBlob =>
        (imageBlob.title must_== blob.title) and
          (imageBlob.description must_== blob.description) and
          (imageBlob.date must_== blob.date)
    }

  private def matchRevisions(
    expected: Seq[ImageBlob],
    actual: Seq[RPCTypes.MetadataBlob]
  ): Result = {
    val actualAsImageBlobs = actual.flatMap(_.blob.image).map(_.fromRPC)
    val expectedMatchers = expected.map(matchImageBlob)

    // this doesn't do what I want, sadly...
    actualAsImageBlobs must contain(expectedMatchers)
  }


  def returnsASubtree = {
    val canonicalID = fixtures.imageBlobCanonical.canonicalID
    val expectedRevisions = List(
      fixtures.imageBlob, fixtures.modifiedImageBlob
    )

    client.fetchHistoryForCanonical(canonicalID) must beSome {
      response: CanonicalWithHistory =>
        (response.canonicalID must_== canonicalID) and
          (response.revisions must haveLength(2))
        // TODO: check contents of revisions
    }
  }
}
