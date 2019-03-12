#!/bin/bash

# this just makes it easier to change users if the repos are organized the same as below.
# The $HOME path should be set to the base dir where Projects is located (e.g. your home directory).
if [[ -z ${HOME} ]]; then
    # not set: if this command is being run from the "dse" directory (which is where the script
    # exists), let's assume the base dir is 3 dirs up from this.
    CURPATH=`pwd`
    echo ${CURPATH}
    if [[ ${CURPATH} == *"/Projects/isstac/dse" ]]; then
      HOME=${CURPATH%/Projects/isstac/dse*}
      echo ${HOME}
      echo "HOME set to: ${HOME}"
    else
      echo "HOME not defined. export HOME to your home directory."
      exit 1
    fi
fi

#-----------------------------------------------------------------------------
# The following are the locations where you keep the repos
#-----------------------------------------------------------------------------

# this is the location of the danalyzer repo
DANALYZER_REPO="${HOME}/Projects/isstac/dse/danalyzer/"

# this is the location of the danhelper agent repo
DANHELPER_REPO="${HOME}/Projects/isstac/dse/danhelper/"

# this is the location of the engagement repo containing the challenge problems
ENGAGE_REPO="${HOME}/Projects/isstac/engagement/"

# this is the location of the canonical repo (contains the executable & source code)
CANON_REPO="${HOME}/Projects/isstac/public-el-information-mirror/Canonical_STAC_Examples/"

#-----------------------------------------------------------------------------
# The following are locations where you wish to keep the Test code in which the
# files are build and instrumented.
#-----------------------------------------------------------------------------

# this is where to build and run the danalyzer-instrumented files.
# - setup.sh creates this dir and copies the required files over from the CANON_REPO location
#   to allow make.sh and run.sh to operate on them.
# - the jar files including the lib dir if supplied should be in a subdir that has the name of
#   one of the entries in the projinfo.sh file, or in the "challenge_problem" subdir of it.
#   You may also define the subdirs e2, e4, e5, etc. in the TESTPATH dir to seperate the problems
#   based on the engagement they came from (this ENGAGE param must be defined in projinfo.sh).
TESTPATH="${HOME}/Projects/isstac/DSETests/"

