#!/bin/bash

#///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#
#  © 2011-2021 Telenav, Inc.
#  Licensed under Apache License, Version 2.0
#
#///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

source telenav-library-functions.sh

# 1) Parse Java version from output like: openjdk version "17.0.3" 2022-04-19 LTS
java_version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')

# 2) Parse Maven version from output like: Apache Maven 3.8.5 (3599d3414f046de2324203b78ddcf9b5e4388aa0)
maven_version=$(mvn -version 2>&1 | awk -F ' ' '/Apache Maven/ {print $3}')

# 3) Parse Git version from output like: git version 2.36.1
git_version=$(git --version 2>&1 | awk -F' ' '{print $3}')

# 4) Parse Git flow version from output like: 1.12.3 (AVH Edition)
git_flow_version=$(git flow version)

if [[ -d $KIVAKIT_HOME ]]; then

    kivakit_version=$(project_version "$KIVAKIT_HOME")

else

    kivakit_version="N/A"

fi

if [[ -d $MESAKIT_HOME ]]; then

    mesakit_version=$(project_version "$MESAKIT_HOME")

else

    mesakit_version="N/A"

fi


echo " "
echo "┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫ Versions ┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓"
echo "┋"
echo "┋  Repositories:"
echo "┋"
echo "┋          KivaKit: $kivakit_version"
echo "┋          MesaKit: $mesakit_version"
echo "┋"
echo "┋  Tools:"
echo "┋"
echo "┋             Java: $java_version"
echo "┋            Maven: $maven_version"
echo "┋              Git: $git_version"
echo "┋         Git Flow: $git_flow_version"
echo "┋"
echo "┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛"
echo " "
