package com.raduy.scalegro

import akka.actor.{ActorRef, ActorSystem, Props}
import com.raduy.scalegro.Buyer.LookForDesiredItemCommand
import com.raduy.scalegro.Seller.StartAuctionsCommand
import com.typesafe.config.ConfigFactory

/**
  * @author Łukasz Raduj 2015.
  */
object ScalegroApp extends App {
  implicit val config = ConfigFactory.load()

  private val system: ActorSystem = ActorSystem("Scalegro")

  private val auctionSearch: ActorRef = system.actorOf(Props[AuctionSearch], "auction-search")

//  private val auctions: List[String] = List("Audi RS6 C7 5.2 V10", "BMW i8", "Pagani Zonda R")
  private val auctions: List[String] = List("BMW i3")
  private val seller: ActorRef = system.actorOf(Props(new Seller("Typowy Mirek", auctions, auctionSearch)))

  seller ! StartAuctionsCommand

  Thread.sleep(3000)
  private val lukasz: ActorRef = system.actorOf(Props(new Buyer("Lukasz", "i3", 20000000)))
  private val antek: ActorRef = system.actorOf(Props(new Buyer("Lukasz", "i3", 19000000)))
  lukasz ! LookForDesiredItemCommand
  antek ! LookForDesiredItemCommand
}
