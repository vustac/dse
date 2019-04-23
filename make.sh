#!/bin/bash

# these options help catch errors.
# 'nounset' throws an error if a parameter being used is undefined.
# 'errexit' causes any error condition to terminate the script, so it doesn't continue running.
set -o nounset
#set -o errexit

# this cleans up the path to remove all the '../' entries
function cleanup_path
{
  REPO=$1
  local NAME=$2
  if [ ! -d ${REPO} ]; then
    echo "$NAME not found at: ${REPO}"
    REPO=""
  else
    REPO=`realpath ${REPO}/`
    REPO="${REPO}/"
  fi
}

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

        if [[ ${NEWREF} -eq 0 ]]; then
            SRCDIR="${TESTPATH}Canonical/Source/src_E1_E4/e1e4"
        else
            SRCDIR="${TESTPATH}Canonical/Source/src"
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
        SRCNAME="${PROJECT}${SUFFIX}"
        PROJDIR="${TESTPATH}Canonical/${PROJECT}/"
    
        # copy source file to destination location for building project (if not found)
        if [ ! -f ${PROJDIR}${SRCNAME}.java ]; then
            echo "- copying project source from ${SRCDIR} to ${PROJDIR}"
            if [ ! -d ${PROJDIR} ]; then
                mkdir -p ${PROJDIR}
            fi
            if [[ ${TESTMODE} -eq 0 ]]; then
                cp ${SRCDIR}/${SRCNAME}.java ${PROJDIR}
            else
                echo "cp ${SRCDIR}/${SRCNAME}.java ${PROJDIR}"
            fi
        fi
    else
        # get the PROJJAR and MAINCLASS
        source projinfo.sh
        if [[ ${FOUND} -eq 0 ]]; then
            PROJJAR=${PROJECT}
            PROJDIR="${TESTPATH}${PROJECT}/"
            MAINCLASS=${PROJECT}
            echo "Project not found. Using default MAINCLASS: ${MAINCLASS}"
        fi
    fi
}

# runs the training session with an entry from the selected images directory
#
# $1 = the color selection (RED or BLUE)
#
function imageproc_train
{
    IMAGETYPE=$1
    while read -r jpgfile; do
        select=(${jpgfile})
        select="${select##*/}"      # remove leading '/' from filename
        # only run jpg files
        if [[ "${select}" == *".jpg" ]]; then
            echo "- training file for ${IMAGETYPE}: ${select}"
            if [[ ${TESTMODE} -eq 0 ]]; then
              java -Xint -cp lib/ipchallenge-0.1.jar com.stac.Main train "${IMAGEDIR}/${IMAGETYPE}/${select}" ${IMAGETYPE}
            else
              echo "java -Xint -cp lib/ipchallenge-0.1.jar com.stac.Main train ${IMAGEDIR}/${IMAGETYPE}/${select} ${IMAGETYPE}"
            fi
        fi
    done < <(ls "${IMAGEDIR}/${IMAGETYPE}")
}

#------------------------- START FROM HERE ---------------------------
# Usage: make.sh [-t] [-v] [-n] <Project>
# Where: <Project> = the project to instrument (e.g. SimpleTest, Category7, Collab)
#        -t = don't build, just show commands
#        -v = (for Category tests only) indicates use the nonvulnerable version
#        -n = (for Category tests only) indicates use the rew reference version

# save current path
CURDIR=$(pwd 2>&1)

# verify some needed commands have been installed
check_command ant cmake z3
if [[ ${VALID} -ne 0 ]]; then
  exit 1
fi

# setup the paths to use
source setpaths.sh

# cleanup the paths for test files
cleanup_path ${TESTPATH} "TESTPATH"
TESTPATH=${REPO}

TESTMODE=0
NEWREF=0
NONVULNERABLE=0
COMPILE=0

# read options
COMMAND=()
while [[ $# -gt 0 ]]; do
    key="$1"
    case ${key} in
        -t|--test)
            TESTMODE=1
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

PROJJAR="${PROJECT}"
SRCNAME="${PROJECT}"

# need to determine the project values: PROJDIR, PROJJAR
get_project_info

# verify the project jar file exists in the specified dir or the lib subdir.
# if not, check the dist dir & if found copy it to the main project dir.
# if not found there, see if the source file is found and set flag to build it.
# otherwise, terminate with error
if [[ ${TESTMODE} -eq 0 ]]; then
    if [[ ! -f ${PROJDIR}${PROJJAR}.jar && ! -f ${PROJDIR}lib/${PROJJAR}.jar ]]; then
        if [[ -f ${PROJDIR}dist/${PROJJAR}.jar ]]; then
            echo "Project jar was found in dist dir - will copy to main project dir"
            cp ${PROJDIR}dist/${PROJJAR}.jar ${PROJDIR}${PROJJAR}.jar
        elif [[ -f ${PROJDIR}${SRCNAME}.java ]]; then
            echo "Project source was found - will re-build source from: ${PROJECT}${SRCNAME}.java"
            COMPILE=1
        else
            echo "Invalid project selection (project jar not found): ${PROJDIR}${PROJJAR}.jar"
            exit 1
        fi
    fi
fi

cd "${DANALYZER_DIR}"
if [[ ${TESTMODE} -ne 0 ]]; then
    echo
    echo "  (from: ${DANALYZER_DIR})"
fi

echo "- building danalyzer"
if [[ ${TESTMODE} -eq 0 ]]; then
    ant jar &> /dev/null
    if [[ $? -ne 0 ]]; then
        echo "ERROR: command failure"
        exit 1
    fi
else
    echo "ant jar"
fi

# run the rest from the project dir
cd ${PROJDIR}
if [[ ${TESTMODE} -ne 0 ]]; then
    echo
    echo "  (from: ${PROJDIR})"
fi

# remove any existing instrumented jar
rm -f ${PROJECT}-dan-ed.jar

# run build & instrumentation from project dir
if [[ ${COMPILE} -ne 0 && -f ${SRCNAME}.java ]]; then
    echo "- building ${PROJECT}"
    if [[ ${TESTMODE} -eq 0 ]]; then
        javac ${SRCNAME}.java
        jar cvf ${PROJECT}.jar *.class
    else
        echo "javac ${SRCNAME}.java"
        echo "jar cvf ${PROJECT}.jar *.class"
    fi
fi

# check whether the application jar file is in the main dir or in lib subdir
if [[ ! -f "${PROJJAR}.jar" && ${TESTMODE} -eq 0 ]]; then
    if [ ! -f "lib/${PROJJAR}.jar" ]; then
        echo "${PROJJAR}.jar not found!"
        exit 1
    fi
    PROJJAR="lib/${PROJJAR}"
fi

# strip out all debug info prior to instrumenting
echo "- stripping debug info from ${PROJECT}"
TMPJAR="temp"
if [[ ${TESTMODE} -eq 0 ]]; then
    pack200 -r -G ${TMPJAR}.jar ${PROJJAR}.jar
else
    echo "pack200 -r -G ${TMPJAR}.jar ${PROJJAR}.jar"
fi

# setup classpath and mainclass for running danalyzer
CLASSPATH=""
add_file_to_classpath "${DANALYZER_DIR}/dist/danalyzer.jar"
add_file_to_classpath "${DANALYZER_DIR}/lib/commons-io-2.5.jar"
add_file_to_classpath "${DANALYZER_DIR}/lib/asm-all-5.2.jar"
add_curdir_to_classpath
add_dir_to_classpath "lib"
add_dir_to_classpath "libs"

MAINCLASS="danalyzer.instrumenter.Instrumenter"

# instrument stripped jar file and rename output file to proper name
echo "- instrumenting ${PROJECT}"
if [[ ${TESTMODE} -eq 0 ]]; then
    java -cp ${CLASSPATH} ${MAINCLASS} ${TMPJAR}.jar
    mv ${TMPJAR}-dan-ed.jar ${PROJECT}-dan-ed.jar
    rm -f ${TMPJAR}.jar
else
    echo "java -cp ${CLASSPATH} ${MAINCLASS} ${TMPJAR}.jar"
    echo "mv ${TMPJAR}-dan-ed.jar ${PROJECT}-dan-ed.jar"
    echo "rm -f ${TMPJAR}.jar"
fi

if [[ ${PROJECT} == "ImageProcessor" ]]; then
    echo "ImageProcessor requires training before running. This creates a .imageClustering folder"
    echo " in your home directory that will contain a trainingSet.csv file that specifies the"
    echo " location of the training files to use in the 'cluster' command. If this is not setup"
    echo " correctly, running the instrumented ImageProcessor program will fail because the"
    echo " files could not be found."
    echo "Do you wish to run the setup for this program? (Y/n)"
    read -s -n 1 SELECT
    SELECT=$(echo "${SELECT}" | tr '[:lower:]' '[:upper:]' 2>&1)
    if [[ ${SELECT} == "Y" ]]; then
        echo "Full size (F) or Reduced size (S)? (F/S)"
        read -s -n 1 SELECT
        if [[ ${SELECT} == "F" || ${SELECT} == "f" ]]; then
            IMAGEDIR=images_orig
        else
            IMAGEDIR=images_small
        fi
        if [ ! -d "${IMAGEDIR}/blue" ]; then
            echo "Unable to find directories ${IMAGEDIR}/blue"
            exit 1
        fi
        if [ ! -d "${IMAGEDIR}/red" ]; then
            echo "Unable to find directories ${IMAGEDIR}/red"
            exit 1
        fi
        echo "Training from directories ${IMAGEDIR}"
        rm -rf ~/.imageClustering
        imageproc_train "blue"
        imageproc_train "red"
#        java -Xint -cp challenge_program/lib/ipchallenge-0.1.jar com.stac.Main train images/blue/bluepic-0.jpg blue
#        java -Xint -cp challenge_program/lib/ipchallenge-0.1.jar com.stac.Main train images/red/redpic-0.jpg red
    fi
fi

