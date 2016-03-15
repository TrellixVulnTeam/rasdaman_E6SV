#!/bin/bash
#
# This file is part of rasdaman community.
#
# Rasdaman community is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# Rasdaman community is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with rasdaman community.  If not, see <http://www.gnu.org/licenses/>.
#
# Copyright 2003, 2004, 2005, 2006, 2007, 2008, 2009 Peter Baumann /
# rasdaman GmbH.
#
# For more information please see <http://www.rasdaman.org>
# or contact Peter Baumann via <baumann@rasdaman.com>.
#
# ------------------------------------------------------------------------------
#
# SYNOPSIS
#    common.sh
# Description
#    Common functionality used by test scripts, like
#   - shortcuts for commands
#   - logging functions
#   - various check functions
#   - test data import
#   - generic test case runner
#
################################################################################

SOURCE="${BASH_SOURCE[0]}"
while [ -h "$SOURCE" ] ; do SOURCE="$(readlink "$SOURCE")"; done
UTIL_SCRIPT_DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"

. "$UTIL_SCRIPT_DIR"/../conf/test.cfg


# ------------------------------------------------------------------------------
# script return codes
#
RC_OK=0
RC_ERROR=1
RC_SKIP=2


# ------------------------------------------------------------------------------
# testing statistics
#
NUM_TOTAL=0 # the number of manipulations
NUM_FAIL=0  # the number of fail manipulations
NUM_SUC=0   # the number of success manipulations

# testing data
TEST_GREY=test_grey
TEST_GREY2=test_grey2
TEST_RGB2=test_rgb2
TEST_GREY3D=test_grey3d
TEST_COMPLEX=test_complex
TEST_NULL=nulltest


# ------------------------------------------------------------------------------
# command shortcuts
#
export RASQL="rasql --server $RASMGR_HOST --port $RASMGR_PORT --user $RASMGR_ADMIN_USER \
              --passwd $RASMGR_ADMIN_PASSWD --database $RASDB"
export RASCONTROL="rascontrol --host $RASMGR_HOST --port $RASMGR_PORT"
export RASDL="rasdl -d $RASDB"
export RASIMPORT="rasimport"

export START_RAS=start_rasdaman.sh
export STOP_RAS=stop_rasdaman.sh
export CREATE_DB=create_db.sh

# check connection itself
export PGSQL="psql -d $RASDB --port $PG_PORT"

# check for petascope
export PSQL="psql -d $PS_DB --port $PG_PORT"

export WGET="wget"
export WGET_CODE_SERVER_ERROR=8 # See https://www.gnu.org/software/wget/manual/html_node/Exit-Status.html
export GDALINFO="gdalinfo -noct -checksum"

# filestorage
export DB_DIR="/tmp/rasdb"
export LOG_DIR="$RMANHOME/log"
export RASMGR_CONF="$RMANHOME/etc/rasmgr.conf"

# ------------------------------------------------------------------------------
# logging
#

LOG="$SCRIPT_DIR/log"
OLDLOG="$LOG.save"
FAILED="$SCRIPT_DIR/failed_cases"
FIXED=0

log()
{
  echo "$PROG: $*" | tee -a $LOG
}

loge()
{
  echo -e "$*" | tee -a $LOG
}

logn()
{
  echo -n "$PROG: $*" | tee -a $LOG
}

feedback()
{
  if [ $? -ne 0 ]; then
    loge failed.
  else
    loge ok.
  fi
}

check()
{
  if [ $? -ne 0 ]; then
    echo "$PROG: failed, exiting." | tee -a $LOG
    exit $RC_ERROR
  else
    loge ok.
  fi
}

error()
{
  echo "$PROG: $*" | tee -a $LOG
  echo "$PROG: exiting." | tee -a $LOG
  exit $RC_ERROR
}

# ------------------------------------------------------------------------------
# setup log
#

if [ -n "$SCRIPT_DIR" ] ; then
  if [ -f $LOG ] ; then
    echo Old logfile found, copying it to $OLDLOG
    rm -f $OLDLOG
    mv $LOG $OLDLOG
  fi

  rm -f "$FAILED"

  NOW=`date`
  log "starting test at $NOW"
  log ""
fi

#
# manage timing, in ms
#
start_timer()
{
  timer_start=$(date +%s%N)
}
stop_timer()
{
  timer_stop=$(date +%s%N)
}
get_time()
{
  echo "scale=3; ($timer_stop - $timer_start) / 1000000.0" | bc
}

# ------------------------------------------------------------------------------
# dependency checks
#
check_rasdaman()
{
  which rasmgr > /dev/null
  if [ $? -ne 0 ]; then
    error "rasdaman not installed, please add rasdaman bin directory to the PATH."
  fi
  pgrep rasmgr > /dev/null
  if [ $? -ne 0 ]; then
    error "rasdaman not started, please start with start_rasdaman.sh"
  fi
  $RASCONTROL -x 'list srv -all' > /dev/null
  if [ $? -ne 0 ]; then
    error "no rasdaman servers started."
  fi
}

check_postgres()
{
  which psql > /dev/null
  if [ $? -ne 0 ]; then
    error "PostgreSQL missing, please add psql to the PATH."
  fi
  pgrep postgres > /dev/null
  if [ $? -ne 0 ]; then
    pgrep postmaster > /dev/null || error "The PostgreSQL service is not started."
  fi
}

check_wget()
{
  which wget > /dev/null
  if [ $? -ne 0 ]; then
    error "wget missing, please install."
  fi
}

check_secore()
{
  $WGET -q "${SECORE_URL}/crs" -O /dev/null
  if [ $? -ne 0 ]; then
    log "failed connecting to SECORE at $SECORE_URL, please deploy it first."
    return 1
  fi
  return 0
}

check_netcdf()
{
  which ncdump > /dev/null
  if [ $? -ne 0 ]; then
    error "netcdf tools missing, please add ncdump to the PATH."
  fi
}

check_gdal()
{
  which gdal_translate > /dev/null
  if [ $? -ne 0 ]; then
    error "gdal missing, please add gdal_translate to the PATH."
  fi
}

# Check if GDAL version is greater-equal than specified (M.m)
# usage: $0 <major> <minor>
# return
#   0 - if GDAL version is >= <major>.<minor>
#   1 - otherwise
check_gdal_version()
{
  GDAL_VERSION="$( $GDALINFO --version | awk -F ' |,' '{ print $2 }' | grep -o -e '[0-9]\+.[0-9]\+' )" # M.m version
  GDAL_VERSION_MAJOR=$(echo $GDAL_VERSION | awk -F '.' '{ print $1; }')
  GDAL_VERSION_MINOR=$(echo $GDAL_VERSION | awk -F '.' '{ print $2; }')
  [[ $GDAL_VERSION_MAJOR -gt 1 || ( $GDAL_VERSION_MAJOR -eq $1 && $GDAL_VERSION_MINOR -ge $2 ) ]] && echo 0 || echo 1
}

check_filestorage_dependencies()
{
  [ -f "$RMANHOME/bin/rasdl" ] || error "rasdl not found, RMANHOME not defined properly?"
  [ -f "$RASMGR_CONF" ] || error "$RASMGR_CONF not found, RMANHOME not defined properly?"
  [ $(which sqlite3) ] || error "sqlite3 not found, please install."
}


# ------------------------------------------------------------------------------
# print test summary
#
print_summary()
{
  if [ $NUM_TOTAL -eq 0 ]; then
    NUM_TOTAL=$(($NUM_FAIL + $NUM_SUC))
  fi
  NOW=`date`

  echo
  log "-------------------------------------------------------"
  log "Test summary"
  log ""
  log "  Test finished at: $NOW"
  log "  Total tests run : $NUM_TOTAL"
  log "  Successful tests: $NUM_SUC"
  log "  Failed tests    : $NUM_FAIL"
  log "  Skipped tests   : $(($NUM_TOTAL - ($NUM_FAIL + $NUM_SUC)))"
  log "  Detail test log : $LOG"
  log "-------------------------------------------------------"

  # compute return code
  if [ $NUM_FAIL -eq 0 ]; then
    RC=$RC_OK
  else
    RC=$RC_ERROR
  fi
}

#
# check if result matches expected result, automatically updating the number of
# failed/successfull tests.
# arg 1: expected result
# arg 2: actual result
# arg 3: message to print
#
check_result()
{
  exp="$1"
  res="$2"
  msg="$3"

  logn "$msg... "
  if [ "$exp" != "$res" ]; then
    NUM_FAIL=$(($NUM_FAIL + 1))
    echo failed.
    log "expected: $exp, got $res"
  else
    NUM_SUC=$(($NUM_SUC + 1))
    echo ok.
  fi
}

check()
{
  if [ $? -ne 0 ]; then
    loge failed.
    NUM_FAIL=$(($NUM_FAIL + 1))
  else
    loge ok.
    NUM_SUC=$(($NUM_SUC + 1))
  fi
  NUM_TOTAL=$(($NUM_TOTAL + 1))
}

#
# Check return code ($?) and update variables tracking number of
# failed/successfull tests.
# In case of the open tests TEST SKIPPED is printed instead of TEST FAILED.
#
update_result()
{
  local rc=$?

  grep "$f" "$KNOWN_FAILS" > /dev/null 2>&1
  local known_fail=$?

  if [ $rc != 0 ]; then
    echo "$SCRIPT_DIR" | grep "testcases_open" > /dev/null
    rc_open=$?
    echo "$f" | egrep "\.fixed$" > /dev/null
    rc_fixed=$?

    if [ $rc_open -ne 0 -o $rc_fixed -eq 0 ]; then
      if [ $known_fail -ne 0 ]; then
        NUM_FAIL=$(($NUM_FAIL + 1))
        log " ->  TEST FAILED"
      else
        log " -> TEST SKIPPED"
      fi
    else
      log " ->  TEST SKIPPED"
    fi
    if [ -n "$FAILED" ]; then
      echo "----------------------------------------------------------------------" >> $FAILED
      if [ -n "$f" ]; then
        echo $f >> $FAILED
      fi
      cat "$f" >> $FAILED
      echo "" >> $FAILED
    fi
  else
    NUM_SUC=$(($NUM_SUC + 1))
    log " ->  TEST PASSED"
    if [ $known_fail -eq 0 ]; then
      log " ->"
      log " -> Case known to fail has been fixed!"
      log " -> Please remove $f from $KNOWN_FAILS"
    fi
  fi
  log "--------------------------------------------------------------------------------------------"
  NUM_TOTAL=$(($NUM_TOTAL + 1))
}

# ------------------------------------------------------------------------------
#
# run rasql query test
# expects several global variables to be set:
# - $f      - input file
# - $out    - output file
# - $oracle - oracle file with expected result
# - $custom_script - bash script to be executed for special comparison of result
#                    to oracle.
#
run_rasql_test()
{
  rm -f "$out"
  local QUERY=`cat $f`
  $RASQL -q "$QUERY" --out file --outfile "$out"

  # move to proper output file
  for tmpf in `ls "$out".*  2> /dev/null`; do
    mv "$tmpf" "$out"
    break
  done

  # if the result is a scalar, there will be no tmp file by rasql,
  # here we output the Result element scalar into tmp.unknown
  if [ ! -f "$out" ]; then
    $RASQL -q "$QUERY" --out string | grep Result > $out
  fi

  cmp "$oracle" "$out"
  update_result
}


# ------------------------------------------------------------------------------
#
# Remove URLs and some prefixes which might break a tests during oracle comparison
#
prepare_xml_file()
{
  xml_file="${1}"
  echo "Preparing XML file $xml_file for oracle comparison... "
  if [ -n "${1}" ]; then
      sed -i 's/gml://g' "$xml_file"
      sed -i $'s/\r//g' "$xml_file"
      sed -i '/xlink:href/d' "$xml_file"
      sed -i '/identifier /d' "$xml_file"
      sed -i 's|xmlns:[^=]*="[^"]*"||g' "$xml_file"
      sed -i 's|xsi:schemaLocation="[^"]*"||g' "$xml_file"
      sed -i 's#http:\/\/\(\w\|[.-]\)\+\(:[0-9]\+\)\?\/def##g' "$xml_file" # not only test.cfg SECORE_URL, but also what's in ps_crs!
      sed -i 's|at=[^ ]*||g' "$xml_file"                                   # e.g. See ``/crs/OGC/0'' Vs ``/crs?authority=OGC&version=0''.
  fi
  echo "ok."
}


# -----------------------------------------------------------------------------

# when wget returns empty file, using curl to get the error in page content.
# $1 is request error link, $2 is output file.
wget_error(){
  # get script name
  PROG=$( basename $0 )
  curl -s -R $1 > $2
}


# ------------------------------------------------------------------------------
#
# run a test suite, expected global vars:
#  $SVC_NAME - service name, e.g. wcps, wcs, etc.
#  $f        - test file
#
run_test()
{
  if [ ! -f "$f" ]; then
    error "test case not found: $f"
  fi

  # check if rasdaman is running and exit if not
  $RASQL -q 'select c from RAS_COLLECTIONNAMES as c' --out string > /dev/null 2>&1
  if [ $? -ne 0 ]; then
    log "rasdaman down, exiting..."
    cleanup
  fi

  # if testcase marked as fixed temporarily we remove the .fixed extension
  oldf="$f"
  if [ -n "$FIXED" -a $FIXED -eq 1 ]; then
    f="$fixedf"
    FIXED=0
  fi

  # get test type - file extension
  test_type=`echo "$f" | sed 's/.*\.//'`

  # various other files expected  by the run_*_test functions
  # NOTE: remove input protocol extension: all queries with the same basename shall refer to the same oracle.
  oracle="$ORACLE_PATH/${f%\.*}.oracle"
  out="$OUTPUT_PATH/$f.out"
  err="$OUTPUT_PATH/$f.err"
  rm -f "$out"
  pre_script="$QUERIES_PATH/${f%\.*}.pre.sh"
  post_script="$QUERIES_PATH/${f%\.*}.post.sh"
  check_script="$QUERIES_PATH/${f%\.*}.check.sh"

  # restore original filename
  f="$oldf"

  # temporary files
  oracle_tmp="$OUTPUT_PATH/temporary_oracle"
  output_tmp="$OUTPUT_PATH/temporary_out"

  #
  # run pre script if present
  #
  if [ -f "$pre_script" ]; then
    log "running pre-test script..."
    $pre_script
    if [ $? -ne 0 ]; then
      log "warning: pre script failed execution - $pre_script"
    fi
  fi

  if [[ "$f" == *.sh ]]; then

    #
    # 0. run custom test script if present
    #
    $f "$out" "$oracle"
    update_result

  else

    QUERY=`cat $f | tr -d '\n'`

    #
    # 1. execute test query
    #
    case "$SVC_NAME" in
      wcps)   # URL encode query
              QUERY=`cat $f | xxd -plain | tr -d '\n' | sed 's/\(..\)/%\1/g'`
              # send to petascope with WCPSVersion=1.5
              $WGET -q "$WCPS_EMBEDDED_URL_1_5$QUERY" -O "$out"
              WGET_EXIT_CODE=$?
              if [[ $WGET_EXIT_CODE != 0 ]]; then
                  echo "Error when processing WCPS request in KVP, query return error: "$WGET_EXIT_CODE
                  wget_error "$WCPS_EMBEDDED_URL_1_5$QUERY" "$out"
                  echo ".Done"
              fi
              ;;
      wcs)    case "$test_type" in
                kvp) $WGET -q "$WCS_URL?$QUERY" -O "$out"
                     WGET_EXIT_CODE=$?
		                 # wget cannot get the error page, then need curl to download error and save to output file.
		                 # Note: not get error page from request in XML as it only returns general message.
                     if [[ $WGET_EXIT_CODE != 0 ]]; then
                        echo "Error when processing WCS request in KVP, query return error: "$WGET_EXIT_CODE
                        wget_error "$WCS_URL?$QUERY" "$out"
                        echo ".Done"
                     fi
                     ;;
                xml) postdata=`mktemp`
                     cat "$f" > "$postdata"
                     $WGET -q --header "Content-Type: text/xml" --post-file "$postdata" "$WCS_URL" -O "$out"
                     WGET_EXIT_CODE=$?
                     rm "$postdata"
                     ;;
                *)   error "unknown wcs test type: $test_type"
              esac
              ;;
      wms)    $WGET -q "$WMS_URL?$QUERY" -O "$out"
              WGET_EXIT_CODE=$?
              ;;
      secore) QUERY=`echo "$QUERY" | sed 's|%SECORE_URL%|'$SECORE_URL'|g' | tr -d '\t' | tr -d ' '`
              $WGET -q "$SECORE_URL$QUERY" -O "$out"
              WGET_EXIT_CODE=$?
              if [[ $WGET_EXIT_CODE != 0 ]]; then
                 echo "Error when processing CRS request in SECORE, query return error: "$WGET_EXIT_CODE
                 wget_error "$SECORE_URL$QUERY" "$out"
                 echo ".Done"
              fi
              ;;
      select|rasql|nullvalues|jit)
              QUERY=`cat $f`
              if [ "$SVC_NAME" = "jit" ]; then
                QUERY="$QUERY [opt 4]"
              fi
              $RASQL -q "$QUERY" --out file --outfile "$out" --quiet > /dev/null 2> "$err"

              # if an exception was thrown, then the err file has non-zero size
              if [ -s "$err" ]; then
                mv "$err" "$out"

              else
                # move to proper output file
                for tmpf in `ls "$out".*  2> /dev/null`; do
                    [ -f "$tmpf" ] || continue
                    mv "$tmpf" "$out"
                    break
                done

                # if the result is a scalar, there will be no tmp file by rasql,
                # here we output the Result element scalar into tmp.unknown
                if [ ! -f "$out" ]; then
                    $RASQL -q "$QUERY" --out string | grep Result > $out
                fi
              fi
              ;;
      *)      error "unknown service: $SVC_NAME"
    esac

    #
    # 2a. create $oracle from $ouput, if missing
    #
    outfiletype=`file "$out" | awk -F ':' '{print $2;}'`
    if [ ! -f "$oracle" ]; then
        log " -> NO ORACLE FOUND"
        log " -> copying $out to $oracle"
        if [[ "$outfiletype" == *XML* ]]; then
            prepare_xml_file "$out"
        fi
        cp "$out" "$oracle"
    fi

    #
    # 2b. check result
    #
    if [ -n "$check_script" -a -f "$check_script" ]; then
      log "custom script"
      "$check_script" "$out" "$oracle"
      update_result

    else

      grep "$oracle" "Stack trace" > /dev/null 2>&1
      if [ $? -eq 0 ]; then
        # do exception comparison
        # NOTE: this part of code is entered only if the server returns a success code 2xx
        #       but with an exception body, since `wget` does _not_ fetch the content of an error response.
        #       This however is not a recommended server behaviour.
        log "exception comparison"
        local lineNo=$(grep -n "Stack trace" "$oracle")
        lineNo=$(sed 's/[^0-9]*//g' <<< $lineNo)
        head -"$lineNo" "$out" > "$output_tmp"
        head -"$lineNo" "$oracle" > "$oracle_tmp"
        cmp "$output_tmp" "$oracle_tmp" 2>&1
        update_result

      # Note: when request in XML, the error is throw in general as
      # e.g >Couldn't understand the recieved request, is the service attribute missing?
      # then with submit in XML, only check HTTP error code
      elif [[ "$out" == *.error.xml* ]]; then

        # This test is supposed to raise an exception: check wget exit code instead of the response.
        log "http exit code comparison"
        test "$WGET_CODE_SERVER_ERROR" = "$WGET_EXIT_CODE"
        update_result

      # Note: when request in KVP, the error is throw in specific message
      # then with submit in KVP, compare the erro page
      elif [ -f "$oracle"  ] || [ "$oracle" == *.error.* ]; then

        filetype=`file "$oracle" | awk -F ':' '{print $2;}'`
        echo "$filetype" | egrep -i "(xml|ascii|text)" > /dev/null
        rc=$?
        gdalinfo "$out" > /dev/null 2>&1
        if [ $? -eq 0 -a $rc -ne 0 ]; then
          # do image comparison
          log "image comparison"
          gdal_translate -of GTiff -co "TILED=YES" "$out" "$output_tmp" > /dev/null
          gdal_translate -of GTiff -co "TILED=YES" "$oracle" "$oracle_tmp" > /dev/null
          cmp "$output_tmp" "$oracle_tmp" 2>&1
        else
          # byte comparison
          if [[ "$filetype" == *XML* ]]; then
              prepare_xml_file "$out"
          fi
          log "byte comparison"
          # diff comparison ignoring EOLs [see ticket #551]
          diff -b "$oracle" "$out" 2>&1 > /dev/null
        fi
        update_result

      else # keep this to re-copy oracle in case it was accidentally deleted since Sec.2a (rare)
        log " -> NO ORACLE FOUND"
        log " -> copying $out to $oracle"
        if [[ "$outfiletype" == *XML* ]]; then
            prepare_xml_file "$out"
        fi
        cp "$out" "$oracle"
        NUM_FAIL=$(($NUM_FAIL + 1))
      fi
    fi

    #rm -f "$oracle_tmp" "$output_tmp"
  fi

  #
  # run post script if present
  #
  if [ -f "$post_script" ]; then
    log "running post-test script..."
    $post_script
    if [ $? -ne 0 ]; then
      log "warning: post script failed execution - $post_script"
    fi
  fi
}

# ------------------------------------------------------------------------------
# rasdaman administration
#

restart_rasdaman()
{
  logn "restarting rasdaman... "
  if [ $(pgrep rasmgr) ]; then
    $STOP_RAS > /dev/null 2>&1
    sleep 0.2 || sleep 1
  fi
  $START_RAS > /dev/null 2>&1
  sleep 0.2 || sleep 1
  loge ok.
}

# ------------------------------------------------------------------------------
#
# server/config management for using local SQLite / file storage database
#
get_backup_rasmgr_conf()
{
  BACKUP_RASMGR_CONF=$(mktemp -u "$RASMGR_CONF.XXXXXXX")
  while [ -f "$BACKUP_RASMGR_CONF" ]; do
    BACKUP_RASMGR_CONF=$(mktemp -u "$RASMGR_CONF.XXXXXXX")
  done
}
prepare_configuration()
{
  local server_no="$1"
  [ -n "$server_no" ] || server_no=1
  get_backup_rasmgr_conf
  logn "backing up $RASMGR_CONF to $BACKUP_RASMGR_CONF... "
  cp "$RASMGR_CONF" "$BACKUP_RASMGR_CONF"
  check
  logn "updating connect string in $RASMGR_CONF to $DB_DIR/RASBASE... "
  echo "define dbh rasdaman_host -connect $DB_DIR/RASBASE" > "$RASMGR_CONF"
  echo "define db RASBASE -dbh rasdaman_host" >> "$RASMGR_CONF"
  echo "" >> "$RASMGR_CONF"
  for i in $(seq 1 "$server_no"); do
    local port=$(($i + 1))
    echo "define srv N$i -host blade -type n -port 700$port -dbh rasdaman_host" >> "$RASMGR_CONF"
    echo "change srv N$i -countdown 200000 -autorestart on -xp --timeout 300000" >> "$RASMGR_CONF"
    echo "" >> "$RASMGR_CONF"
  done
}
restore_configuration()
{
  if [ -n "$BACKUP_RASMGR_CONF" -a -f "$BACKUP_RASMGR_CONF" ]; then
    logn "restoring $RASMGR_CONF from $BACKUP_RASMGR_CONF... "
    cp "$BACKUP_RASMGR_CONF" "$RASMGR_CONF"
    feedback
    rm -f "$BACKUP_RASMGR_CONF"
  fi
}

recreate_rasbase()
{
  rm -rf "$DB_DIR"; mkdir -p "$DB_DIR"
  rm -rf "$LOG_DIR"/*
  logn "recreating RASBASE... "
  rasdl -c --connect "$DB_DIR/RASBASE" > /dev/null && rasdl --connect "$DB_DIR/RASBASE" -r "$RMANHOME/share/rasdaman/examples/rasdl/basictypes.dl" -i > /dev/null
  check
  restart_rasdaman
}
# ------------------------------------------------------------------------------

# print the server pid (single rasserver should be running, otherwise wrong pid might be determined)
get_server_pid()
{
  local -r server_pid=$(ps aux | grep 'bin/rasserver' | grep -v grep | awk '{ print $2; }' | head -n 1)
  [ -n "$server_pid" ] && echo "$server_pid"
}

#
# load all modules
#
. "$UTIL_SCRIPT_DIR"/rasql.sh
. "$UTIL_SCRIPT_DIR"/petascope.sh
