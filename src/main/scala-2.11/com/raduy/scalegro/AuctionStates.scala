package com.raduy.scalegro

import akka.actor.ActorRef

/**
 * @author Łukasz Raduj 2015.
 */
sealed trait AuctionState

case object Created extends AuctionState

case object Ignored extends AuctionState

case object Activated extends AuctionState

case object Sold extends AuctionState

sealed trait AuctionData

case object NoOffer extends AuctionData

case class Offer(price: BigDecimal, bidder: ActorRef) extends AuctionData
