#!/bin/bash

# copies the files over from the specified project in the engagement repo to the TESTPATH location.
#
# Inputs:
# TESTPATH - the base path to the tests

# this helps catch errors.
# 'nounset' throws an error if a parameter being used is undefined.
set -o nounset

source setpaths.sh

# this cleans up the path to remove all the '../' entries
function cleanup_path
{
  REPO=$1
  local NAME=$2
  if [ ! -d ${REPO} ]; then
    echo "$NAME not found at: ${REPO}"
    REPO=""
  else
    REPO=`realpath ${REPO}`
    REPO="${REPO}/"
  fi
}

function copy_project
{
    case "$1" in
    # engagement 1 problems
    Blogger)            ENGAGE="1";  PROBLEMDIR="blogger"                   ;;
    GabFeed1)           ENGAGE="1";  PROBLEMDIR="gabfeed_1"                 ;;
    GabFeed2)           ENGAGE="1";  PROBLEMDIR="gabfeed_2"                 ;;
    GabFeed3)           ENGAGE="1";  PROBLEMDIR="gabfeed_3"                 ;;
    GabFeed4)           ENGAGE="1";  PROBLEMDIR="gabfeed_4"                 ;;
    GabFeed5)           ENGAGE="1";  PROBLEMDIR="gabfeed_5"                 ;;
    GraphAnalyzer)      ENGAGE="1";  PROBLEMDIR="graph_analyzer"            ;;
    LawEnforcement)     ENGAGE="1";  PROBLEMDIR="law_enforcement_database"  ;;
    SnapBuddy1)         ENGAGE="1";  PROBLEMDIR="snapbuddy_1"               ;;
    SnapBuddy2)         ENGAGE="1";  PROBLEMDIR="snapbuddy_2"               ;;
    SnapBuddy3)         ENGAGE="1";  PROBLEMDIR="snapbuddy_3"               ;;
    SubSpace)           ENGAGE="1";  PROBLEMDIR="subspace"                  ;;

    # engagement 2 problems
    ImageProcessor)     ENGAGE="2";  PROBLEMDIR="image_processor"           ;;
    Textcrunchr1)       ENGAGE="2";  PROBLEMDIR="textcrunchr_1"             ;;
    Textcrunchr2)       ENGAGE="2";  PROBLEMDIR="textcrunchr_2"             ;;
    Textcrunchr3)       ENGAGE="2";  PROBLEMDIR="textcrunchr_3"             ;;
    Textcrunchr5)       ENGAGE="2";  PROBLEMDIR="textcrunchr_5"             ;;
    Textcrunchr6)       ENGAGE="2";  PROBLEMDIR="textcrunchr_6"             ;;
    Textcrunchr7)       ENGAGE="2";  PROBLEMDIR="textcrunchr_7"             ;;

    # engagement 4 problems
    Airplan1)           ENGAGE="4";  PROBLEMDIR="airplan_1"                 ;;
    Airplan2)           ENGAGE="4";  PROBLEMDIR="airplan_2"                 ;;
    Airplan3)           ENGAGE="4";  PROBLEMDIR="airplan_3"                 ;;
    Airplan4)           ENGAGE="4";  PROBLEMDIR="airplan_4"                 ;;
    Airplan5)           ENGAGE="4";  PROBLEMDIR="airplan_5"                 ;;
    Bidpal1)            ENGAGE="4";  PROBLEMDIR="bidpal_1"                  ;;
    Bidpal2)            ENGAGE="4";  PROBLEMDIR="bidpal_2"                  ;;
    Collab)             ENGAGE="4";  PROBLEMDIR="collab"                    ;;
    InfoTrader)         ENGAGE="4";  PROBLEMDIR="info_trader"               ;;
    LinearAlgebra)      ENGAGE="4";  PROBLEMDIR="linear_algebra_platform"   ;;
    MalwareAnalyzer)    ENGAGE="4";  PROBLEMDIR="malware_analyzer"          ;;
    Powerbroker1)       ENGAGE="4";  PROBLEMDIR="powerbroker_1"             ;;
    Powerbroker2)       ENGAGE="4";  PROBLEMDIR="powerbroker_2"             ;;
    Powerbroker3)       ENGAGE="4";  PROBLEMDIR="powerbroker_3"             ;;
    Powerbroker4)       ENGAGE="4";  PROBLEMDIR="powerbroker_4"             ;;
    RsaCommander)       ENGAGE="4";  PROBLEMDIR="rsa_commander"             ;;
    SmartMail)          ENGAGE="4";  PROBLEMDIR="smartmail"                 ;;
    TourPlanner)        ENGAGE="4";  PROBLEMDIR="tour_planner"              ;;
    Tweeter)            ENGAGE="4";  PROBLEMDIR="tweeter"                   ;;
    Withmi1)            ENGAGE="4";  PROBLEMDIR="withmi_1"                  ;;
    Withmi2)            ENGAGE="4";  PROBLEMDIR="withmi_2"                  ;;
    Withmi3)            ENGAGE="4";  PROBLEMDIR="withmi_3"                  ;;
    Withmi4)            ENGAGE="4";  PROBLEMDIR="withmi_4"                  ;;
    Withmi5)            ENGAGE="4";  PROBLEMDIR="withmi_5"                  ;;
    Withmi6)            ENGAGE="4";  PROBLEMDIR="withmi_6"                  ;;

    # engagement 5 problems
    AccountingWizard)   ENGAGE="5";  PROBLEMDIR="accounting_wizard"         ;;
    Battleboats1)       ENGAGE="5";  PROBLEMDIR="battleboats_1"             ;;
    Battleboats2)       ENGAGE="5";  PROBLEMDIR="battleboats_2"             ;;
    Braidit1)           ENGAGE="5";  PROBLEMDIR="braidit_1"                 ;;
    Braidit2)           ENGAGE="5";  PROBLEMDIR="braidit_2"                 ;;
    Ibasys)             ENGAGE="5";  PROBLEMDIR="ibasys"                    ;;
    Medpedia)           ENGAGE="5";  PROBLEMDIR="medpedia"                  ;;
    Poker)              ENGAGE="5";  PROBLEMDIR="poker"                     ;;
    SearchableBlog)     ENGAGE="5";  PROBLEMDIR="searchable_blog"           ;;
    Simplevote1)        ENGAGE="5";  PROBLEMDIR="simplevote_1"              ;;
    Simplevote2)        ENGAGE="5";  PROBLEMDIR="simplevote_2"              ;;
    Stacsql)            ENGAGE="5";  PROBLEMDIR="stacsql"                   ;;
    Stegosaurus)        ENGAGE="5";  PROBLEMDIR="stegosaurus"               ;;
    Stufftracker)       ENGAGE="5";  PROBLEMDIR="stufftracker"              ;;
    TawaFs)             ENGAGE="5";  PROBLEMDIR="tawa_fs"                   ;;

    # engagement 6 problems
    Battleboats3)       ENGAGE="6";  PROBLEMDIR="battleboats_3"             ;;
    Braidit3)           ENGAGE="6";  PROBLEMDIR="braidit_3"                 ;;
    Braidit4)           ENGAGE="6";  PROBLEMDIR="braidit_4"                 ;;
    Calculator1)        ENGAGE="6";  PROBLEMDIR="calculator_1"              ;;
    Calculator2)        ENGAGE="6";  PROBLEMDIR="calculator_2"              ;;
    Casedb)             ENGAGE="6";  PROBLEMDIR="casedb"                    ;;
    Chessmaster)        ENGAGE="6";  PROBLEMDIR="chessmaster"               ;;
    ClassScheduler)     ENGAGE="6";  PROBLEMDIR="class_scheduler"           ;;
    EffectsHero)        ENGAGE="6";  PROBLEMDIR="effectshero"               ;;
    Railyard)           ENGAGE="6";  PROBLEMDIR="railyard"                  ;;
    Simplevote3)        ENGAGE="6";  PROBLEMDIR="simplevote_3"              ;;
    Simplevote4)        ENGAGE="6";  PROBLEMDIR="simplevote_4"              ;;
    StacCoin)           ENGAGE="6";  PROBLEMDIR="stac_coin"                 ;;
    Swappigans)         ENGAGE="6";  PROBLEMDIR="swappigans"                ;;
    Tollbooth)          ENGAGE="6";  PROBLEMDIR="tollbooth"                 ;;

    *)
        echo "ERROR: Project '$1' not valid"
        exit 1
        ;;
    esac

    PROBSUBDIR=""
    if [[ ${ENGAGE} -lt 4 ]]; then
        PROBSUBDIR="/challenge_problems"
    elif [[ ${ENGAGE} -lt 6 ]]; then
        PROBSUBDIR="/challenge_programs"
    fi
    FROMPATH="${ENGAGE_REPO}engagement_${ENGAGE}${PROBSUBDIR}/${PROBLEMDIR}"
    TOPATH="${TESTPATH}e${ENGAGE}/$1"

    # exit if source path not found; if destination path not found, ask user to create it.
    if [ ! -d ${FROMPATH} ]; then
        echo "ERROR: source path not found: ${FROMPATH}"
        exit 1
    fi
    if [ ! -d ${TESTPATH} ]; then
        echo "Test destination path not found: ${TESTPATH}"
        read -n 1 -p "Do you wish to create it? (Y/n)" ANSWER
        echo
        if [[ ${ANSWER} == "N" || ${ANSWER} == "n" ]]; then
            exit 0
        fi
        mkdir -p ${TESTPATH}
    fi

    # create the destination directory
    if [ ! -d ${TOPATH} ]; then
        mkdir -p ${TOPATH}
        if [ ! -d ${TOPATH} ]; then
            echo "ERROR: unable to create path: ${TOPATH}"
            exit 0
        fi
    fi

    # copy the files over
    echo "- copying engagement ${ENGAGE} project: $1"
    rm -f -r ${TOPATH}/challenge_program
    rm -f -r ${TOPATH}/examples
    rm -f -r ${TOPATH}/questions
    cp -r ${FROMPATH}/challenge_program ${TOPATH}
    cp -r ${FROMPATH}/examples ${TOPATH}
    cp -r ${FROMPATH}/questions ${TOPATH}
    cp ${FROMPATH}/description.txt ${TOPATH}

    # this one places the jars in different location, so let's copy it to a better location
    if [[ $1 == "LawEnforcement" ]]; then
        CURDIR=`pwd`
        cd ${TOPATH}/challenge_program/
        cp server/dist/DistributedStore.jar .
        cp -f -r server/lib .
        cp -f -r server/files .
        cp -f -r server/dumps .
        cd ${CURDIR}
    fi
}

function setup_1
{
    cleanup_path ${ENGAGE_REPO} "ENGAGE_REPO"
    ENGAGE_REPO=${REPO}
    if [ ${ENGAGE_REPO} == "" ]; then
        echo "Unable to copy project from source: can't locate engagement repo"
        return
    fi

    copy_project Blogger
    copy_project GabFeed1
    copy_project GabFeed2
    copy_project GabFeed3
    copy_project GabFeed4
    copy_project GabFeed5
    copy_project GraphAnalyzer
    copy_project LawEnforcement
    copy_project SnapBuddy1
    copy_project SnapBuddy2
    copy_project SnapBuddy3
    copy_project SubSpace
}

function setup_2
{
    cleanup_path ${ENGAGE_REPO} "ENGAGE_REPO"
    ENGAGE_REPO=${REPO}
    if [ ${ENGAGE_REPO} == "" ]; then
        echo "Unable to copy project from source: can't locate engagement repo"
        return
    fi

    copy_project ImageProcessor
    copy_project Textcrunchr1
    copy_project Textcrunchr2
    copy_project Textcrunchr3
    copy_project Textcrunchr5
    copy_project Textcrunchr6
    copy_project Textcrunchr7
}

function setup_4
{
    cleanup_path ${ENGAGE_REPO} "ENGAGE_REPO"
    ENGAGE_REPO=${REPO}
    if [ ${ENGAGE_REPO} == "" ]; then
        echo "Unable to copy project from source: can't locate engagement repo"
        return
    fi

    copy_project Airplan1
    copy_project Airplan2
    copy_project Airplan3
    copy_project Airplan4
    copy_project Airplan5
    copy_project Bidpal1
    copy_project Bidpal2
    copy_project Collab
    copy_project InfoTrader
    copy_project LinearAlgebra
    copy_project MalwareAnalyzer
    copy_project Powerbroker1
    copy_project Powerbroker2
    copy_project Powerbroker3
    copy_project Powerbroker4
    copy_project RsaCommander
    copy_project SmartMail
    copy_project TourPlanner
    copy_project Tweeter
    copy_project Withmi1
    copy_project Withmi2
    copy_project Withmi3
    copy_project Withmi4
    copy_project Withmi5
    copy_project Withmi6
}

function setup_5
{
    cleanup_path ${ENGAGE_REPO} "ENGAGE_REPO"
    ENGAGE_REPO=${REPO}
    if [ ${ENGAGE_REPO} == "" ]; then
        echo "Unable to copy project from source: can't locate engagement repo"
        return
    fi

    copy_project AccountingWizard
    copy_project Battleboats1
    copy_project Battleboats2
    copy_project Braidit1
    copy_project Braidit2
    copy_project Ibasys
    copy_project Medpedia
    copy_project Poker
    copy_project SearchableBlog
    copy_project Simplevote1
    copy_project Simplevote2
    copy_project Stacsql
    copy_project Stegosaurus
    copy_project Stufftracker
    copy_project TawaFs
}

function setup_6
{
    cleanup_path ${ENGAGE_REPO} "ENGAGE_REPO"
    ENGAGE_REPO=${REPO}
    if [ ${ENGAGE_REPO} == "" ]; then
        echo "Unable to copy project from source: can't locate engagement repo"
        return
    fi

    copy_project Battleboats3
    copy_project Braidit3
    copy_project Braidit4
    copy_project Calculator1
    copy_project Calculator2
    copy_project Casedb
    copy_project Chessmaster
    copy_project ClassScheduler
    copy_project EffectsHero
    copy_project Railyard
    copy_project Simplevote3
    copy_project Simplevote4
    copy_project StacCoin
    copy_project Swappigans
    copy_project Tollbooth
}

function setup_canon
{
    cleanup_path ${CANON_REPO} "CANON_REPO"
    CANON_REPO=${REPO}
    if [ ${CANON_REPO} == "" ]; then
        echo "Unable to copy project from source: can't locate Canonical repo"
        exit 1
    fi
    
    if [ ! -d "${CANON_REPO}Source" ]; then
        echo "ERROR: Canonical source repo not found: ${CANON_REPO}Source"
        exit 1
    fi
    if [ ! -d "${TESTPATH}Canonical" ]; then
        mkdir -p "${TESTPATH}Canonical"
        if [ ! -d "${TESTPATH}Canonical" ]; then
            echo "ERROR: unable to create path: ${TESTPATH}Canonical"
            exit 0
        fi
    fi
    # if canonical source directory exists, ask user if he wants to re-fresh
    # (i.e. remove the old sources and copy new from repo)
    if [ -d "${TESTPATH}Canonical/Source" ]; then
        echo "Canonical source directory already exists? Do you wish to replace from repo? (Y/n)"
        read -s -n 1 SELECT
        if [[ ${SELECT} == "N" || ${SELECT} == "n" ]]; then
            return
        fi
        rm -r "${TESTPATH}Canonical/Source"
    fi

    mkdir -p "${TESTPATH}Canonical/Source/src"
    mkdir -p "${TESTPATH}Canonical/Source/src_E1_E4/e1e4"

    echo "- copying canonicals"
    cp ${CANON_REPO}Source/src/*.java ${TESTPATH}Canonical/Source/src
    cp ${CANON_REPO}Source/src_E1_E4/e1e4/*.java ${TESTPATH}Canonical/Source/src_E1_E4/e1e4
}

cleanup_path ${TESTPATH} "TESTPATH"
TESTPATH=${REPO}
if [ ${TESTPATH} == "" ]; then
    echo "Unable to copy project from source: can't locate destination folder"
    exit 1
fi

if [[ $# -eq 0 ]]; then
    echo "sets up the test path contents for running the make.sh and run.sh scripts"
    echo
    echo "Usage: setup.sh <project name>             - to copy a single project from engagement repo"
    echo "       setup.sh [ e1 | e2 | e4 | e5 | e6 ] - to copy all of the problems from an engagement"
    echo "       setup.sh canon                      - to copy the canonical sources from the repo"
    echo "       setup.sh all                        - to copy all of the problems from the repo"
    exit 0
fi
case "$1" in
    e1)     setup_1;;
    e2)     setup_2;;
    e4)     setup_4;;
    e5)     setup_5;;
    e6)     setup_6;;
    canon)  setup_canon;;
    all)    setup_1; setup_2; setup_4; setup_5; setup_canon;;
    *)      copy_project $1;;
esac
