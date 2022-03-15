#!/bin/bash

#///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#
#  © 2011-2021 Telenav, Inc.
#  Licensed under Apache License, Version 2.0
#
#///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

source library-functions.sh
source cactus-projects.sh

help="[version]"

version=$1

require_variable version "$help"

if [ "$CACTUS_HOME" = "$version" ]; then

    bash cactus-build.sh
    bash cactus-build-documentation.sh
    bash cactus-build.sh deploy-local

    echo " "
    echo "┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫ Release Created  ┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓"
    echo "┋"
    echo "┋  1. The branch release/$version has been created with git flow "
    echo "┋  2. Files containing version information such as pom.xml files have been updated to $version"
    echo "┋  3. The project and its documentation have been built"
    echo "┋  4. The project has been re-built from scratch and deployed to the local repository"
    echo "┋"
    echo "┋  Next Steps:"
    echo "┋"
    echo "┋  1. Check the release/$version branch carefully to make sure it's ready to go"
    echo "┋  2. Run cactus-release-finish.sh $version"
    echo "┋  3. Run cactus-build.sh deploy-ossrh"
    echo "┋  4. Sign into OSSRH and release to Maven Central"
    echo "┋"
    echo "┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛"
    echo " "

else

    bash cactus-release-start.sh "$version"
    bash cactus-release-update-version.sh "$version"

    echo " "
    echo "┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫ Release Created  ┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓"
    echo "┋"
    echo "┋  1. The branch release/$version has been created with git flow "
    echo "┋  2. Files containing version information such as pom.xml files have been updated to $version"
    echo "┋"
    echo "┋  Exit your terminal program entirely and restart it, then re-execute the command:"
    echo "┋"
    echo "┋  $0"
    echo "┋"
    echo "┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛"


fi

