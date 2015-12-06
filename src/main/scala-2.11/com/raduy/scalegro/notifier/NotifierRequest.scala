package com.raduy.scalegro.notifier

import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

import akka.actor._
import com.raduy.scalegro.notifier.Notifier.AuctionChangedNotification

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class NotifierRequest(publisherAddress: String, notification: AuctionChangedNotification)
  extends Actor with ActorLogging {

  @throws[Exception](classOf[Exception])
  override def postRestart(reason: Throwable): Unit = {
    log.debug("Restarting! Reason: {}", reason)
    super.postRestart(reason)
  }

  override def preStart() = {
    val now: LocalDateTime = LocalDateTime.now()
    tryToDeliverNotification()
  }

  override def receive: Receive = {
    case ex: Exception =>
      log.warning("Cannot reach publisher system!")
      throw ex
  }

  private def tryToDeliverNotification(): Unit = {
    val publisherFuture = publisher.resolveOne(Duration(1, TimeUnit.SECONDS))
    publisherFuture.onSuccess {
      case publisher: ActorRef =>
        publisher ! notification
        context.parent ! PoisonPill
    }

    publisherFuture.onFailure {
      case ex: Throwable =>
        self ! ex
    }
  }

  def publisher = {
    context.actorSelection(publisherAddress)
  }
}