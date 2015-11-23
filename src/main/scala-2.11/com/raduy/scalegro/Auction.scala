package com.raduy.scalegro

import java.util.concurrent.TimeUnit

import akka.actor.{ActorLogging, ActorRef, FSM}
import com.raduy.scalegro
import com.raduy.scalegro.Auction._
import com.raduy.scalegro.AuctionSearch.{AuctionUnregisteredEvent, AuctionRegisteredEvent, RegisterAuctionCommand, UnregisterAuctionCommand}
import com.raduy.scalegro.Seller.AuctionRef

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

/**
  * @author Łukasz Raduj 2015.
  */
class Auction(title: String, seller: ActorRef, auctionSearch: ActorRef) extends FSM[AuctionState, AuctionData] with ActorLogging {
  var price: BigDecimal = 0

  override def preStart(): Unit = {
    import context.dispatcher
    context.system.scheduler.scheduleOnce(Duration(10, TimeUnit.SECONDS), self, FinishAuctionCommand)

    auctionSearch ! RegisterAuctionCommand(AuctionRef(title, self))
    super.preStart()
  }

  private def deleteItem() = {
    log.debug("Deleting sold auction with title '{}'", title)
    context.stop(self)
  }

  private def finishAuction() = {
    auctionSearch ! UnregisterAuctionCommand(AuctionRef(title, self))
  }

  /**
    * Finite State Machine definition
    */
  startWith(Created, NoOffer)

  when(Created) {
    case Event(BidCommand(offer: BigDecimal, bidder: ActorRef), NoOffer) =>
      log.debug("First offer in '{}' received! Offer value: {}", title, offer)
      goto(Activated) using Offer(offer, bidder)

    case Event(FinishAuctionCommand, NoOffer) =>
      //start DeleteTimer
      import ExecutionContext.Implicits.global
      context.system.scheduler.scheduleOnce(Duration(5, TimeUnit.SECONDS), self, DeleteFinishedAuctionCommand)
      finishAuction()
      goto(Ignored) using NoOffer

    case Event(e: AuctionRegisteredEvent, NoOffer) =>
      seller ! e
      stay() using NoOffer

    case Event(e: AuctionUnregisteredEvent, NoOffer) =>
      seller ! e
      stay() using NoOffer
  }

  when(Ignored) {
    case Event(DeleteFinishedAuctionCommand, NoOffer) =>
      deleteItem()
      stop(FSM.Normal)

    case Event(e: AuctionUnregisteredEvent, NoOffer) =>
      seller ! e
      stay() using NoOffer
  }

  when(Activated) {
    case Event(BidCommand(offer: BigDecimal, bidder: ActorRef), o: Offer) =>
      log.debug("New offer in '{}' auction! Offer value: {}", title, offer)

      if (offer > o.price) {
        o.bidder ! BiggerOfferReceivedEvent(offer)
        stay() using Offer(offer, bidder)
      } else {
        stay() using o
      }

    case Event(FinishAuctionCommand, o: Offer) =>
      log.debug("Finishing auction '{}'! Final Price: {}", title, o.price)
      //notify buyer
      finishAuction()
      o.bidder ! YouWonAuctionEvent(title, o.price)
      seller ! AuctionSoldEvent(AuctionRef(title, self), o.price)

      //start DeleteTimer
      import context.dispatcher
      context.system.scheduler.scheduleOnce(Duration(3, TimeUnit.SECONDS), self, DeleteFinishedAuctionCommand)

      goto(Sold) using o
  }

  when(Sold) {
    case Event(DeleteFinishedAuctionCommand, o: Offer) =>
      deleteItem()
      stop(FSM.Normal)

    case Event(e: AuctionUnregisteredEvent, o: Offer) =>
      seller ! e
      stay() using o
  }

  override def toString: String = {
    title
  }
}

case object Auction {
  //commands accepted by this actor:
  case class FinishAuctionCommand()
  case class BidCommand(offer: BigDecimal, bidder: ActorRef)
  case class DeleteFinishedAuctionCommand()

  //events produced by this actor:
  case class AuctionSoldEvent(auctionRef: AuctionRef, finalPrice: BigDecimal)
  case class BiggerOfferReceivedEvent(actualOffer: BigDecimal)
  case class YouWonAuctionEvent(auctionTitle: String, finalPrice: BigDecimal)
}
