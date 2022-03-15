#!/bin/bash

#///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#
#  © 2011-2021 Telenav, Inc.
#  Licensed under Apache License, Version 2.0
#
#///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

source cactus-library-functions.sh
source cactus-projects.sh

help="[version]"

version=$1

require_variable version "$help"

for project_home in "${CACTUS_PROJECT_HOMES[@]}"; do

    if ! git_flow_init "$project_home"; then

        exit 1

    fi

done

for project_home in "${CACTUS_PROJECT_HOMES[@]}"; do

    git_flow_release_start "$project_home" "$version"

done
