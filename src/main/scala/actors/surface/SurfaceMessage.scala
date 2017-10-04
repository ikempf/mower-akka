package actors.surface

import actors.mower.MowerMessage
import akka.typed.ActorRef
import model.{Command, Mower}

sealed trait SurfaceMessage

object SurfaceMessage {

  case object BeginProcessing extends SurfaceMessage
  case object PrintSystemState extends SurfaceMessage
  case class AllCommandsExecuted(mowerRef: ActorRef[MowerMessage], mower: Mower) extends SurfaceMessage
  case class RequestAuthorisation(mowerRef: ActorRef[MowerMessage], currentState: Mower, newState: Mower, commands: List[Command], retry: Int) extends SurfaceMessage

}