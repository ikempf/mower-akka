package actors.surface

import actors.mower.MowerMessage.{ExecuteCommands, PositionAllowed, PositionRejected, TerminateProcessing}
import actors.mower.{MowerActor, MowerMessage}
import actors.surface.SurfaceMessage.{AllCommandsExecuted, BeginProcessing, RequestAuthorisation}
import akka.typed.ActorRef
import akka.typed.scaladsl.Actor
import model.{Command, Mower, Position}
import org.slf4j.LoggerFactory

object SurfaceActor {

  private val log = LoggerFactory.getLogger(getClass)
  private val MaxRetry = 10

  type SurfaceConfig = Map[Mower, List[Command]]

  def spawningMowers(initialState: SurfaceConfig): Actor.Immutable[SurfaceMessage] = {
    Actor.immutable[SurfaceMessage] { (context, msg) =>
      msg match {
        case BeginProcessing =>
          log.info("Spawning mowers")

          initialState.zipWithIndex.foreach {
            case ((mower, commands), index) =>
              val mowerRef = context.spawn(MowerActor.awaitingCommands(context.self), s"mower-$index")
              log.info(s"Starting mower $mowerRef")
              mowerRef ! ExecuteCommands(mower, commands, retry = 0)
          }

          handlingCollisions(Map.empty, initialState.size)
      }
    }
  }

  def handlingCollisions(usedPositions: Map[Int, Position], mowersLeft: Int): Actor.Immutable[SurfaceMessage] =
    Actor.immutable[SurfaceMessage] { (context, msg) =>
      msg match {
        case RequestAuthorisation(mowerRef: ActorRef[MowerMessage], currentState: Mower, newState: Mower, remainingCommands: List[Command], retry: Int) =>
          log.info(s"RequestPosition:<${newState.pos}> usedPositions:<$usedPositions> retry:<$retry>")

          val conflictingMowers = mowersAtPosition(usedPositions, newState.pos, excluding = currentState)

          conflictingMowers match {
            case Nil =>
              mowerRef ! PositionAllowed(newState, remainingCommands)
              handlingCollisions(usedPositions + (currentState.id -> newState.pos), mowersLeft)

            case _ if retry <= MaxRetry =>
              log.info(s"Position <${newState.pos}> rejected!!")
              mowerRef ! PositionRejected(currentState, remainingCommands, retry + 1)
              Actor.same

            case _ if retry > MaxRetry =>
              log.info(s"Stopping mower:<$currentState> because no attempts remaining!")
              mowerRef ! TerminateProcessing(newState)
              Actor.same
          }

        case AllCommandsExecuted(mowerRef: ActorRef[MowerMessage], mower) =>
          log.info(s"Stopping mower:<$mower> because finished commands!")
          context.stop(mowerRef)

          if (mowersLeft - 1 == 0)
            Actor.stopped
          else
            handlingCollisions(usedPositions - mower.id, mowersLeft - 1)
      }
    }

  private def mowersAtPosition(usedPositions: Map[Int, Position], position: Position, excluding: Mower): List[Position] =
    usedPositions.filterKeys(_ != excluding.id).values.filter(_ == position).toList

}