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

if [ "$CACTUS_VERSION" = "$version" ]; then

    echo " "
    echo "┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫ Building Release  ┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓"
    echo "┋"
    echo "┋ Release Version: $version"
    echo "┋"
    echo "┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛"
    echo " "

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

    echo " "
    echo "┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫ Creating Release  ┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓"
    echo "┋"
    echo "┋ Current Version: $CACTUS_VERSION"
    echo "┋ Release Version: $version"
    echo "┋"
    echo "┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛"

    if cactus-release-start.sh "$version"; then

        echo " "
        echo "┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫ Release Created  ┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓"
        echo "┋"
        echo "┋  1. The branch release/$version has been created with git flow "
        echo "┋  2. Build files have been updated from $CACTUS_VERSION to $version"
        echo "┋"
        echo "┋  EXIT YOUR TERMINAL PROGRAM ENTIRELY and restart it, then re-execute the command:"
        echo "┋"
        echo "┋  $(basename $0) $1"
        echo "┋"
        echo "┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛"

    fi

fi

