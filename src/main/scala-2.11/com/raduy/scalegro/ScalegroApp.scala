package com.raduy.scalegro

import akka.actor.{PoisonPill, ActorRef, ActorSystem, Props}
import com.raduy.scalegro.Buyer.LookForDesiredItemCommand
import com.raduy.scalegro.Seller.StartAuctionsCommand
import com.raduy.scalegro.notifier.Notifier
import com.typesafe.config.ConfigFactory

/**
  * @author Łukasz Raduj 2015.
  */
object ScalegroApp extends App {
  implicit val config = ConfigFactory.load()

  //audit system
  private val auctionPublisherSystem = ActorSystem("auction-publisher-system", config.getConfig("auction-publisher-system").withFallback(config))
  private val publisher: ActorRef = auctionPublisherSystem.actorOf(Props[AuctionPublisher], "auction-publisher")
  private val notifier: ActorRef = auctionPublisherSystem.actorOf(Props[Notifier], "notifier")

  //auction system
  private val auctionSystem = ActorSystem("auction-system", config.getConfig("auction-system").withFallback(config))
  private val auctionSearch: ActorRef = auctionSystem.actorOf(Props[AuctionSearch], "auction-search")

  private val auctions: List[String] = List("BMW i3 30")
  private val seller: ActorRef = auctionSystem.actorOf(Props(new Seller("Typowy Mirek", auctions, auctionSearch, notifier)))

  seller ! StartAuctionsCommand

  Thread.sleep(2000)
  private val lukasz: ActorRef = auctionSystem.actorOf(Props(new Buyer("Lukasz", "i3", 20000000)))
  private val antek: ActorRef = auctionSystem.actorOf(Props(new Buyer("Antek", "i3", 19000000)))
  lukasz ! LookForDesiredItemCommand
  Thread.sleep(100)
  antek ! LookForDesiredItemCommand


  //demo of auction-publisher external service become unavailable for a moment
  Thread.sleep(15000)
  println(
    "\n*******************************************\n" +
      "********* AUCTION PUBLISHER DOWN!!!! ******\n" +
      "*******************************************\n")
  publisher ! PoisonPill

  //having network problems or sth like that...
  Thread.sleep(5000)

  println(
    "\n*******************************************\n" +
      "********* AUCTION PUBLISHER UP!!!! ********\n" +
      "*******************************************\n")

  auctionPublisherSystem.actorOf(Props[AuctionPublisher], "auction-publisher")
}
