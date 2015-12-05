package com.raduy.scalegro

import akka.actor.ActorRef
import akka.persistence.fsm.PersistentFSM.FSMState

/**
 * @author Łukasz Raduj 2015.
 */
sealed trait AuctionState extends FSMState

case object Idle extends AuctionState {
  override def identifier: String = "Idle"
}
case object Created extends AuctionState {
  override def identifier: String = "Created"
}
case object Activated extends AuctionState {
  override def identifier: String = "Activated"
}
case object Ignored extends AuctionState {
  override def identifier: String = "Ignored"
}
case object Sold extends AuctionState {
  override def identifier: String = "Sold"
}

sealed trait AuctionData

case object NoOffer extends AuctionData

case class Offer(price: BigDecimal, bidder: ActorRef) extends AuctionData
