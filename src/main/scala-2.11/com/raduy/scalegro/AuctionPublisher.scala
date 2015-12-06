package com.raduy.scalegro

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.raduy.scalegro.notifier.Notifier
import Notifier.{AuctionStartedNotification, NewOfferInAuctionNotification}

class AuctionPublisher extends Actor with ActorLogging {

  override def receive: Receive = {
    case AuctionStartedNotification(auctionTitle: String) =>
      log.info("New auction with title: {} started", auctionTitle)

    case NewOfferInAuctionNotification(auctionTitle: String, actualOffer: BigDecimal, buyer: ActorRef) =>
      log.info("New offer in auction: {} ! Price: {} buyer: {}", auctionTitle, actualOffer, buyer)
  }
}