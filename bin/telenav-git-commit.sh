#!/bin/bash

#///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#
#  © 2011-2021 Telenav, Inc.
#  Licensed under Apache License, Version 2.0
#
#///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

source telenav-library-functions.sh

message=$1

if [[ "$message" == "" ]]; then

    read -p "Commit message? " -r
    message=$REPLY

fi

cd_workspace
scope=$(repository_scope)
echo mvn --quiet "$scope" -Dtelenav.commit-message=\""$message"\" -Dtelenav.update-root=true com.telenav.cactus:cactus-build-maven-plugin:commit || exit 1
