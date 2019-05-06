#!/bin/bash

# define the default argument list for the specified project
# sets the following on return:
# FOUND     - 1 if the project was found, 0 if not
# ARGLIST   - should define the default argument list for the project.

# this helps catch errors.
# 'nounset' throws an error if a parameter being used is undefined.
set -o nounset

FOUND=1
case "${PROJECT}" in
    # engagement 1 problems
    # TODO: (if we want these)
    Blogger)            ARGLIST="" ;;
    GabFeed1)           ARGLIST="-p 8080 -d data/ -k ServersPrivateKey.txt -w ServersPasswordKey.txt" ;;
    GabFeed2)           ARGLIST="-p 8080 -d data/ -k ServersPrivateKey.txt -w ServersPasswordKey.txt" ;;
    GabFeed3)           ARGLIST="-p 8080 -d data/ -k ServersPrivateKey.txt -w ServersPasswordKey.txt" ;;
    GabFeed4)           ARGLIST="-p 8080 -d data/ -k ServersPrivateKey.txt -w ServersPasswordKey.txt" ;;
    GabFeed5)           ARGLIST="-p 8080 -d data/ -k ServersPrivateKey.txt -w ServersPasswordKey.txt" ;;
    GraphAnalyzer)      ARGLIST="dot ../examples/example.dot xy diagram png ../examples/outputFile" ;;
    LawEnforcement)     ARGLIST="" ;;
    SnapBuddy1)         ARGLIST="-p 8080 -d data/ -k ServersPrivateKey.txt -w ServersPasswordKey.txt" ;;
    SnapBuddy2)         ARGLIST="-p 8080 -d data/ -k ServersPrivateKey.txt -w ServersPasswordKey.txt" ;;
    SnapBuddy3)         ARGLIST="-p 8080 -d data/ -k ServersPrivateKey.txt -w ServersPasswordKey.txt" ;;
    SubSpace)           ARGLIST="data/app-config/subspace.properties" ;;

    # engagement 2 problems
    ImageProcessor)     ARGLIST="cluster ../examples/classification_images/tinyred.jpg" ;;
                        #ARGLIST="train /var/lib/trainer/images/blue/bluepic-0.jpg blue" ;;
    Textcrunchr1)       ARGLIST="../examples/test.zip" ;;
    Textcrunchr2)       ARGLIST="../examples/test.zip" ;;
    Textcrunchr3)       ARGLIST="../examples/test.zip" ;;
    Textcrunchr5)       ARGLIST="../examples/test.zip" ;;
    Textcrunchr6)       ARGLIST="../examples/test.zip" ;;
    Textcrunchr7)       ARGLIST="../examples/test.zip" ;;

    # engagement 4 problems
    Airplan1)           ARGLIST="-s 12345 -p 8443 -r -d data/ -w ServersPasswordKey.txt" ;;
    Airplan2)           ARGLIST="-s 12345 -p 8443 -r -d data/ -w ServersPasswordKey.txt" ;;
    Airplan3)           ARGLIST="-s 12345 -p 8443 -r -d data/ -w ServersPasswordKey.txt" ;;
    Airplan4)           ARGLIST="-s 12345 -p 8443 -r -d data/ -w ServersPasswordKey.txt" ;;
    Airplan5)           ARGLIST="-s 12345 -p 8443 -r -d data/ -w ServersPasswordKey.txt" ;;
    Bidpal1)            ARGLIST="8000 seller" ;;
    Bidpal2)            ARGLIST="8000 seller" ;;
    Collab)             ARGLIST="server" ;;
    LinearAlgebra)      ARGLIST="8080" ;;
    Powerbroker1)       ARGLIST="-i data/la/la.id" ;;
    Powerbroker2)       ARGLIST="-i data/la/la.id" ;;
    Powerbroker3)       ARGLIST="-i data/la/la.id" ;;
    Powerbroker4)       ARGLIST="-i data/la/la.id" ;;
    RsaCommander)       ARGLIST="-c -ckey privatekey_a.pem -b localhost -p 8080" ;;
    # SmartMail)           ARGLIST="" ;;
    TourPlanner)        OSM_FILE=data/massachusetts-latest.osm.pbf
                         GRAPH=${OSM_FILE%.*}-gh
                         MATRIX_FILE=data/matrix.csv
                         CONFIG=config.properties
                         JETTY_HOST=127.0.0.1
                         JETTY_PORT=8989
                         ARGLIST="jetty.host=$JETTY_HOST jetty.port=$JETTY_PORT config=$CONFIG graph.location=$GRAPH matrix.csv=$MATRIX_FILE" ;;
    Tweeter)            ARGLIST="dictionary.txt" ;;
    Withmi1)            ARGLIST="-d data -i ../examples/matilda.id -s storage" ;;
    Withmi2)            ARGLIST="-d data -i ../examples/matilda.id -s storage" ;;
    Withmi3)            ARGLIST="-d data -i ../examples/matilda.id -s storage" ;;
    Withmi4)            ARGLIST="-d data -i ../examples/matilda.id -s storage" ;;
    Withmi5)            ARGLIST="-d data -i ../examples/matilda.id -s storage" ;;
    Withmi6)            ARGLIST="-d data -i ../examples/matilda.id -s storage" ;;

    # engagement 5 problems
    AccountingWizard)   ARGLIST="" ;;
    Battleboats1)       ARGLIST="-i data/player1.id" ;;
    Battleboats2)       ARGLIST="-i data/player1.id" ;;
    Braidit1)           ARGLIST="-i data/player1.id" ;;
    Braidit2)           ARGLIST="-i data/player1.id" ;;
    Ibasys)             ARGLIST="" ;;
    Medpedia)           ARGLIST="" ;;
    Poker)              ARGLIST="server 9899 2 mySrvKeystore" ;; # or: client localhost 9899 mySrvKeystore
    SearchableBlog)     ARGLIST="";;
    Simplevote1)        ARGLIST="-d data/server0 -i data/server0/server.id -s data/server0/connectionlist.txt -w ../examples/ServersPasswordKey.txt" ;;
    Simplevote2)        ARGLIST="-d data/server1 -i data/server1/server.id -s data/server1/connectionlist.txt -w ../examples/ServersPasswordKey2.txt" ;;
    Stacsql)            ARGLIST="" ;;
    Stegosaurus)        ARGLIST="" ;;
    Stufftracker)       ARGLIST="" ;;
    TawaFs)             ARGLIST="" ;;

    # engagement 6 problems
    Battleboats3)       ARGLIST="-i data/player1.id" ;;
    Braidit3)           ARGLIST="-i data/player1.id" ;;
    Braidit4)           ARGLIST="-i data/player1.id" ;;
    Calculator1)        ARGLIST="" ;;
    Calculator2)        ARGLIST="" ;;
    Casedb)             ARGLIST="" ;;
    Chessmaster)        ARGLIST="" ;;
    ClassScheduler)     ARGLIST="" ;;
    EffectsHero)        ARGLIST="" ;;
    Railyard)           ARGLIST="--password PASS --keystore keystore.jks" ;; # modified source to allow specifying location of keystore file
    Simplevote3)        ARGLIST="-p 8443 -d data/server0/ -i data/server0/server.id -s data/server0/connectionlist.txt -w ../examples/ServersPasswordKey.txt" ;;
    Simplevote4)        ARGLIST="-p 8443 -d data/server0/ -i data/server0/server.id -s data/server0/connectionlist.txt -w ../examples/ServersPasswordKey.txt" ;;
    StacCoin)           ARGLIST="abcdefgh 4567 localhost:5678 123456" ;;
    Swappigans)         ARGLIST="users.csv items.csv" ;;
    Tollbooth)          ARGLIST="" ;;

    # driver problems
    DrvBraidit1)        ARGLIST="4 xyyX";;
    DrvBraidit2)        ARGLIST="4 xyyX";;
    DrvSearchableBlog)  ARGLIST="data/init.dat";;

    *)
        FOUND=0
        ;;
esac

