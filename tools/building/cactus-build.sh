#!/bin/bash

#///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#
#  © 2011-2021 Telenav, Inc.
#  Licensed under Apache License, Version 2.0
#
#///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

source "$CACTUS_HOME"/tools/library/cactus-library-functions.sh
source "$CACTUS_HOME"/tools/library/cactus-library-build.sh
source "$CACTUS_HOME"/tools/library/cactus-projects.sh

for project_home in "${CACTUS_PROJECT_HOMES[@]}"; do

    build "$project_home" $@

done
