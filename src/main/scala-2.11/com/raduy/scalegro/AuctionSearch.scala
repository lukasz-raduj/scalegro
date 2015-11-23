package com.raduy.scalegro

import akka.actor.{Actor, ActorLogging}
import com.raduy.scalegro.AuctionSearch._
import com.raduy.scalegro.Seller.AuctionRef

/**
  * @author Łukasz Raduj 2015.
  */
class AuctionSearch extends Actor with ActorLogging {

  private var registeredAuctions: Set[AuctionRef] = Set()

  override def receive: Receive = {
    case RegisterAuctionCommand(auctionRef: AuctionRef) => {
      log.info("New auction registered!")
      registeredAuctions = registeredAuctions + auctionRef
      log.info("AuctionSearch auctions:{}", registeredAuctions)
      sender() ! AuctionRegisteredEvent(auctionRef)
    }
    case UnregisterAuctionCommand(auctionRef) =>
      log.debug("Removing auction {} from auctions search", auctionRef.title)
      registeredAuctions = registeredAuctions - auctionRef
      sender() ! AuctionUnregisteredEvent(auctionRef)
      log.debug("AuctionsSearch state: {}", registeredAuctions)

    case FindByKeyWordQuery(keyword) =>
      val matchingAuctions = registeredAuctions.filter { auction => auction.title.split(' ').contains(keyword) }

      sender() ! QueryResultEvent(keyword, matchingAuctions)
  }
}

case object AuctionSearch {

  //commands accepted by this actor:
  case class RegisterAuctionCommand(auctionRef: AuctionRef)

  case class UnregisterAuctionCommand(auctionRef: AuctionRef)

  //queries accepted by this actor:
  case class FindByKeyWordQuery(keyword: String)

  //events thrown by this actor
  case class AuctionRegisteredEvent(auction: AuctionRef)

  case class AuctionUnregisteredEvent(auction: AuctionRef)

  case class QueryResultEvent(keyword: String, auctions: Set[AuctionRef])

}


