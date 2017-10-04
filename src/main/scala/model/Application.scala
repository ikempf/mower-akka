package model

import actors.surface.SurfaceActor
import actors.surface.SurfaceActor.SurfaceConfig
import actors.surface.SurfaceMessage.BeginProcessing
import akka.typed.ActorSystem
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global

object Application extends App {

  private val log = LoggerFactory.getLogger(getClass)

  private val surface = Surface(Position(5, 5))

  private val commands: SurfaceConfig = Map(
    Mower(1, surface, pos = Position(1, 2), ori = North) -> List(Left, Forward, Left, Forward, Left, Forward, Left, Forward, Forward),
    Mower(2, surface, pos = Position(3, 3), ori = East) -> List(Forward, Forward, Right, Forward, Forward, Right, Forward, Right, Right, Forward)
  )

  private val config = ConfigFactory.load()

  private val surfaceBehaviour = SurfaceActor.spawningMowers(commands)
  private val surfaceActor = ActorSystem(surfaceBehaviour, "system")
  surfaceActor ! BeginProcessing

  surfaceActor.whenTerminated
    .onComplete(_ => {
      log.info("Shutting down")
      System.exit(1)
    })

}