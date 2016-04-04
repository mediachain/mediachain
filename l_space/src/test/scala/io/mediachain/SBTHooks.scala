package io.mediachain

import com.orientechnologies.orient.core.Orient

// See http://stackoverflow.com/a/9901616
// These classes run setup / cleanup code when instantiated
// Used in Tests.setup and Tests.cleanup hooks in build.sbt

class SBTSetupHook {
  // The default Orient shutdown hook runs on JVM shutdown, but we want it
  // to run after each test run, otherwise Orient will complain that its
  // beans are already registered

  // Accessing the Orient.instance here also prevents a NoClassDefFound error
  // during test execution.
  Orient.instance().removeShutdownHook()
}

class SBTCleanupHook {
  // Manually shutdown orient after testing, since we removed the shutdown hook above
  Orient.instance().shutdown()
}
