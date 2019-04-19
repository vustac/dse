#!/bin/bash

# make sure JAVA_HOME is exported or danhelper will not be able to compile.
if [ -z ${JAVA_HOME} ]; then
    echo "JAVA_HOME is not currently defined"
    if [ -d /usr/lib/jvm/java-8-openjdk-amd64 ]; then
        export JAVA_HOME="/usr/lib/jvm/java-8-openjdk-amd64"
    else
        echo "The java folder /usr/lib/jvm/java-8-openjdk-amd64 was not found."
        echo "Please install java and export the JAVA_HOME definition."
        exit 1
    fi
fi

# these options help catch errors.
# 'nounset' throws an error if a parameter being used is undefined.
# 'errexit' causes any error condition to terminate the script, so it doesn't continue running.
set -o nounset
#set -o errexit

# verifies the specified command is present
#
# inputs: $1 = the list of commands to verify
#
function check_command
{
  VALID=""
  for cmd in "$@"; do
    LOCATION=$(command -v ${cmd} 2>&1)
    if [[ $? -ne 0 ]]; then
      echo "ERROR: command '${cmd}' not found. Please install it."
      VALID="1"
    fi
  done
}

# adds specified jar file to $CLASSPATH
#
# inputs: $1 = jar file (plus full path) to add
#
function add_file_to_classpath
{
    if [[ -z $1 ]]; then
        return
    fi

    if [[ -z ${CLASSPATH} ]]; then
        CLASSPATH="$1"
    else
        CLASSPATH="${CLASSPATH}:$1"
    fi
    # echo "  added: $1"
}

# adds the specified directory to the classpath. all files in this directory will be added.
#
# inputs: $1 = path of lib files to add
#
function add_dir_to_classpath
{
    if [[ -z $1 ]]; then
        return
    elif [[ ! -d "$1" ]]; then
        return
    else
        if [[ -z ${CLASSPATH} ]]; then
            CLASSPATH="$1/*"
        else
            CLASSPATH="${CLASSPATH}:$1/*"
        fi
    fi
}

# adds all of the jar files in the current path to $CLASSPATH except the $PROJJAR, since the
# danalyzed version is added in its place.
#
function add_curdir_to_classpath
{
    jarpath=`pwd`
    while read -r jarfile; do
        select=(${jarfile})
        select="${select##*/}"      # remove leading '/' from filename
        # skip anything that is not a jar file
        if [[ "${select}" != *".jar" ]]; then
            continue
        fi
        # ignore the project jar file, since we have already included the danalyzed version of it
        if [[ "${select}" != "${PROJJAR}.jar" &&
              "${select}" != "${PROJECT}-dan-ed.jar" ]]; then
            add_file_to_classpath ${jarfile}
        fi
    done < <(ls ${jarpath})
}

function arg_message
{
    if [[ ${COUNT} -lt ${REQUIRED} ]]; then
        echo "ERROR: Category ${NUMBER} requires ${REQUIRED} numeric argument: $1"
        exit 1
    fi
}

function help_canonical
{
    COUNT=$#
    case ${NUMBER} in
        1)  REQUIRED=1
            arg_message "(any value)"
            ;;
        2)  REQUIRED=1
            arg_message "(value < 100)"
            ;;
        3)  REQUIRED=2
            arg_message "(any value)"
            ;;
        4)  echo "Category ${NUMBER} runs as a server with a single thread"
            ;;
        5)  echo "Category ${NUMBER} runs as a server with multiple threads"
            ;;
        6)  echo "Category ${NUMBER} runs as a server with a single thread"
            ;;
        7)  REQUIRED=1
            arg_message "(value < 100)"
            ;;
        8)  REQUIRED=3
            arg_message "(arg1&2 < 10, arg3 < 100)"
            ;;
        9)  REQUIRED=1
            arg_message "(value >= 1 and <= 10000000)"
            ;;
        10) echo "Category ${NUMBER} runs as a server with multiple threads"
            ;;
        11) echo "Category ${NUMBER} runs as a server with a single thread"
            ;;
        12) echo "Category ${NUMBER} runs as a server with a single thread"
            ;;
        *)
            ;;
    esac
}

# gets additional naming suffix for certain tests that have multiple cases
#
# Inputs: $NUMBER = the Category number value (should be only for 11 and 12)
#         $SELECT = the additional selection (if any) after the number
# Returns:
#         $SUFFIX = the additional info to attach to CategoryX to specify the name
#
function check_special_canonicals
{
    # if user did not make A or B selection, ask him for it
    if [[ ${SELECT} != "A" && ${SELECT} != "B" ]]; then
        echo "Canonical ${NUMBER} requires either an A or B selection:"
        read -s -n 1 SELECT
        SELECT=$(echo "${SELECT}" | tr '[:lower:]' '[:upper:]' 2>&1)
        if [[ ${SELECT} != "A" && ${SELECT} != "B" ]]; then
            echo "Invalid selection, case 'A' is chosen by default"
            SELECT="A"
        fi
    fi

    # create the corresponding file name
    case ${NUMBER} in
        11) SUFFIX="_Case_${SELECT}${SUFFIX}"
            ;;
        12) if [[ "${SELECT}" == "A" ]]; then
                SUFFIX="${SUFFIX}_conditional"
            else
                SUFFIX="${SUFFIX}_exception"
            fi
            ;;
        *)
            echo "Invalid category selection: ${NUMBER}"
            exit 1
            ;;
    esac
}

function get_project_info
{
    if [[ ${PROJECT} == "Category"* ]]; then
        EXT=${PROJECT:8}
        if [[ "${EXT:1}" =~ ^[[:digit:]] ]]; then
            NUMBER=${EXT:0:2}
            SELECT=${EXT:2}
        else
            NUMBER=${EXT:0:1}
            SELECT=${EXT:1}
        fi

        if [[ ${NONVULNERABLE} -eq 0 ]]; then
            SUFFIX="_vulnerable"
        else
            SUFFIX="_not_vulnerable"
        fi

        # handle special canonical cases
        if [[ ${NUMBER} -gt 10 ]]; then
            check_special_canonicals
        fi
    
        # define the paths and files to use
        PROJJAR=${PROJECT}
        PROJDIR="${TESTPATH}Canonical/${PROJECT}/"
        MAINCLASS="Category${NUMBER}${SUFFIX}"
        if [[ ${NEWREF} -eq 0 ]]; then
            MAINCLASS="e1e4/${MAINCLASS}"
        fi

        # show input expectations for selection
        help_canonical ${ARGLIST}
        if [[ ${TESTMODE} -eq 0 ]]; then
            echo ""
        fi
    elif [[ ${PROJECT} == "SimpleTest" ]]; then
        # special programs
        PROJJAR=${PROJECT}
        PROJDIR=${TESTPATH}${PROJECT}
        MAINCLASS=${PROJECT}
    else
        # else, must be a challenge problem
        # get the PROJJAR and MAINCLASS
        source projinfo.sh
        if [[ ${FOUND} -eq 0 ]]; then
            PROJJAR=${PROJECT}
            PROJDIR=${TESTPATH}${PROJECT}
            MAINCLASS=${PROJECT}
            echo "Project not found. Using default MAINCLASS: ${MAINCLASS}"
        fi

        # add default arglist if none was given
        if [[ ${ARGLIST} == "" ]]; then
            source projargs.sh
        fi
    fi
}

#------------------------- START FROM HERE ---------------------------
# Usage: run.sh [-t] [-v] [-n] <Project> <Arglist>
# Where: <Project> = the instrumented project to run (e.g. SimpleTest, Category7, Collab)
#        <Arglist> = the arguments (if any) to pass to the program
#        -t = don't build, just show commands
#        -f = force rebuild of danhelper agent
#        -v = (for Category tests only) indicates use the nonvulnerable version
#        -n = (for Category tests only) indicates use the rew reference version

# save current path
CURDIR=$(pwd 2>&1)
PROJDIR=""

# verify some needed commands have been installed
check_command cmake z3
if [[ ${VALID} -ne 0 ]]; then
  exit 1
fi

# setup the paths to use
source setpaths.sh

FORCE=0
TESTMODE=0
NEWREF=0
NONVULNERABLE=0
MAINCLASS=""

# read options
COMMAND=()
while [[ $# -gt 0 ]]; do
    key="$1"
    case ${key} in
        -t|--test)
            TESTMODE=1
            shift
            ;;
        -f|--force)
            FORCE=1
            shift
            ;;
        -n|--new)
            NEWREF=1
            shift
            ;;
        -v|--nonvulnerable)
            NONVULNERABLE=1
            shift
            ;;
        *)
            COMMAND+=("$1")
            shift
            ;;
    esac
done

# the 1st remaining word is the project name and the remainder terms are the optional arguments
PROJECT="${COMMAND[@]:0:1}"
ARGLIST="${COMMAND[@]:1}"

# need to determine the project values: PROJDIR, PROJJAR, MAINCLASS
# (will also set ARGLIST to default if no args defined)
get_project_info

# make sure agent lib has been built
if [[ ${FORCE} -ne 0 ]]; then
    # to force a rebuild of danhelper, simply remove the executable that we use to detect it
    rm -f ${DANHELPER_REPO}src/libdanhelper.so
    rm -f ${DANHELPER_REPO}libdanhelper.so
elif [ -f "${DANHELPER_REPO}src/libdanhelper.so" ]; then
    mv ${DANHELPER_REPO}src/libdanhelper.so ${DANHELPER_REPO}libdanhelper.so
elif [ ! -f "${DANHELPER_REPO}libdanhelper.so" ]; then
    FORCE=1
fi

# (this is run from DANHELPER_REPO)
cd ${DANHELPER_REPO}
if [[ ${FORCE} -ne 0 ]]; then
    echo "- building danhelper agent"
    if [[ ${TESTMODE} -eq 0 ]]; then
        cmake .
        make
        if [ -f "${DANHELPER_REPO}src/libdanhelper.so" ]; then
            mv ${DANHELPER_REPO}src/libdanhelper.so ${DANHELPER_REPO}libdanhelper.so
        fi
        if [ ! -f "${DANHELPER_REPO}libdanhelper.so" ]; then
            echo "ERROR: danhelper build failure. No agent produced."
            exit 1
        fi
    else
        echo "cmake ."
        echo "make"
    fi
fi

# run from project dir
cd ${PROJDIR}
if [[ ${TESTMODE} -ne 0 ]]; then
    echo
    echo "  (from: ${PROJDIR})"
fi

# make sure classlist was copied to current dir
if [[ ${TESTMODE} -eq 0 && ! -f classlist.txt ]]; then
    echo "ERROR: classlist.txt not found in ${PROJDIR}."
    exit 1
fi

# setup classpath
CLASSPATH=""
add_file_to_classpath "${PROJECT}-dan-ed.jar"
add_file_to_classpath "${DANALYZER_REPO}lib/commons-io-2.5.jar"
add_file_to_classpath "${DANALYZER_REPO}lib/asm-all-5.2.jar"
add_curdir_to_classpath
add_dir_to_classpath "lib"
add_dir_to_classpath "libs"

# run the instrumented code with the agent
OPTIONS="-Xverify:none -Dsun.boot.library.path=$JAVA_HOME/bin:/usr/lib"
BOOTCLASSPATH="-Xbootclasspath/a:${DANALYZER_REPO}dist/danalyzer.jar:${DANALYZER_REPO}lib/com.microsoft.z3.jar:${DANALYZER_REPO}lib/guava-27.1-jre.jar"
AGENTPATH="-agentpath:${DANHELPER_REPO}libdanhelper.so"
MONGO_JARS=${DANALYZER_REPO}lib/mongodb-driver-core-3.8.2.jar:${DANALYZER_REPO}lib/mongodb-driver-sync-3.8.2.jar:${DANALYZER_REPO}lib/bson-3.8.2.jar

# append the Mongo files to the class paths
CLASSPATH="${CLASSPATH}:${MONGO_JARS}"
BOOTCLASSPATH=${BOOTCLASSPATH}:${MONGO_JARS}

if [[ ${TESTMODE} -eq 0 ]]; then
    java ${OPTIONS} ${BOOTCLASSPATH} ${AGENTPATH} -cp ${CLASSPATH} ${MAINCLASS} ${ARGLIST}
else
    echo "java ${OPTIONS} ${BOOTCLASSPATH} ${AGENTPATH} -cp ${CLASSPATH} ${MAINCLASS} ${ARGLIST}"
fi
