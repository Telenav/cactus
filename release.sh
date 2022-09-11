#!/bin/bash

mvn -P sign-artifacts -P attach-jars clean deploy

echo " "
echo "Instructions to complete the release :"
echo " "
echo "  1. Sign into OSSRH (https://s01.oss.sonatype.org/)"
echo "  2. Close the staging repository and release it"
echo "  3. Wait for the release to be copied to Maven Central (https://repo1.maven.org/maven2/com/telenav/cactus/")
echo "  4. Run cbump"
echo " "
