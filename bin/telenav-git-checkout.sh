#!/bin/bash

#///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#
#  © 2011-2021 Telenav, Inc.
#  Licensed under Apache License, Version 2.0
#
#///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

source telenav-library-functions.sh


#
# checkout [branch]
#

if [[ "$#" -eq 1 ]]; then
    branch=$1
fi

#
# checkout [scope] [branch]
#

if [[ "$#" -eq 2 ]]; then
    scope=$1
    branch=$2
fi

#
# checkout
#

if [[ "$#" -eq 0 ]]; then

    read -p "Branch? " -r
    branch=$REPLY

fi

cd_workspace
scope=$(repository_scope "$scope")
mvn --quiet "$scope" -Dtelenav.branch="$branch" -Dtelenav.permit-local-modifications=false com.telenav.cactus:cactus-build-maven-plugin:checkout || exit 1
