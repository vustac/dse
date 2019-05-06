#!/bin/bash

# define the name of the application jar file and its main class for the specified project and
# the location of the test jar files.
#
# Inputs:
# PROJECT  - the name of the project to make or run
# TESTPATH - the base path to the tests
#
# sets the following on return:
# FOUND     - 1 if the project was found, 0 if not
# ENGAGE    - the engagement directory
# PROJJAR   - should define the jarfile of the application to danalyze.
# MAINCLASS - should define the main class for it.
# PROJDIR   - will define the path to the project jar file (or the lib file containing it)

# this helps catch errors.
# 'nounset' throws an error if a parameter being used is undefined.
set -o nounset

FOUND=1
ENGAGE=""
PROJJAR=${PROJECT}
MAINCLASS="${PROJECT}"
case "${PROJECT}" in
    # engagement 1 problems
    Blogger)            ENGAGE="1";  PROJJAR="nanohttpd-javawebserver-2.2.0-SNAPSHOT-jar-with-dependencies";
                                                              MAINCLASS="fi.iki.elonen.JavaWebServer" ;;
    GabFeed1)           ENGAGE="1";  PROJJAR="gabfeed_1"                 ; MAINCLASS="com.cyberpointllc.stac.host.Main" ;;
    GabFeed2)           ENGAGE="1";  PROJJAR="gabfeed_2"                 ; MAINCLASS="com.cyberpointllc.stac.host.Main" ;;
    GabFeed3)           ENGAGE="1";  PROJJAR="gabfeed_3"                 ; MAINCLASS="com.cyberpointllc.stac.host.Main" ;;
    GabFeed4)           ENGAGE="1";  PROJJAR="gabfeed_4"                 ; MAINCLASS="com.cyberpointllc.stac.host.Main" ;;
    GabFeed5)           ENGAGE="1";  PROJJAR="gabfeed_5"                 ; MAINCLASS="com.cyberpointllc.stac.host.Main" ;;
    GraphAnalyzer)      ENGAGE="1";  PROJJAR="GraphDisplay"              ; MAINCLASS="user.commands.CommandProcessor" ;;
    LawEnforcement)     ENGAGE="1";  PROJJAR="DistributedStore"          ; MAINCLASS="server.DistFSysServer" ;;
    SnapBuddy1)         ENGAGE="1";  PROJJAR="snapbuddy_1"               ; MAINCLASS="com.cyberpointllc.stac.host.Main" ;;
    SnapBuddy2)         ENGAGE="1";  PROJJAR="snapbuddy_2"               ; MAINCLASS="com.cyberpointllc.stac.host.Main" ;;
    SnapBuddy3)         ENGAGE="1";  PROJJAR="snapbuddy_3"               ; MAINCLASS="com.cyberpointllc.stac.host.Main" ;;
    SubSpace)           ENGAGE="1";  PROJJAR="Subspace-1.0"              ; MAINCLASS="com.example.subspace.Main" ;;

    # engagement 2 problems
    ImageProcessor)     ENGAGE="2";  PROJJAR="ipchallenge-0.1"           ; MAINCLASS="com.stac.Main" ;;
    Textcrunchr1)       ENGAGE="2";  PROJJAR="textcrunchr_1"             ; MAINCLASS="com.cyberpointllc.stac.host.Main" ;;
    Textcrunchr2)       ENGAGE="2";  PROJJAR="textcrunchr_2"             ; MAINCLASS="com.cyberpointllc.stac.host.Main" ;;
    Textcrunchr3)       ENGAGE="2";  PROJJAR="textcrunchr_3"             ; MAINCLASS="com.cyberpointllc.stac.host.Main" ;;
    Textcrunchr5)       ENGAGE="2";  PROJJAR="textcrunchr_5"             ; MAINCLASS="com.cyberpointllc.stac.host.Main" ;;
    Textcrunchr6)       ENGAGE="2";  PROJJAR="textcrunchr_6"             ; MAINCLASS="com.cyberpointllc.stac.host.Main" ;;
    Textcrunchr7)       ENGAGE="2";  PROJJAR="textcrunchr_7"             ; MAINCLASS="com.cyberpointllc.stac.host.Main" ;;

    # engagement 4 problems
    Airplan1)           ENGAGE="4";  PROJJAR="airplan_1"                 ; MAINCLASS="edu.cyberapex.home.StacMain" ;;
    Airplan2)           ENGAGE="4";  PROJJAR="airplan_2"                 ; MAINCLASS="com.networkapex.start.StacMain" ;;
    Airplan3)           ENGAGE="4";  PROJJAR="airplan_3"                 ; MAINCLASS="net.cybertip.home.StacMain" ;;
    Airplan4)           ENGAGE="4";  PROJJAR="airplan_4"                 ; MAINCLASS="net.techpoint.place.StacMain" ;;
    Airplan5)           ENGAGE="4";  PROJJAR="airplan_5"                 ; MAINCLASS="com.roboticcusp.place.StacMain" ;;
    Bidpal1)            ENGAGE="4";  PROJJAR="bidpal_1"                  ; MAINCLASS="edu.computerapex.origin.StacMain" ;;
    Bidpal2)            ENGAGE="4";  PROJJAR="bidpal_2"                  ; MAINCLASS="org.techpoint.origin.StacMain" ;;
    Collab)             ENGAGE="4";  PROJJAR="Collab"                    ; MAINCLASS="collab.RunCollab" ;;
    InfoTrader)         ENGAGE="4";  PROJJAR="InfoTrader"                ; MAINCLASS="infotrader.messaging.controller.module.RunInfoTrader" ;;
    LinearAlgebra)      ENGAGE="4";  PROJJAR="linalgservice"             ; MAINCLASS="com.example.linalg.Main" ;;
    MalwareAnalyzer)    ENGAGE="4";  PROJJAR="malware_analyzer"          ; MAINCLASS="com.ainfosec.MalwareAnalyzer.MalwareAnalyzer" ;;
    Powerbroker1)       ENGAGE="4";  PROJJAR="powerbroker_1"             ; MAINCLASS="edu.networkcusp.main.StacMain" ;;
    Powerbroker2)       ENGAGE="4";  PROJJAR="powerbroker_2"             ; MAINCLASS="org.digitalapex.main.StacMain" ;;
    Powerbroker3)       ENGAGE="4";  PROJJAR="powerbroker_3"             ; MAINCLASS="net.roboticapex.place.StacMain" ;;
    Powerbroker4)       ENGAGE="4";  PROJJAR="powerbroker_4"             ; MAINCLASS="com.virtualpoint.start.StacMain" ;;
    RsaCommander)       ENGAGE="4";  PROJJAR="challenge_program"         ; MAINCLASS="stac.Main" ;;
    SmartMail)          ENGAGE="4";  PROJJAR="SmartMail"                 ; MAINCLASS="smartmail.messaging.controller.module.RunSmartMail" ;;
    TourPlanner)        ENGAGE="4";  PROJJAR="challenge"                 ; MAINCLASS="com.graphhopper.http.GHServer" ;;
    Tweeter)            ENGAGE="4";  PROJJAR="Tweeter-1.0.0a"            ; MAINCLASS="com.tweeter.TwitterApplication" ;;
    Withmi1)            ENGAGE="4";  PROJJAR="withmi_1"                  ; MAINCLASS="edu.networkcusp.place.StacMain" ;;
    Withmi2)            ENGAGE="4";  PROJJAR="withmi_2"                  ; MAINCLASS="org.digitaltip.start.StacMain" ;;
    Withmi3)            ENGAGE="4";  PROJJAR="withmi_3"                  ; MAINCLASS="net.robotictip.main.StacMain" ;;
    Withmi4)            ENGAGE="4";  PROJJAR="withmi_4"                  ; MAINCLASS="com.digitalpoint.host.StacMain" ;;
    Withmi5)            ENGAGE="4";  PROJJAR="withmi_5"                  ; MAINCLASS="com.techtip.home.StacMain" ;;
    Withmi6)            ENGAGE="4";  PROJJAR="withmi_6"                  ; MAINCLASS="net.computerpoint.origin.StacMain" ;;

    # engagement 5 problems
    AccountingWizard)   ENGAGE="5";  PROJJAR="accounting_wizard-app"     ; MAINCLASS="com.bbn.accounting.wizard.AccountingWizard" ;;
    Battleboats1)       ENGAGE="5";  PROJJAR="battleboats_1"             ; MAINCLASS="com.cyberpointllc.stac.host.StacMain" ;;
    Battleboats2)       ENGAGE="5";  PROJJAR="battleboats_2"             ; MAINCLASS="com.cyberpointllc.stac.host.StacMain" ;;
    Braidit1)           ENGAGE="5";  PROJJAR="braidit_1"                 ; MAINCLASS="com.cyberpointllc.stac.host.StacMain" ;;
    Braidit2)           ENGAGE="5";  PROJJAR="braidit_2"                 ; MAINCLASS="com.cyberpointllc.stac.host.StacMain" ;;
    Ibasys)             ENGAGE="5";  PROJJAR="IBASys"                    ; MAINCLASS="com.ainfosec.ibasys.RunIBASys" ;;
    Medpedia)           ENGAGE="5";  PROJJAR="medpedia-1.0"              ; MAINCLASS="com.bbn.MedpediaApplication" ;;
    Poker)              ENGAGE="5";  PROJJAR="STACPoker"                 ; MAINCLASS="com.ainfosec.STACPoker.STACPoker" ;;
    SearchableBlog)     ENGAGE="5";  PROJJAR="SearchableBlog-1.0"        ; MAINCLASS="com.bbn.SearchableBlogApplication" ;;
    Simplevote1)        ENGAGE="5";  PROJJAR="simplevote_1"              ; MAINCLASS="com.cyberpointllc.stac.host.StacMain" ;;
    Simplevote2)        ENGAGE="5";  PROJJAR="simplevote_2"              ; MAINCLASS="com.cyberpointllc.stac.host.StacMain" ;;
    Stacsql)            ENGAGE="5";  PROJJAR="StacSQL-0.1"               ; MAINCLASS="com.stac.StacSQL" ;;
    Stegosaurus)        ENGAGE="5";  PROJJAR="Stegosaurus-1.0.0"         ; MAINCLASS="com.bbn.Stegosaurus" ;;
    Stufftracker)       ENGAGE="5";  PROJJAR="StuffTrackerService-app"   ; MAINCLASS="com.ainfosec.StuffTracker.StuffTrackerService" ;;
    TawaFs)             ENGAGE="5";  PROJJAR="Tawa-fs-app"               ; MAINCLASS="com.bbn.Tawafs" ;;

    # engagement 6 problems
    Battleboats3)       ENGAGE="6";  PROJJAR="battleboats_3"             ; MAINCLASS="com.cyberpointllc.stac.host.StacMain" ;;
    Braidit3)           ENGAGE="6";  PROJJAR="braidit_3"                 ; MAINCLASS="com.cyberpointllc.stac.host.StacMain" ;;
    Braidit4)           ENGAGE="6";  PROJJAR="braidit_4"                 ; MAINCLASS="com.cyberpointllc.stac.host.StacMain" ;;
    Calculator1)        ENGAGE="6";  PROJJAR="calculator_1"              ; MAINCLASS="com.cyberpointllc.stac.host.StacMain" ;;
    Calculator2)        ENGAGE="6";  PROJJAR="calculator_2"              ; MAINCLASS="com.cyberpointllc.stac.host.StacMain" ;;
    Casedb)             ENGAGE="6";  PROJJAR="CASEDBService-app"         ; MAINCLASS="com.ainfosec.casedbcamel.svr.MainCASEDBSvr" ;;
    Chessmaster)        ENGAGE="6";  PROJJAR="chessmaster"               ; MAINCLASS="com.ainfosec.chessmaster.Chessmaster" ;;
    ClassScheduler)     ENGAGE="6";  PROJJAR="class_scheduler"           ; MAINCLASS="com.bbn.classScheduler.scheduler.Application" ;;
    EffectsHero)        ENGAGE="6";  PROJJAR="effectshero"               ; MAINCLASS="com.bbn.hero.Application" ;;
    Railyard)           ENGAGE="6";  PROJJAR="railyard-modified"         ; MAINCLASS="com.ainfosec.Main" ;;
    Simplevote3)        ENGAGE="6";  PROJJAR="simplevote_3"              ; MAINCLASS="com.cyberpointllc.stac.host.StacMain" ;;
    Simplevote4)        ENGAGE="6";  PROJJAR="simplevote_4"              ; MAINCLASS="com.cyberpointllc.stac.host.StacMain" ;;
    StacCoin)           ENGAGE="6";  PROJJAR="staccoin-1.0"              ; MAINCLASS="com.ainfosec.StacCoin" ;;
    Swappigans)         ENGAGE="6";  PROJJAR="swappigans"                ; MAINCLASS="com.weathers.swappigans.Swappigans" ;;
    Tollbooth)          ENGAGE="6";  PROJJAR="TollBooth-app"             ; MAINCLASS="com.bbn.TollBooth" ;;

    # driver problems
    DrvBraidit1)        ENGAGE="0";  PROJJAR="Braidit1"                 ; MAINCLASS="plait.Plait" ;;
    DrvBraidit2)        ENGAGE="0";  PROJJAR="Braidit2"                 ; MAINCLASS="weave.Weave" ;;
    DrvSearchableBlog)  ENGAGE="0";  PROJJAR="SearchableBlog"           ; MAINCLASS="searchabledriver.SearchableDriver" ;;

    *)
        FOUND=0
        ;;
esac

# add the path seperator to the engagement dir, if defined
if [[ "${ENGAGE}" != "" ]]; then
    if [[ ${ENGAGE} -eq 0 ]]; then
        ENGAGE="drivers/"
    else
        ENGAGE="e${ENGAGE}/"
    fi
fi

# all jar files are placed in the lib subdir of the corresponding challenge problem directory,
# which is specified by the user selection. These *may* be seperated by a parent directory
# that specifies the engagement the problem is associated with.
if [[ ! -d ${TESTPATH} ]]; then
    echo "Invalid project test directory: ${TESTPATH}"
    exit 1
fi

PROJDIR=${TESTPATH}
if [[ -d ${TESTPATH}${ENGAGE} ]]; then
    PROJDIR=${TESTPATH}${ENGAGE}
fi
if [[ ! -d ${PROJDIR}${PROJECT} ]]; then
    echo "Invalid project selection (project dir ${PROJECT} not found in: ${PROJDIR})"
    exit 1
fi
PROJDIR+="${PROJECT}/"

# the jar files *may* be located in the challenge_program subdirectory of the main project folder.
if [[ -d "${PROJDIR}challenge_program" ]]; then
    PROJDIR+="challenge_program/"
fi


