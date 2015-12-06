package com.raduy.scalegro

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

import akka.actor.{ActorLogging, ActorRef}
import akka.persistence.fsm.PersistentFSM
import com.raduy.scalegro.Auction.AuctionEvents.{AuctionEvent, _}
import com.raduy.scalegro.Auction._
import com.raduy.scalegro.AuctionSearch.{AuctionRegisteredEvent, AuctionUnregisteredEvent, RegisterAuctionCommand, UnregisterAuctionCommand}
import com.raduy.scalegro.notifier.Notifier
import Notifier.{AuctionStartedNotification, NewOfferInAuctionNotification}
import com.raduy.scalegro.Seller.AuctionRef

import scala.concurrent.duration.Duration
import scala.reflect.ClassTag

/**
  * @author Łukasz Raduj 2015.
  */
class Auction(title: String, seller: ActorRef, auctionSearch: ActorRef, notifier: ActorRef)
  extends PersistentFSM[AuctionState, AuctionData, AuctionEvent] with ActorLogging {

  var price: BigDecimal = 0
  var startDate: LocalDateTime = _
  val DURATION_SECONDS = 3000
  val BEFORE_DELETION_SECONDS = 60

  //for demo purposes only!
  var processedEventsCounter = 0
  val recoveryStartupDateTime = LocalDateTime.now()

  import context.dispatcher

  override def domainEventClassTag: ClassTag[AuctionEvent] = ClassTag(classOf[AuctionEvent])

  override def persistenceId: String = s"auction2.0-$title"

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
  startWith(Idle, NoOffer)

  when(Idle) {
    case Event(StartAuctionCommand, NoOffer) => {
      val now: LocalDateTime = LocalDateTime.now()
      goto(Created) applying AuctionStartedEvent(now, sender()) andThen {
        case _ =>
          val auctionEndDate: LocalDateTime = now.plusSeconds(DURATION_SECONDS)
          log.debug("Scheduling auction to finish at: {}", auctionEndDate)
          self ! ScheduleBidTimerCommand(auctionEndDate)
      }
    }
  }

  when(Created) {
    case Event(ScheduleBidTimerCommand(finishDate: LocalDateTime), NoOffer) =>
      stay() applying AuctionFinishScheduledEvent(finishDate) andThen {
        case _ =>
          scheduleBidTimer(finishDate)
          notifier ! AuctionStartedNotification(title)
      }

    case Event(BidCommand(offer: BigDecimal, bidder: ActorRef), NoOffer) =>
      log.debug("First offer in '{}' received! Offer value: {}", title, offer)
      goto(Activated) applying BidOfferReceivedEvent(offer, bidder) andThen {
        case _ =>
          notifyPublisherAboutNewOffer(offer, bidder)
      }

    case Event(FinishAuctionCommand, NoOffer) =>
      val now = LocalDateTime.now()
      goto(Ignored) applying AuctionFinishedEvent(now) andThen {
        case _ =>
          finishAuction()
          val deletionDateTime: LocalDateTime = now.plusSeconds(BEFORE_DELETION_SECONDS)
          scheduleDeleteTimer(deletionDateTime)
      }

    case Event(e: AuctionRegisteredEvent, NoOffer) =>
      seller ! e
      stay()

    case Event(e: AuctionUnregisteredEvent, NoOffer) =>
      seller ! e
      stay()
  }

  def notifyPublisherAboutNewOffer(offer: BigDecimal, bidder: ActorRef) = {
    notifier ! NewOfferInAuctionNotification(title, offer, bidder)
  }

  when(Activated) {
    case Event(BidCommand(offer: BigDecimal, bidder: ActorRef), o: Offer) => {
      log.debug("New offer in '{}' auction! Offer value: {}", title, offer)
      stay() applying BidOfferReceivedEvent(offer, bidder) andThen {
        case _ =>
          notifyPublisherAboutNewOffer(offer, bidder)
      }
    }

    case Event(FinishAuctionCommand, o: Offer) => {
      log.debug("Finishing auction '{}'! Final Price: {}", title, o.price)
      val now: LocalDateTime = LocalDateTime.now()
      goto(Sold) applying AuctionFinishedEvent(now) andThen {
        case _ =>
          finishAuction()
          // notify buyer
          o.bidder ! YouWonAuctionEvent(title, o.price)
          // notify seller
          seller ! AuctionSoldEvent(AuctionRef(title, self), o.price)
          // start DeleteTimer
          val deleteAtDateTime: LocalDateTime = now.plusSeconds(BEFORE_DELETION_SECONDS)
          scheduleDeleteTimer(deleteAtDateTime)
      }
    }
  }

  when(Ignored) {
    case Event(DeleteFinishedAuctionCommand, NoOffer) =>
      deleteItem()
      stop()

    case Event(e: AuctionUnregisteredEvent, NoOffer) =>
      seller ! e
      stay()
  }

  when(Sold) {
    case Event(DeleteFinishedAuctionCommand, o: Offer) =>
      deleteItem()
      stop()

    case Event(e: AuctionUnregisteredEvent, o: Offer) =>
      seller ! e
      stay()
  }

  override def applyEvent(event: AuctionEvent, dataBeforeEvent: AuctionData): AuctionData = {
    log.debug("In state: {} for {} auction with {} event", dataBeforeEvent, title, event)
    processedEventsCounter += 1

    event match {
      case BidOfferReceivedEvent(offer: BigDecimal, bidder: ActorRef) => {
        acceptNewOffer(dataBeforeEvent, offer, bidder)
      }

      case AuctionStartedEvent(auctionStartDate: LocalDateTime, seller: ActorRef) =>
        startDate = auctionStartDate
        dataBeforeEvent

      case _ =>
        dataBeforeEvent
    }
  }

  def acceptNewOffer(dataBeforeEvent: AuctionData, offer: BigDecimal, bidder: ActorRef): Offer = {
    dataBeforeEvent match {
      case Offer(existingPrice: BigDecimal, existingBidder: ActorRef) =>
        if (offer > existingPrice) {
          existingBidder ! BiggerOfferReceivedEvent(offer)
          Offer(offer, bidder)
        } else {
          bidder ! TooLittleOfferGivenEvent(offer)
          Offer(existingPrice, existingBidder)
        }

      case NoOffer =>
        Offer(offer, bidder)
    }
  }


  def scheduleBidTimer(finishDate: LocalDateTime): Unit = {
    val timeTillAuctionFinish: Long = LocalDateTime.now().until(finishDate, ChronoUnit.SECONDS)
    context.system.scheduler.scheduleOnce(Duration(timeTillAuctionFinish, TimeUnit.SECONDS),
      self,
      FinishAuctionCommand
    )
  }

  def scheduleDeleteTimer(deletionDateTime: LocalDateTime) = {
    val tillAuctionDeletion: Long = LocalDateTime.now().until(deletionDateTime, ChronoUnit.SECONDS)
    context.system.scheduler.scheduleOnce(Duration(tillAuctionDeletion, TimeUnit.SECONDS),
      self,
      DeleteFinishedAuctionCommand
    )
  }

  def rescheduleTimers(auctionStartDate: LocalDateTime) = {
    val now: LocalDateTime = LocalDateTime.now()
    if (auctionStartDate.plusSeconds(DURATION_SECONDS).isAfter(now)) {
      //DeleteTimer
      scheduleDeleteTimer(auctionStartDate.plusSeconds(DURATION_SECONDS + BEFORE_DELETION_SECONDS))
    } else {
      //BidTimer
      scheduleBidTimer(auctionStartDate.plusSeconds(DURATION_SECONDS))
    }
  }

  override def onRecoveryCompleted(): Unit = {
    super.onRecoveryCompleted()

    if (stateName != Sold && stateName != Ignored) {
      auctionSearch ! RegisterAuctionCommand(AuctionRef(title, self))
    } else {
      println("not registering")
    }

    if (isAuctionInitialized) {
      rescheduleTimers(startDate)
    }

    log.error(
      "\n*******************************************************\n" +
        "*** RECOVERED! PROCESSED {} EVENTS FROM COMMIT LOG. ***\n" +
        "*** TIME CONSUMED: {} MILLISECONDS ****************\n" +
        "*******************************************************\n",
      processedEventsCounter,
      recoveryStartupDateTime.until(LocalDateTime.now(), ChronoUnit.MILLIS))

    log.debug("Auction with id : {} recovered to state: {} and offer {}", persistenceId, stateName, stateData)
  }

  def isAuctionInitialized: Boolean = {
    stateName != Idle && (stateName != Sold || stateName == Ignored)
  }

  override def toString: String = {
    s"Auction with title: $title state: $stateName and data: $stateData"
  }
}


case object Auction {

  case class AuctionTimersConfig(finishDate: LocalDateTime, deleteDate: LocalDateTime)

  //commands accepted by this actor:
  case class FinishAuctionCommand()

  case class ScheduleBidTimerCommand(finishDate: LocalDateTime)

  case class BidCommand(offer: BigDecimal, bidder: ActorRef)

  case class DeleteFinishedAuctionCommand()

  case object StartAuctionCommand

  //events produced by this actor:
  case class AuctionSoldEvent(auctionRef: AuctionRef, finalPrice: BigDecimal)

  case class BiggerOfferReceivedEvent(actualOffer: BigDecimal)

  case class YouWonAuctionEvent(auctionTitle: String, finalPrice: BigDecimal)

  case class TooLittleOfferGivenEvent(actualOffer: BigDecimal)

  object AuctionEvents {

    sealed trait AuctionEvent

    case class BidOfferReceivedEvent(offer: BigDecimal, bidder: ActorRef) extends AuctionEvent

    case class AuctionFinishScheduledEvent(finishDate: LocalDateTime) extends AuctionEvent

    case class AuctionFinishedEvent(finishedAtDate: LocalDateTime) extends AuctionEvent

    case class AuctionStartedEvent(endDate: LocalDateTime, seller: ActorRef) extends AuctionEvent

  }

}
