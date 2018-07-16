These instructions are meant for developers interested in work on the IGV code.  For normal use,
we recommend the pre-built releases available at www.igv.org.

The IGV JAR build now uses Gradle instead of Ant.  

Install Gradle for your platform.  See https://gradle.org/ for details.

Builds are executed from the IGV project directory.  Files will be created in the 'build' subdirectory.
You may need to execute 'gradle wrapper' to set up the gradle wrapper.  This should be necessary the 
first time only, or if you clean up the local .gradle directory.  Only do this if the './gradlew' calls
fail.

There are two different build options, one for Java 8 and another for Java 9 and up.  The default is 
to build for Java 8.  Java 8 builds are *NOT* compatible with Java 9 and vice versa.  

There are other options but these cover the most common uses:
- Use './gradlew createDist' to build a "distribution" directory (found in 'build/dist') containing 
  igv.jar and its third-party library required runtime JAR dependencies (batik-codec, goby, and 
  log4j-core) as well as helper scripts for launching.
  - These four JARs will be identical to those available in the download bundles from our website, 
    with the exception that they will not be signed with our certificate (required for JNLP) and
    will have slightly different build properties (timestamp, etc) in about.properties.
  - All four JARs must be in the same location in order to run IGV. 
  - Launch with 'igv.sh' on UNIX, 'igv.command' on Mac, and 'igv.bat' on Windows.  These scripts can
    be edited to adjust e.g. JVM flags like maximum memory, etc.
  - All other runtime dependencies are bundled into igv.jar.  There is also an igv-minimal.jar in
    'build/libs' containing just the IGV classes and resources for those who prefer to manage 
    dependencies as separate files.
- Use './gradlew build' to build everything and run the test suite.  See 'src/test/README.txt' for more
  information about running the tests.

Note that Gradle creates a number of other subdirectories in 'build'.  These can be safely ignored.

The instructions for Java 9 are nearly identical other than the need to specify the Java 9 build file
and that the results will be found in 'build_java9' rather than 'build'.  More specifically:
- Use './gradlew -b build_java9.gradle createDist' to build a distribution directory with helper scripts
  for launching.  The structure is slightly different but the concept is the same.
- Use './gradlew -b build_java9.gradle build' to build everything and run the test suite.

The full JAR build option is *NOT* available for Java 9+ because of modularilty requirements.

NOTE: In the above, use './gradlew.bat' on the Windows platform.