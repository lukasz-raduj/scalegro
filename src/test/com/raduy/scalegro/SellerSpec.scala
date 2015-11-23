package com.raduy.scalegro

import akka.actor.{Props, ActorRef, ActorSystem}
import akka.testkit.{TestProbe, ImplicitSender, TestKit}
import com.raduy.scalegro.AuctionSearch.RegisterAuctionCommand
import com.raduy.scalegro.Seller.{StartAuctionsCommand, AuctionRef}
import org.scalatest.{BeforeAndAfterAll, GivenWhenThen, WordSpecLike}

/**
  * Created by raduj on 23/11/15.
  */
class SellerSpec extends TestKit(ActorSystem("ScalegroTest"))
with WordSpecLike
with BeforeAndAfterAll
with ImplicitSender with GivenWhenThen {


  override def afterAll = {
    system.shutdown()
  }


  "Seller" should {

    "create new auctions when starting auction" in {
      Given("couple of auctions")
      val auctions = List("Pagani Zonda", "Arrinera Hussarya")

      And("seller")
      val auctionSearch = TestProbe()
      val seller: ActorRef = system.actorOf(Props(new Seller("Mirek", auctions, auctionSearch.ref)))


      When("it's time to start auctions")
      seller ! StartAuctionsCommand


      Then("seller should register them in auction search")
      auctionSearch.expectMsgClass(classOf[RegisterAuctionCommand])
      auctionSearch.expectMsgClass(classOf[RegisterAuctionCommand])
    }
  }
}