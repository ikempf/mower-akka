package model

import actors.mower.MowerActor
import actors.mower.MowerMessage.{ExecuteCommands, PositionAllowed, PositionRejected, TerminateProcessing}
import actors.surface.SurfaceMessage
import actors.surface.SurfaceMessage.{AllCommandsExecuted, RequestAuthorisation}
import akka.testkit.TestKit
import akka.typed.testkit.TestKitSettings
import akka.typed.{ActorSystem, Behavior}
import akka.typed.testkit.scaladsl.TestProbe
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import model.MowerActorSpec._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterEach, FunSpec, Matchers}

import scala.concurrent.duration._
import scala.util.Random

class MowerActorSpec extends FunSpec with ScalaFutures with Matchers with BeforeAndAfterEach {

  implicit val system = ActorSystem(Behavior.ignore, "test-system")
  implicit val systemSettigns = TestKitSettings.apply(ConfigFactory.load())
  implicit val timeout = Timeout(5.seconds)

  describe("A mower actor") {

    it("should inform that there are no commands left") {
      // given
      val mower = aMower()
      val noMoreCommands = ExecuteCommands(mower = mower, commands = Nil, 0)

      val probe = TestProbe[SurfaceMessage]()
      val mowerRef = system.systemActorOf(MowerActor.awaitingCommands(probe.ref), "mower").futureValue

      // when
      mowerRef ! noMoreCommands

      // then
      val expectedResponse = AllCommandsExecuted(mowerRef, mower)
      probe.expectMsgType[SurfaceMessage](max = 5.seconds) shouldBe expectedResponse
    }

    it("should rotate and finish") {
      // given
      val mower = aMowerFacing(North)
      val commands = ExecuteCommands(mower = mower, commands = List(Right, Right, Left), 0)

      val expectedMower = mower.withOrientation(East)
      val probe = TestProbe[SurfaceMessage]()
      val mowerRef = system.systemActorOf(MowerActor.awaitingCommands(probe.ref), "mower").futureValue

      // when
      mowerRef ! commands

      // then
      val expectedResponse = AllCommandsExecuted(mowerRef, expectedMower)
      probe.expectMsgType[SurfaceMessage](max = 5.seconds) shouldBe expectedResponse
    }
  }

  describe("A mower actor asking authorisation") {

    it("should rotate, ask for permission, move and finish") {
      // given
      val mower = aMowerFacing(North)
      val commands = ExecuteCommands(mower = mower, commands = List(Right, Right, Left, Forward), 5)

      val previousMower = mower.withOrientation(East)
      val expectedMower = mower.withOrientation(East).withPosition(Position(1, 0))
      val probe = TestProbe[SurfaceMessage]()
      val mowerRef = system.systemActorOf(MowerActor.awaitingCommands(probe.ref), "mower").futureValue

      // when
      mowerRef ! commands

      // then
      val expectedResponse = RequestAuthorisation(mowerRef, previousMower, expectedMower, List(Forward), 0)
      probe.expectMsgType[SurfaceMessage](max = 5.seconds) shouldBe expectedResponse
    }

    it("should rotate, ask for permission, stay and finish") {
      // given
      val mower = aMowerFacing(South).withPosition(Position(0, 1))
      val commands = ExecuteCommands(mower = mower, commands = List(Right, Right, Left, Left, Forward), 5)

      val previousMower = mower.withOrientation(South)
      val expectedMower = mower.withOrientation(South).withPosition(Position(0, 0))
      val probe = TestProbe[SurfaceMessage]()
      val mowerRef = system.systemActorOf(MowerActor.awaitingCommands(probe.ref), "mower").futureValue

      // when
      mowerRef ! commands

      // then
      val expectedResponse = RequestAuthorisation(mowerRef, previousMower, expectedMower, List(Forward), 0)
      probe.expectMsgType[SurfaceMessage](max = 5.seconds) shouldBe expectedResponse
    }

  }

  describe("A mower actor with authorisation granted") {

    it("should move forward and ask for authorisation") {
      // given
      val mower = aMower()
      val moveForward = ExecuteCommands(mower = mower, commands = List(Forward), 0)
      val mowerResult = mower.forward

      val probe = TestProbe[SurfaceMessage]()
      val mowerRef = system.systemActorOf(MowerActor.awaitingCommands(probe.ref), "mower").futureValue

      // when
      mowerRef ! moveForward

      // then
      val expectedResponse = RequestAuthorisation(mowerRef, mower, mowerResult, List(Forward), 0)
      probe.expectMsgType[SurfaceMessage](max = 5.seconds) shouldBe expectedResponse
    }

    it("should receive position allowed and then finish") {
      // given
      val mower = aMower()
      val moveForward = ExecuteCommands(mower = mower, commands = List(Forward), 0)
      val positionAllowed = PositionAllowed(mower = mower, commands = List(Forward))

      val probe = TestProbe[SurfaceMessage]()
      val mowerRef = system.systemActorOf(MowerActor.awaitingCommands(probe.ref), "mower").futureValue

      // when
      mowerRef ! moveForward
      mowerRef ! positionAllowed

      // then
      val expectedResponse1 = RequestAuthorisation(mowerRef, mower, mower.forward, List(Forward), 0)
      val expectedResponse2 = AllCommandsExecuted(mowerRef, mower)
      probe.expectMsgType[SurfaceMessage](max = 5.seconds) shouldBe expectedResponse1
      probe.expectMsgType[SurfaceMessage](max = 5.seconds) shouldBe expectedResponse2
    }

  }

    describe("A mower actor with authorisation rejected") {

      it("should reply by AllCommandsExecuted") {
        // given
        val mower = aMower()
        val moveForward = ExecuteCommands(mower = mower, commands = List(Forward), 0)
        val question = PositionRejected(mower = mower, commands = Nil, 0)

        val probe = TestProbe[SurfaceMessage]()
        val mowerRef = system.systemActorOf(MowerActor.awaitingCommands(probe.ref), "mower").futureValue

        // when
        mowerRef ! moveForward
        mowerRef ! question

        // then
        val expectedResponse1 = RequestAuthorisation(mowerRef, mower, mower.forward, List(Forward), 0)
        val expectedResponse2 = AllCommandsExecuted(mowerRef, mower)
        probe.expectMsgType[SurfaceMessage](max = 5.seconds) shouldBe expectedResponse1
        probe.expectMsgType[SurfaceMessage](max = 5.seconds) shouldBe expectedResponse2
      }

    }

    describe("A mower actor that stops itself") {

      it("should not answer any more messages") {
        // given
        val mower = aMower()
        val question = TerminateProcessing(mower = mower)

        val probe = TestProbe[SurfaceMessage]()
        val mowerRef = system.systemActorOf(MowerActor.awaitingCommands(probe.ref), "mower").futureValue

        // when
        mowerRef ! question

        // then
        probe.expectNoMsg(5.seconds)
      }

    }

}

object MowerActorSpec {

  val surface = Surface(Position(5, 5))

  def aMower(id: Int = Random.nextInt()) = Mower(id = id, surface = surface, pos = Position(0, 0), ori = North)

  def aMowerFacing(ori: Orientation) = aMower().copy(ori = ori)

  implicit class MowerOperations(mower: Mower) {

    def withPosition(newPos: Position) = mower.copy(pos = newPos)

    def withOrientation(newOri: Orientation) = mower.copy(ori = newOri)
  }

}
