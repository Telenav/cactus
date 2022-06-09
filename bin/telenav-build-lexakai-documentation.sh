#!/bin/bash

#///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#
#  © 2011-2021 Telenav, Inc.
#  Licensed under Apache License, Version 2.0
#
#///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

source telenav-library-functions.sh

#
# telenav-build-lexakai-documentation.sh [scope]?
#
# scope = { all, this, <family-name> }
#

cd_workspace
scope=$(repository_scope "$1")
mvn --quiet "$scope" com.telenav.cactus:cactus-build-maven-plugin:lexakai || exit 1
