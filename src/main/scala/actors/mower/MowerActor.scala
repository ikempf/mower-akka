package actors.mower

import actors.mower.MowerMessage.{ExecuteCommands, PositionAllowed, PositionRejected, TerminateProcessing}
import actors.surface.SurfaceMessage
import actors.surface.SurfaceMessage.{AllCommandsExecuted, RequestAuthorisation}
import akka.typed.scaladsl.Actor
import akka.typed.{ActorRef, Behavior}
import model.{Command, Forward, Mower}
import org.slf4j.LoggerFactory


object MowerActor {

  private val log = LoggerFactory.getLogger(getClass)

  def awaitingCommands(surfaceActor: ActorRef[SurfaceMessage]): Behavior[MowerMessage] =
    Actor.immutable[MowerMessage] { (context, msg) =>
      msg match {
        case ExecuteCommands(mower, commands, retry) =>
          log.info(s"Executing instructions for <$mower>, remaining: <$commands>")
          executeCommands(surfaceActor, context.self)(mower, commands, retry)

        case TerminateProcessing(mower) =>
          log.info(s"Terminating $mower")
          Actor.stopped
      }
    }

  def awaitingAuthorisation(surfaceActor: ActorRef[SurfaceMessage]): Behavior[MowerMessage] =
    Actor.immutable[MowerMessage] { (context, msg) =>
      msg match {
        case PositionAllowed(newState, commands) =>
          log.info(s"Position <${newState.pos}> authorized, remaining:<${commands.tail}>")
          context.self ! ExecuteCommands(newState, commands.tail, 0)
          awaitingCommands(surfaceActor)

        case PositionRejected(oldState, commands, retry) =>
          context.self ! ExecuteCommands(oldState, commands, retry)
          awaitingCommands(surfaceActor)
      }
    }

  def awaitingTermination(surfaceActor: ActorRef[SurfaceMessage]): Behavior[MowerMessage] =
    Actor.immutable[MowerMessage] { (context, msg) =>
      msg match {
        case TerminateProcessing(mower) =>
          log.info(s"Terminating $mower after command completion")
          Actor.stopped
      }
    }

  def executeCommands(surfaceRef: ActorRef[SurfaceMessage], mowerRef: ActorRef[MowerMessage])
                     (mower: Mower, commands: List[Command], retry: Int): Behavior[MowerMessage] = {
    commands match {
      case Nil =>
        surfaceRef ! AllCommandsExecuted(mowerRef, mower)
        awaitingTermination(surfaceRef)

      case Forward :: tail =>
        log.info(s"Going forward on <$mower>, remaining:<$tail>, retry:<$retry>")
        val newState = mower.forward
        surfaceRef ! RequestAuthorisation(mowerRef, mower, newState, commands, retry)
        awaitingAuthorisation(surfaceRef)

      case command :: tail =>
        log.info(s"Rotating:<$command>, remaining:<$tail>")
        val newState = mower.rotate(command)
        mowerRef ! MowerMessage.ExecuteCommands(newState, commands.tail, 0)
        Actor.same
    }
  }

}