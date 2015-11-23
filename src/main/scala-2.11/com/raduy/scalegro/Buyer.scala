package com.raduy.scalegro

import akka.actor.{Actor, ActorLogging}
import com.raduy.scalegro.Auction.{YouWonAuctionEvent, BiggerOfferReceivedEvent, BidCommand}
import com.raduy.scalegro.AuctionSearch.{FindByKeyWordQuery, QueryResultEvent}
import com.raduy.scalegro.Buyer.LookForDesiredItemCommand
import com.raduy.scalegro.Seller.AuctionRef

/**
  * @author Łukasz Raduj 2015.
  */
class Buyer(name: String, keyword: String, budget: BigDecimal) extends Actor with ActorLogging {

  val FACTOR: Double = 1.1

  def haveEnoughMoney(actualOffer: BigDecimal): Boolean = {
    FACTOR * actualOffer < budget
  }

  override def receive: Receive = {
    case YouWonAuctionEvent(auctionTitle: String, finalPrice: BigDecimal) =>
      log.info("User {} won {} auction with final price of {}", name, auctionTitle, finalPrice)

    case LookForDesiredItemCommand => {
      auctionSearch ! FindByKeyWordQuery(keyword)
    }

    case QueryResultEvent(keyword: String, auctions: Set[AuctionRef]) => {
      val auctionRef: AuctionRef = auctions.iterator.next()
      auctionRef.auction ! BidCommand(budget, self)
    }

    case BiggerOfferReceivedEvent(actualOffer: BigDecimal) => {
      if (haveEnoughMoney(actualOffer)) {
        sender() ! BidCommand(FACTOR * actualOffer, self)
      }
    }
  }

  private def auctionSearch = context.actorSelection("/user/auction-search")
}

case object Buyer {
  //commands accepted by this actor
  case object LookForDesiredItemCommand
}
