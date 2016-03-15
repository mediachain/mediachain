package io.mediachain

import com.orientechnologies.orient.core.Orient

// See http://stackoverflow.com/a/9901616
// These classes run setup / cleanup code when instantiated
// Used in Tests.setup and Tests.cleanup hooks in build.sbt

class SBTSetupHook {
  // Set the classloader to the JVM default, instead of the restricted sbt classloader
  Thread.currentThread().setContextClassLoader(this.getClass.getClassLoader)

  // The default Orient shutdown hook runs on JVM shutdown, but we want it
  // to run after each test run, otherwise Orient will complain that its
  // beans are already registered
  // (or something... see https://github.com/orientechnologies/orientdb/issues/4967)
  Orient.instance().removeShutdownHook()
}

class SBTCleanupHook {
  // Manually shutdown orient after testing, since we removed the shutdown hook above
  Orient.instance().shutdown()
}
