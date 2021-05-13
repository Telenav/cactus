#!/bin/bash

#///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#
#  © 2011-2021 Telenav, Inc.
#  Licensed under Apache License, Version 2.0
#
#///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

source $CACTUS_HOME/tools/library/cactus-library-functions.sh
source $CACTUS_HOME/tools/library/cactus-projects.sh

for project_home in "${CACTUS_PROJECT_HOMES[@]}"; do

    project_name=$(project_name $project_home)

    lexakai -project-version=$CACTUS_VERSION -output-folder=$CACTUS_ASSETS_HOME/docs/lexakai/$project_name $project_home

done
