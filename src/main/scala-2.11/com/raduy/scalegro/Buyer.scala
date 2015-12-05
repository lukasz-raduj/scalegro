package com.raduy.scalegro

import akka.actor.{Actor, ActorLogging}
import com.raduy.scalegro.Auction.{BidCommand, BiggerOfferReceivedEvent, TooLittleOfferGivenEvent, YouWonAuctionEvent}
import com.raduy.scalegro.AuctionSearch.{FindByKeyWordQuery, QueryResultEvent}
import com.raduy.scalegro.Buyer.LookForDesiredItemCommand
import com.raduy.scalegro.Seller.AuctionRef

/**
  * @author Łukasz Raduj 2015.
  */
class Buyer(name: String, keyword: String, budget: BigDecimal) extends Actor with ActorLogging {

  def haveEnoughMoney(actualOffer: BigDecimal): Boolean = {
    1.0 + actualOffer < budget
  }

  override def receive: Receive = {
    case YouWonAuctionEvent(auctionTitle: String, finalPrice: BigDecimal) =>
      log.info("User {} won {} auction with final price of {}", name, auctionTitle, finalPrice)

    case LookForDesiredItemCommand => {
      auctionSearch ! FindByKeyWordQuery(keyword)
    }

    case QueryResultEvent(keyword: String, auctions: Set[AuctionRef]) => {
      val auctionRef: AuctionRef = auctions.iterator.next()

      auctionRef.auction ! BidCommand(1.0, self)
    }

    case BiggerOfferReceivedEvent(actualOffer: BigDecimal) => {
      Thread.sleep(500)
      if (haveEnoughMoney(actualOffer)) {
        sender() ! BidCommand(actualOffer + 1.0, self)
      }
    }

    case TooLittleOfferGivenEvent(actualOffer: BigDecimal) => {
      if (haveEnoughMoney(actualOffer)) {
        sender() ! BidCommand(actualOffer + 1.0, self)
      }
    }
  }

  private def auctionSearch = context.actorSelection("/user/auction-search")
}

case object Buyer {

  //commands accepted by this actor
  case object LookForDesiredItemCommand

}
