package actors.mower

import model.{Command, Mower}

sealed trait MowerMessage

object MowerMessage {

  case class ExecuteCommands(mower: Mower, commands: List[Command], retry: Int) extends MowerMessage
  case class PositionAllowed(mower: Mower, commands: List[Command]) extends MowerMessage
  case class PositionRejected(mower: Mower, commands: List[Command], retry: Int) extends MowerMessage
  case class TerminateProcessing(mower: Mower) extends MowerMessage

}