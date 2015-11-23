package com.raduy.scalegro

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestFSMRef, TestKit, TestProbe}
import com.raduy.scalegro.Auction._
import com.raduy.scalegro.AuctionSearch.{RegisterAuctionCommand, UnregisterAuctionCommand}
import com.raduy.scalegro.Seller.AuctionRef
import org.scalatest.{BeforeAndAfterAll, GivenWhenThen, WordSpecLike}

/**
  * Created by raduj on 22/11/15.
  */
class AuctionSpec extends TestKit(ActorSystem("ScalegroTest"))
with WordSpecLike
with BeforeAndAfterAll
with ImplicitSender with GivenWhenThen {

  override def afterAll = {
    system.shutdown()
  }

  val seller = TestProbe()
  val auctionSearch = TestProbe()
  val auctionTitle: String = "BMW i8"


  "Auction" should {

    "notify Seller and Buyer after winning" in {
      Given("auction without any offers")
      val auction: ActorRef = system.actorOf(Props(new Auction(auctionTitle, seller.ref, auctionSearch.ref)))
      auctionSearch.expectMsg(RegisterAuctionCommand(AuctionRef(auctionTitle, auction)))

      When("it's time to finish auction")
      auction ! FinishAuctionCommand

      Then("auction should unregister itself from auction search")
      auctionSearch.expectMsg(UnregisterAuctionCommand(AuctionRef(auctionTitle, auction)))
    }

    "register itself in AuctionSearch just before start" in {
      Given("seller actor")
      val auctionSearch = TestProbe()
      val seller = TestProbe().ref

      When("creating new auction")
      val auction: ActorRef = system.actorOf(Props(new Auction("Audi RS6", seller, auctionSearch.ref)))

      Then("new auction was registered in auction search")
      auctionSearch expectMsg RegisterAuctionCommand(AuctionRef("Audi RS6", auction))
    }

    "change state to Activated after first offer" in {
      Given("new auction without any offer")
      val fsm = TestFSMRef(new Auction(auctionTitle, seller.ref, auctionSearch.ref))
      assert(fsm.stateName == Created)

      And("some buyer actor")
      val buyer = TestProbe()

      When("buyer send some offer")
      fsm ! BidCommand(100, buyer.ref)

      Then("auction should state to activated")
      assert(fsm.stateName == Activated)
    }

    "change state to Ignored after bid timer expired" in {
      Given("auction in Created state")
      val fsm = TestFSMRef(new Auction(auctionTitle, seller.ref, auctionSearch.ref))

      When("bid timer expired")
      fsm ! FinishAuctionCommand

      Then("action change state to Ignored")
      assert(fsm.stateName == Ignored)
    }

    "notify seller about sold auction" in {
      Given("auction with offers")
      val auction: ActorRef = system.actorOf(Props(new Auction(auctionTitle, seller.ref, auctionSearch.ref)))
      val bidder = TestProbe()
      auction ! BidCommand(100, bidder.ref)

      When("bid timer expired")
      auction ! FinishAuctionCommand

      Then("seller was notified")
      seller.expectMsg(AuctionSoldEvent(AuctionRef(auctionTitle, auction), 100))
    }

    "notify buyer about sold auction" in {
      Given("auction with offers")
      val auction: ActorRef = system.actorOf(Props(new Auction(auctionTitle, seller.ref, auctionSearch.ref)))
      val bidder = TestProbe()
      auction ! BidCommand(100, bidder.ref)

      When("bid timer expired")
      auction ! FinishAuctionCommand

      Then("seller was notified")
      bidder.expectMsg(YouWonAuctionEvent(auctionTitle, 100))
    }

    "not accept offers with smaller value than actual" in {
      Given("auction in Activated state")
      val bidder = TestProbe()
      val fsm = TestFSMRef(new Auction(auctionTitle, seller.ref, auctionSearch.ref))
      fsm.setState(Activated, Offer(100, bidder.ref))
      And("bidder giving smaller offer")
      val smallerOfferBidder = TestProbe()

      When("smaller offer receive")
      fsm ! BidCommand(90, smallerOfferBidder.ref)

      Then("smaller offer was not accepted")
      assert(fsm.stateData == Offer(100, bidder.ref))
    }

    "notify bidder when someone offers more" in {
      Given("auction with some offers")
      val auction: ActorRef = system.actorOf(Props(new Auction(auctionTitle, seller.ref, auctionSearch.ref)))
      val bidder = TestProbe()
      auction ! BidCommand(100, bidder.ref)
      val biggerOfferBidder = TestProbe()

      When("bigger offer comes")
      auction ! BidCommand(120, biggerOfferBidder.ref)

      Then("bidder was notified")
      bidder.expectMsg(BiggerOfferReceivedEvent(120))
    }
  }
}