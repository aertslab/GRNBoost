package org.aertslab.grnboost.lab

import org.scalatest.{FlatSpec, Matchers}

/**
  * @author Thomas Moerman
  */
class ResourceLab extends FlatSpec with Matchers {

  "Some class" should "behave correctly as managed resource" in {

    class Bla {
      def shout = "bla"
      def klose: Unit = println("closed")
    }

    val result =
      resource
        .makeManagedResource(new Bla())(_.klose)(Nil)
        .map(_.shout)
        .tried
        .get

    println(result)
  }

}
