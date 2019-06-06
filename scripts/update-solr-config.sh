#!/usr/bin/env bash

#---------------------------------------------------------------------------------------------
# Update Solr Core files - schema.xml, solrconfig.xml, stopwords.txt, prefixstopwords.txt
# Execute the files as $sudo ./update-solr-core.sh -h
#
# Florian Korninger - fkorn@ebi.ac.uk
# Guilherme Viteri  - gviteri@ebi.ac.uk
#
#---------------------------------------------------------------------------------------------

# Default value
_SOLR_HOME="/var/solr"

_SOLR_CORE="reactome"
_SOLR_PORT=8983
_SOLR_USER="admin"
_SOLR_PASSWORD=""
_GITRAWURL="https://raw.githubusercontent.com"
_GITREPO="reactome"
_GITPROJECT="search-indexer"
_GITBRANCH="master"

if [[ $(id -u) -ne 0 ]] ; then echo "Please run as sudo." ; exit 1 ; fi

usage () {
    echo "Program to auto setup the Apache Lucene Solr in Reactome environment."
    echo "usage: sudo ./$(basename "$0") solrpass=<solr_passwd>"
    echo "              OPTIONAL solrcore=<solr_core>"
    echo "                       solrport=<solr_port>"
    echo "                       solruser=<solr_user>"
    echo "                       gitbranch=<git_branch>"
    echo ""
    echo "   where:"
    echo "       solrpass         REQUIRED"
    echo "       solrcore         DEFAULT: reactome"
    echo "       solruser         DEFAULT: admin"
    echo "       solrport         DEFAULT: 8983"
    echo "       gitbranch        DEFAULT: master (Download Solr configuration from git)"
    echo ""
    echo "e.g sudo ./$(basename "$0") solrpass=not2share"
    echo "e.g sudo ./$(basename "$0") solrpass=not2share solrcore=pathways gitbranch=dev"

    exit
}

# Check arguments
for ARGUMENT in "$@"
do
    KEY=$(echo ${ARGUMENT} | cut -f1 -d=)
    VALUE=$(echo ${ARGUMENT} | cut -f2 -d=)

    case "$KEY" in
            solrcore)       _SOLR_CORE=${VALUE} ;;
            solruser)       _SOLR_USER=${VALUE} ;;
            solrpass)       _SOLR_PASSWORD=${VALUE} ;;
            gitbranch)      _GITBRANCH=${VALUE} ;;
            help)           _HELP="help-me" ;;
            -h)             _HELP="help-me" ;;
            *)
    esac
done

if [[ "${_HELP}" == "help-me" ]]; then
    usage
fi

if [[ -z ${_SOLR_PASSWORD} ]]; then
    echo "missing argument for solrpass=<password>"
    exit 1
fi;

verifyBranch () {
    echo " > Validating GitHub Branch"
    STATUS=`curl -X GET -w "%{http_code}" --silent --output /dev/null "https://github.com/$_GITREPO/$_GITPROJECT/tree/$_GITBRANCH/"`
    if [[ 200 != "${STATUS}" ]]; then
        echo " > Invalid GitHub Branch: $_GITBRANCH"
        exit 1
    fi
    echo " > Branch OK."
}

updateSolrConfigFiles () {
    echo ""
    echo "Running SolR Update Configuration files"

    verifyBranch

    _SOLR_CORE_CONF_DIR=${_SOLR_HOME}/data/${_SOLR_CORE}/conf

    echo "Checking SolR Core configuration folder [$_SOLR_CORE_CONF_DIR]"
    if [[ ! -d "$_SOLR_CORE_CONF_DIR" ]]; then
        echo "Wrong SolR Home path was specified [$_SOLR_CORE_CONF_DIR]. Check Solr Core name and rerun".
        exit 1
    fi

    echo "Checking Solr Status"
    _STATUS=$(curl -H "Content-Type: application/json" --user ${_SOLR_USER}:${_SOLR_PASSWORD} --write-out "%{http_code}\n" --silent --output /tmp/solr.out "http://localhost:$_SOLR_PORT/solr/admin/cores?action=STATUS&wt=json")
    if [[ 401 == "$_STATUS" ]]; then
        echo "Could not check SolR Status. Invalid user/password combination."
        exit 1
    elif [[ 200 == "$_STATUS" ]]; then
        # HTTP Return 200, but the JSON output might contain the error.
        _FAILURES=$(cat /tmp/solr.out | python -c "import json, sys; obj=json.load(sys.stdin); print obj['initFailures']['$_SOLR_CORE'];" 2>/dev/null)
        _OUT=$?
        if [[ "$_OUT" == 0 ]] || [[ ! -z "$_FAILURES" ]]; then
            echo "SolR is running but there are issues in the core: [$_FAILURES]"
            echo "This script will automatically repair the core."
            echo "Rolling back SolR Core configuration based on the files from GitHub origin master"
            downloadCoreConfig master
        fi
    else
        if ! sudo service solr start 2>&1 /dev/null; then
            echo "SolR is not running and can not be started"
            exit 1
        fi
        exit 1
    fi

    echo "Checking Reactome core"
    _STATUS=$(curl -H "Content-Type: application/json" --user ${_SOLR_USER}:${_SOLR_PASSWORD} --write-out "%{http_code}\n" --silent --output /dev/null "http://localhost:$_SOLR_PORT/solr/$_SOLR_CORE/admin/ping?wt=json")
    if [[ 200 != "$_STATUS" ]]; then
        echo "Could not check SolR core [$_SOLR_CORE]. Consider a SolR fresh installation. Run sudo ./install-solr.sh solrpass=<solrpass>"
    fi

    echo "Shutting down Solr for updating the core"
    sudo service solr stop >/dev/null 2>&1

    echo "Updating SolR Configuration files based on GitHub - branch [$_GITBRANCH]"
    downloadCoreConfig ${_GITBRANCH}

    echo "Starting Solr"
    if ! sudo service solr start ; then
        echo "Solr has been updated, however we couldn't start it. Run 'sudo service solr start'"
        exit 2;
    fi

    echo "Successfully updated Solr"

}

downloadCoreConfig () {
    _BRANCH=$1
    sudo wget -q --no-check-certificate ${_GITRAWURL}/${_GITREPO}/${_GITPROJECT}/${_BRANCH}/solr-conf/${_SOLR_CORE}/schema.xml -O ${_SOLR_CORE_CONF_DIR}/schema.xml >/dev/null 2>&1
    sudo wget -q --no-check-certificate ${_GITRAWURL}/${_GITREPO}/${_GITPROJECT}/${_BRANCH}/solr-conf/${_SOLR_CORE}/solrconfig.xml -O ${_SOLR_CORE_CONF_DIR}/solrconfig.xml >/dev/null 2>&1
    sudo wget -q --no-check-certificate ${_GITRAWURL}/${_GITREPO}/${_GITPROJECT}/${_BRANCH}/solr-conf/${_SOLR_CORE}/stopwords.txt -O ${_SOLR_CORE_CONF_DIR}/stopwords.txt >/dev/null 2>&1
    sudo wget -q --no-check-certificate ${_GITRAWURL}/${_GITREPO}/${_GITPROJECT}/${_BRANCH}/solr-conf/${_SOLR_CORE}/prefixstopwords.txt -O ${_SOLR_CORE_CONF_DIR}/prefixstopwords.txt >/dev/null 2>&1
}

generalSummary () {
   echo "======================================"
   echo "============ Update SOLR ============="
   echo "======================================"
   echo "SolR Default Home:  " ${_SOLR_HOME}
   echo "SolR Core:          " ${_SOLR_CORE}
   echo "SolR Port:          " ${_SOLR_PORT}
   echo "SolR User:          " ${_SOLR_USER}
   echo "GitHub Branch:      " ${_GITBRANCH}
   echo "======================================"
}

# -- Print variables
generalSummary

updateSolrConfigFiles

echo "DONE. Bye!"
