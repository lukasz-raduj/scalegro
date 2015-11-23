package com.raduy.scalegro

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.raduy.scalegro.Auction.AuctionSoldEvent
import com.raduy.scalegro.AuctionSearch.{AuctionUnregisteredEvent, AuctionRegisteredEvent, UnregisterAuctionCommand}
import com.raduy.scalegro.Seller.{StartAuctionsCommand, AuctionRef}

/**
  * @author Łukasz Raduj 2015.
  */
class Seller(name: String, auctionToStart: List[String], auctionSearch: ActorRef) extends Actor with ActorLogging {

  var onGoingAuctions: List[AuctionRef] = List()

  import context._

  override def receive: Receive = {
    case StartAuctionsCommand => {
      auctionToStart.map { auctionTitle => system.actorOf(Props(new Auction(auctionTitle, self, auctionSearch))) }
    }

    case AuctionRegisteredEvent(auctionRef: AuctionRef) => {
      onGoingAuctions = auctionRef :: onGoingAuctions
    }

    case AuctionUnregisteredEvent(auctionRef: AuctionRef) => {
      log.info("Un-registering auction of name: {}", auctionRef.title)
      onGoingAuctions = onGoingAuctions.filter { auction => auction != auctionRef }
    }

    case AuctionSoldEvent(auctionRef: AuctionRef, finalPrice: BigDecimal) => {
      log.info("Seller {} sold item {} with final price {}", name, auctionRef.title, finalPrice)
    }
  }
}

case object Seller {

  //commands accepted by this actor
  case class StartAuctionsCommand()

  case class AuctionRef(title: String, auction: ActorRef)
}
