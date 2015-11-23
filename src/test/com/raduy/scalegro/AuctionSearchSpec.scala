package com.raduy.scalegro

import akka.actor.{Props, ActorRef, ActorSystem}
import akka.testkit.{TestProbe, ImplicitSender, TestKit}
import com.raduy.scalegro.AuctionSearch._
import com.raduy.scalegro.Seller.AuctionRef
import org.scalatest.{BeforeAndAfterAll, GivenWhenThen, WordSpecLike}

/**
  * Created by raduj on 23/11/15.
  */
class AuctionSearchSpec extends TestKit(ActorSystem("ScalegroTest"))
with WordSpecLike
with BeforeAndAfterAll
with ImplicitSender with GivenWhenThen {


  override def afterAll = {
    system.shutdown()
  }

  val seller = TestProbe()
  val auction = TestProbe()

  "Auction Search" should {

    "be able to register new auction" in {
      Given("auction search listening for new auctions")
      val auctionSearch: ActorRef = system.actorOf(Props[AuctionSearch])

      When("registering new auction")
      val auctionRef: AuctionRef = AuctionRef("Audi RS6 C7", auction.ref)
      seller.send(auctionSearch, RegisterAuctionCommand(auctionRef))

      Then("auction should be registered and seller should get a confirmation event")
      seller.expectMsg(AuctionRegisteredEvent(auctionRef))
    }

    "be able to unregister existing auction" in {
      Given("auction search with auction")
      val auctionSearch: ActorRef = system.actorOf(Props[AuctionSearch])
      val auctionRef: AuctionRef = AuctionRef("Audi RS6 C7", auction.ref)
      auction.send(auctionSearch, RegisterAuctionCommand(auctionRef))
      auction.expectMsg(AuctionRegisteredEvent(auctionRef))

      When("un-registering auction")
      auction.send(auctionSearch, UnregisterAuctionCommand(auctionRef))

      Then("auction should be un-registered and seller should get a confirmation event")
      auction.expectMsg(AuctionUnregisteredEvent(auctionRef))
    }

    "returns all matching auctions by keyword" in {
      Given("auction search with couple of auctions registered")
      val keyword: String = "Audi"
      val auctionSearch: ActorRef = system.actorOf(Props[AuctionSearch])

      val audiRs7Auction: ActorRef = TestProbe().ref
      registerAuction(auctionSearch, "Audi RS7 5.2 V10", audiRs7Auction)

      val bmw120DAuction: ActorRef = TestProbe().ref
      registerAuction(auctionSearch, "BMW 120D black 170ps", bmw120DAuction)

      When("buyer asks for matching auctions")
      val buyer = TestProbe()
      buyer.send(auctionSearch, FindByKeyWordQuery(keyword))

      Then("auction search returns only matching auctions")
      buyer.expectMsg(QueryResultEvent(keyword, Set(AuctionRef("Audi RS7 5.2 V10", audiRs7Auction))))
    }
  }

  def registerAuction(auctionSearch: ActorRef, auctionTitle: String, auction: ActorRef): Unit = {
    val auctionRef: AuctionRef = AuctionRef(auctionTitle, auction)
    seller.send(auctionSearch, RegisterAuctionCommand(auctionRef))
    seller.expectMsg(AuctionRegisteredEvent(auctionRef))
  }
}