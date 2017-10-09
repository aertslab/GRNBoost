package org.aertslab.grnboost

import org.scalatest.Tag

/**
  * @author Thomas Moerman
  */
object Specs {

  /**
    * Test tag for tests that require running on a server, with the data available and sufficient memory.
    * Tests annotated with this tag will probably be removed from the final release.
    */
  object Server extends Tag("org.aertslab.grnboost.Server")

}
