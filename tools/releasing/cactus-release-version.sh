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

for project_home in "${CACTUS_ALL_HOMES[@]}"; do

    update_version $project_home $version

done
