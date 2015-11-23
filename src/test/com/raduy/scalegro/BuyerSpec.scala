package com.raduy.scalegro

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.raduy.scalegro.Auction.{BidCommand, BiggerOfferReceivedEvent}
import org.scalatest.{BeforeAndAfterAll, GivenWhenThen, WordSpecLike}

/**
  * Created by raduj on 23/11/15.
  */
class BuyerSpec extends TestKit(ActorSystem("ScalegroTest"))
with WordSpecLike
with BeforeAndAfterAll
with ImplicitSender with GivenWhenThen {

  override def afterAll = {
    system.shutdown()
  }

  "Buyer" should {

    "send new offer when someone gave bigger offer if have enough money" in {
      Given("buyer with some budget")
      val auction = TestProbe()
      val buyer: ActorRef = system.actorOf(Props(new Buyer("Raduj", "RS 6", 500000)))

      When("auction notify about bigger offer")
      auction.send(buyer, BiggerOfferReceivedEvent(20000))

      Then("buyer sends bigger offer if can afford")
      auction.expectMsg(BidCommand(22000, buyer))
    }

    "not send new offer when someone gave bigger offer but buyer don't have more money to spend" in {
      Given("buyer with some budget")
      val auction = TestProbe()
      val buyer: ActorRef = system.actorOf(Props(new Buyer("Raduj", "RS 6", 500000)))

      When("auction notify about bigger offer")
      auction.send(buyer, BiggerOfferReceivedEvent(600000))

      Then("buyer didn't send bigger offer")
      auction.expectNoMsg()
    }
  }
}
