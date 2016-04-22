package io.mediachain.rpc

import io.grpc.inprocess.{InProcessChannelBuilder}
import io.mediachain.rpc.Services.{CanonicalWithRootRevision, LSpaceServiceGrpc}
import io.mediachain.rpc.client.LSpaceClient
import io.mediachain.rpc.TypeConversions._
import io.mediachain.{BaseSpec, GraphFixture}
import io.mediachain.util.orient.MigrationHelper
import org.specs2.specification.BeforeAfterAll


object LSpaceServerSpec extends BaseSpec
  with BeforeAfterAll
{

  def is =
    s2"""
         - returns a canonical with root revision $fetchesACanonicalById
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
    println(s"fetching canonical $canonicalID")

    client.fetchCanonical(canonicalID) must beSome {
      response: CanonicalWithRootRevision =>
        (response.canonicalID must_== canonicalID) and
          (response.getRootRevision.getImage.title must_== blob.title)
    }
  }

}
