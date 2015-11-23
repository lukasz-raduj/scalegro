package com.raduy.scalegro

import akka.actor.{ActorRef, ActorSystem, Props}
import com.raduy.scalegro.Buyer.LookForDesiredItemCommand
import com.raduy.scalegro.Seller.StartAuctionsCommand

/**
  * @author Łukasz Raduj 2015.
  */
object ScalegroApp extends App {
  private val system: ActorSystem = ActorSystem("Scalegro")

  private val auctionSearch: ActorRef = system.actorOf(Props[AuctionSearch], "auction-search")

  private val auctions: List[String] = List("Audi RS6 C7 5.2 V10", "BMW i8", "Pagani Zonda R")
  private val seller: ActorRef = system.actorOf(Props(new Seller("Typowy Mirek", auctions, auctionSearch)))

  seller ! StartAuctionsCommand

  Thread.sleep(1000)
  private val buyer: ActorRef = system.actorOf(Props(new Buyer("Lukasz", "RS6", 100000)))
  buyer ! LookForDesiredItemCommand


  //  private val scalegro: ActorRef = system.actorOf(Props[AuctionSystem], "auction_system")
  //
  //  scalegro ! CreateNewAuctionCommand("Google Nexus 5", "Best mobile phone")
  //  scalegro ! CreateNewAuctionCommand("MacBook Pro", "Good laptop just for you")
  //  scalegro ! CreateNewAuctionCommand("Blue jeans", "Just old jeans")
  //
  //  scalegro ! CreateNewBuyerCommand("Lukasz")
  //  scalegro ! CreateNewBuyerCommand("Jacek")
  //  scalegro ! CreateNewBuyerCommand("Pawel")
  //
  //  scalegro ! DoDemoCommand()
  system.awaitTermination()
}
