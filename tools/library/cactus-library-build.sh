#///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#
#  © 2011-2021 Telenav, Inc.
#  Licensed under Apache License, Version 2.0
#
#///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

usage() {

    SCRIPT=$1

    echo " "
    echo "Usage: $SCRIPT [build-type] [build-modifiers]*"
    echo " "
    echo "  Build types:"
    echo " "
    echo "       [default] - compile, and run quick tests"
    echo " "
    echo "             all - compile, run tests, build tools and javadoc"
    echo " "
    echo "           tools - compile, run tests, build tools"
    echo " "
    echo "         compile - compile (no tests)"
    echo " "
    echo "         javadoc - compile and build javadoc"
    echo " "
    echo " javadoc-package - package and build javadoc"
    echo " "
    echo "  Build modifiers:"
    echo " "
    echo "           debug - turn maven debug mode on"
    echo " "
    echo "     debug-tests - stop in debugger on surefire tests"
    echo " "
    echo "      no-javadoc - do not build javadoc"
    echo " "
    echo "        no-tests - do not run tests"
    echo " "
    echo " single-threaded - build with only one thread"
    echo " "
    echo "           quiet - build with minimal output"
    echo " "
    echo "            show - show maven command line but don't build"
    echo " "
    echo "       sparkling - prompt to remove entire .m2 repository and all cached and temporary files"
    echo " "
    echo "           tests - run all tests"
    echo " "
    exit 1
}

if [[ "$1" == "help" ]]; then

    SCRIPT=$(basename -- "$0")
    usage $SCRIPT
fi

addSwitch() {

    SWITCH="$1"

    if [ -z "$SWITCHES" ]; then
        SWITCHES=$SWITCH
    else
        SWITCHES="$SWITCHES $SWITCH"
    fi
}

build() {

    PROJECT=$1
    PROJECT_NAME=$(basename $PROJECT)
    BUILD_TYPE=$2

    case "${BUILD_TYPE}" in

    "all")
        JAVADOC=true
        BUILD_ARGUMENTS="clean install"
        BUILD_MODIFIERS="multi-threaded tests tools ${@:3}"
        ;;

    "test")
        BUILD_ARGUMENTS="clean install"
        BUILD_MODIFIERS="single-threaded tests no-javadoc ${@:3}"
        ;;

    "tools")
        BUILD_ARGUMENTS="clean install"
        BUILD_MODIFIERS=(multi-threaded tests tools no-javadoc ${@:3})
        ;;

    "compile")
        BUILD_ARGUMENTS="clean compile"
        BUILD_MODIFIERS=(multi-threaded no-tests no-javadoc quiet ${@:3})
        ;;

    "javadoc")
        JAVADOC="true"
        BUILD_ARGUMENTS="clean compile"
        BUILD_MODIFIERS=(multi-threaded no-tests javadoc ${@:3})
        ;;

    "javadoc-package")
        JAVADOC="true"
        BUILD_ARGUMENTS="clean package"
        BUILD_MODIFIERS=(multi-threaded no-tests javadoc-package ${@:3})
        ;;

    *)
        BUILD_TYPE="default"
        BUILD_ARGUMENTS="clean install"
        BUILD_MODIFIERS=(multi-threaded no-javadoc ${@:2})
        ;;

    esac

    BUILD_MODIFIERS_STRING=""
    DELIMITER=""

    for MODIFIER in ${BUILD_MODIFIERS[@]}; do

        BUILD_MODIFIERS_STRING="$BUILD_MODIFIERS_STRING$DELIMITER$MODIFIER"
        DELIMITER=" "

        case "$MODIFIER" in

        "tools")
            addSwitch "-P tools"
            ;;

        "javadoc")
            if [ ! -z "$JAVADOC" ]; then
                BUILD_ARGUMENTS="$BUILD_ARGUMENTS javadoc:aggregate"
            fi
            ;;

        "javadoc-package")
            if [ ! -z "$JAVADOC" ]; then
                BUILD_ARGUMENTS="$BUILD_ARGUMENTS javadoc:aggregate"
            fi
            ;;

        "multi-threaded")
            THREADS=12
            ;;

        "single-threaded")
            THREADS=1
            ;;

        "no-javadoc")
            addSwitch "-Dmaven.javadoc.skip=true"
            ;;

        "debug")
            addSwitch "--debug"
            ;;

        "debug-tests")
            addSwitch "-Dmaven.surefire.debug"
            ;;

        "no-tests")
            addSwitch "-Dmaven.test.skip=true"
            ;;

        "tests") ;;

        "quiet")
            addSwitch "-q -Dsurefire.printSummary=false"
            ;;

        "show")
            SHOW="true"
            ;;

        *)
            echo " "
            echo "Build modifier '$MODIFIER' is not recognized"
            usage $SCRIPT
            ;;

        esac
        shift

    done

    addSwitch "--threads $THREADS"

    BUILD_FOLDER="$PROJECT"

    FILTER_OUT="grep -y -v --line-buffered"

    if [ -e "$BUILD_FOLDER" ]; then

        echo " "
        echo "┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫ Building '$PROJECT_NAME' ┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓"
        echo "┋"
        echo "┋         Build-Folder: $BUILD_FOLDER"
        echo "┋           Build-Type: $BUILD_TYPE"
        echo "┋      Build-Modifiers: $BUILD_MODIFIERS_STRING"
        echo "┋   Maven Command Line: mvn $SWITCHES $BUILD_ARGUMENTS"
        echo "┋"

        if [ -z "$SHOW" ]; then

            $PRE_BUILD_SCRIPT

            cd $BUILD_FOLDER
            $M2_HOME/bin/mvn $SWITCHES $BUILD_ARGUMENTS 2>&1 | $FILTER_OUT "illegal reflective access\|denied in a future release\|please consider reporting"

            if [ ${PIPESTATUS[0]} -ne "0" ]; then

                echo "Unable to build $PROJECT_NAME."
                exit 1

            else

                $POST_BUILD_SCRIPT

            fi

        fi

        echo "┋"
        echo "┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫ Built '$PROJECT_NAME' ┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛"
        echo " "

    else

        echo "$PROJECT not found"
        exit 1

    fi
}
