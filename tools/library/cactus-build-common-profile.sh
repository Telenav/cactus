
source $KIVAKIT_WORKSPACE/cactus-build/tools/library/cactus-library-functions.sh

system_variable CACTUS_HOME "$KIVAKIT_WORKSPACE/cactus-build"
system_variable CACTUS_ASSETS_HOME "$KIVAKIT_WORKSPACE/cactus-build-assets"
system_variable CACTUS_VERSION "$(project_version $CACTUS_HOME)"
system_variable CACTUS_TOOLS "$CACTUS_HOME/tools"
system_variable CACTUS_JAVA_OPTIONS "-Xmx12g"

system_variable LEXAKAI_VERSION 0.9.5-alpha-SNAPSHOT

append_path "$CACTUS_TOOLS/building"
append_path "$CACTUS_TOOLS/library"

echo " "
echo "┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫ Cactus Environment ┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓"
echo "┋"
echo -e "┋              CACTUS_HOME: ${ATTENTION}$CACTUS_HOME${NORMAL}"
echo -e "┋           CACTUS_VERSION: ${ATTENTION}$CACTUS_VERSION${NORMAL}"
echo "┋       CACTUS_ASSETS_HOME: $CACTUS_ASSETS_HOME"
echo "┋"
echo "┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛"
echo " "
