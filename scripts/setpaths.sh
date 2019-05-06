#!/bin/bash

# This assumes this script is running from the directory it is contained in: the root
# of the DSE project.
#
DSE_DIR=`pwd`

# these are sub-projects of DSE, so they are easy to locate. You should not have to modify these.
DANALYZER_DIR=${DSE_DIR}/danalyzer
DANHELPER_DIR=${DSE_DIR}/danhelper
DANPATCH_DIR=${DSE_DIR}/danpatch/build

#-----------------------------------------------------------------------------
# The following are the locations where you keep the repos for the engagement
# and Canonical sources. These are only required if you attempt to load from them
# by running setup.sh script.
#
# These are all assumed to be located in the same directory as the DSE repo.
# If they are not, you must specify where they are.
#-----------------------------------------------------------------------------

# this is the location of the engagement repo containing the challenge problems
ENGAGE_REPO=${DSE_DIR}/../engagement

# this is the location of the canonical repo (contains the executable & source code)
CANON_REPO=${DSE_DIR}/../public-el-information-mirror/Canonical_STAC_Examples/

#-----------------------------------------------------------------------------
# The following is where you keep the Test code that you wish to build and run
# with make.sh and run.sh.
#
# These are all assumed to be located in the same directory as the DSE repo.
# If they are not, you must specify where they are.
#-----------------------------------------------------------------------------

TESTPATH=${DSE_DIR}/../DSETests/

