package com.raduy.scalegro.notifier

import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor._
import akka.pattern.BackoffSupervisor
import com.raduy.scalegro.notifier.Notifier.AuctionChangedNotification

import scala.concurrent.duration.Duration

class Notifier extends Actor with ActorLogging {

  val publisherAddress: String = "akka.tcp://auction-publisher-system@127.0.0.1:2553/user/auction-publisher"

  override def receive: Receive = {
    case notification: AuctionChangedNotification =>
      log.debug("Notification received!\n\n\n")
      val notificationId: String = UUID.randomUUID().toString


      val notificationRequestProps = Props(new NotifierRequest(publisherAddress, notification))

      val supervisor = BackoffSupervisor.props(
        notificationRequestProps,
        childName = notificationId,
        minBackoff = Duration(3, TimeUnit.SECONDS),
        maxBackoff = Duration(30, TimeUnit.SECONDS),
        randomFactor = 0 // adds 20% "noise" to vary the intervals slightly
      )
      context.actorOf(supervisor)
  }
}

case object Notifier {

  //in fact it's looks like events, not sure if sth like 'notification' required here...
  sealed trait AuctionChangedNotification

  case class AuctionStartedNotification(auctionTitle: String)

  case class NewOfferInAuctionNotification(auctionTitle: String, actualOffer: BigDecimal, buyer: ActorRef) extends AuctionChangedNotification

}
