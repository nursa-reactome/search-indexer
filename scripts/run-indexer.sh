#!/usr/bin/env bash

#-----------------------------------------------------------
# Script that automates the Indexer execution
#
# How to execute ?
# * Clone indexer project
#    > git clone https://github.com/reactome/search-indexer.git
# * Go to search-indexer/scripts
# * Execute the file as ./run-indexer.sh help
#
# Florian Korninger - fkorn@ebi.ac.uk
# Guilherme Viteri  - gviteri@ebi.ac.uk
#
#-----------------------------------------------------------

DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && cd .. && pwd)
cd ${DIR}

# Default value
_SOLR_HOME="/var/solr"

_SOLR_CORE="reactome"
_SOLR_PORT=8983
_SOLR_USER="admin"
_SOLR_PASSWORD=""

_NEO4J_HOST="localhost"
_NEO4J_PORT="7474"
_NEO4J_USER="neo4j"
_NEO4J_PASSWORD=""

_GITREPO="reactome"
_GITPROJECT="search-indexer"
_GITBRANCH="master"

_TARGET="yes"
_EBEYEXML="yes"
_SITEMAP="yes"
_MAIL_SMTP=""
_MAIL_PORT=""
_MAIL_DEST=""

_MVN=`which mvn`

usage () {
    echo "Program to Index Reactome data into SolR"
    echo "usage: sudo ./$(basename "$0") neo4jpass=<neo4j_passwd> solrpass=<solr_passwd> "
    echo "              OPTIONAL "
    echo "                       neo4jhost=<neo4j_host> without protocol"
    echo "                       neo4jport=<neo4j_port>"
    echo "                       neo4juser=<neo4j_user>"
    echo "                       solruser=<solr_user>"
    echo "                       solrcore=<solr_core>"
    echo "                       maildest=<mail_destination>"
    echo "                       mailsmtp=<mail_smtp>"
    echo "                       mailport=<mail_port>"
    echo "                       iconsdir=<icons library directory>"
    echo "                       ehlddir=<ehlds directory>"
    echo "                       ebeyexml=<y|t or n|f> Default: true"
    echo "                       sitemap=<y|t or n|f> Default: true"
    echo "                       target=<y|t or n|f> Default: true"
    echo "                       gitbranch=<git_branch>"
    echo ""
    echo "   where:"
    echo "       neo4jhost        DEFAULT: localhost"
    echo "       neo4jport        DEFAULT: 7474"
    echo "       neo4juser        DEFAULT: neo4j"
    echo "       neo4jpass        REQUIRED"
    echo "       solrcore         DEFAULT: reactome"
    echo "       solruser         DEFAULT: admin"
    echo "       solrpass         REQUIRED"
    echo "       gitbranch        DEFAULT: master (Check if local code matches given branch)"
    echo ""
    echo "e.g sudo ./$(basename "$0") neo4jpass=neopw solrpass=not2share"
    echo "e.g sudo ./$(basename "$0") neo4jpass=neopw solrpass=not2share solrcore=pathways gitbranch=dev"

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
            neo4jhost)      _NEO4J_HOST=${VALUE} ;;
            neo4jport)      _NEO4J_PORT=${VALUE} ;;
            neo4juser)      _NEO4J_USER=${VALUE} ;;
            neo4jpass)      _NEO4J_PASSWORD=${VALUE} ;;
            gitbranch)      _GITBRANCH=${VALUE} ;;
            maildest)       _MAIL_DEST=${VALUE} ;;
            mailsmtp)       _MAIL_SMTP=${VALUE} ;;
            mailport)       _MAIL_PORT=${VALUE} ;;
            ebeyexml)       _EBEYEXML=${VALUE} ;;
            sitemap)        _SITEMAP=${VALUE} ;;
            target)         _TARGET=${VALUE} ;;
            iconsdir)       _ICONS=${VALUE} ;;
            ehlddir)        _EHLDS=${VALUE} ;;
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

if [[ -z ${_NEO4J_PASSWORD} ]]; then
    echo "missing argument for neo4jpass=<password>"
    exit 1
fi;

# -- Check if neo4j is running and stops program otherwise
# -- If something was wrong with n4j we only knew after a java exception during the program execution
checkNeo4j() {
    _MSG="OK"
    _JSONFILE="query_result.json"
    _NEO4J_URL="http://$_NEO4J_USER:$_NEO4J_PASSWORD@$_NEO4J_HOST:$_NEO4J_PORT/db/data/"
    _STATUS=$(curl -H "Content-Type: application/json" ${_NEO4J_URL} --write-out "%{http_code}\n" --silent --output ${_JSONFILE})
    # no content from the server
    if [[ 000 == "$_STATUS" ]]; then
        _MSG="Neo4j is not running. Please check 'service neo4j status'"
    # didn't succeed
    elif [[ 200 != "$_STATUS" ]]; then
        _JSON_MSG=$(cat ${_JSONFILE} | python -c "import sys, json; print json.load(sys.stdin)['errors'][0]['message']")
        _MSG="Couldn't retrieve neo4j information. Reason [$_JSON_MSG]"
    fi
    if [[ -f "$_JSONFILE" ]]; then rm ${_JSONFILE}; fi
    echo "$_MSG"
}

getReleaseInfo() {
    _RELEASE_INFO="Couldn't retrieve DBInfo."
    _JSONFILE="query_result.json"
    _CYPHER='{"statements":[{"statement":"MATCH (n:DBInfo) RETURN n.version LIMIT 1"}]}'
    _NEO4J_URL="http://$_NEO4J_USER:$_NEO4J_PASSWORD@$_NEO4J_HOST:$_NEO4J_PORT/db/data/transaction/commit"
    _STATUS=$(curl -H "Content-Type: application/json" -d "$_CYPHER" ${_NEO4J_URL} --write-out "%{http_code}\n" --silent --output ${_JSONFILE})
    if [[ 200 == "$_STATUS" ]]; then
        _RELEASE_INFO=v-$(cat ${_JSONFILE} | sed 's/,//g;s/^.*row...\([0-9]*\).*$/\1/' | tr -d '[:space:]')
    fi
    if [[ -f "$_JSONFILE" ]]; then
        rm ${_JSONFILE}
    fi

    echo "$_RELEASE_INFO"
}

# -- Getting neo4j version, also check if neo4j is running and stops program otherwise
getNeo4jVersion() {
    _RET=""
    _JSONFILE="query_result.json"
    _NEO4J_URL="http://$_NEO4J_USER:$_NEO4J_PASSWORD@$_NEO4J_HOST:$_NEO4J_PORT/db/data/"
    _STATUS=$(curl -H "Content-Type: application/json" ${_NEO4J_URL} --write-out "%{http_code}\n" --silent --output ${_JSONFILE})
    if [[ 200 == "$_STATUS" ]]; then
        _RET=$(cat ${_JSONFILE} | python -c "import sys, json; print json.load(sys.stdin)['neo4j_version']")
    fi
    if [[ -f "$_JSONFILE" ]]; then
        rm ${_JSONFILE}
    fi

    echo "$_RET"
}


# SolR Data is created in $_SOLR_HOME/data/$_SOLR_CORE/data
runIndexer () {

    _MSG=$(checkNeo4j)
    if [[ "$_MSG" != "OK" ]]; then
        echo ${_MSG}
        exit 1
    fi

    echo "================ Neo4j ==============="
    echo "Neo4j host:         " "http://"${_NEO4J_HOST}":"${_NEO4J_PORT}
    echo "Neo4j user:         " ${_NEO4J_USER}
    echo "Neo4j Version:      " $(getNeo4jVersion)
    echo "DB Content:         " $(getReleaseInfo)
    echo "======================================"

    if [[ ! -z "$_GITBRANCH" ]]; then
        _CURRENT_BRANCH=$(git branch | sed -n -e 's/^\* \(.*\)/\1/p')
        if [[ "$_GITBRANCH" != "$_CURRENT_BRANCH" ]]; then
            echo "Your git branch [${_GITBRANCH}] parameter does not match the selected git branch in the project [${_CURRENT_BRANCH}]"
            exit 1
        fi
    fi

    echo "Checking if Solr is running..."
    _STATUS=$(curl -H "Content-Type: application/json" --user ${_SOLR_USER}:${_SOLR_PASSWORD} --write-out "%{http_code}\n" --silent --output /dev/null http://localhost:${_SOLR_PORT}/solr/admin/cores?action=STATUS)
    if [[ 200 != "$_STATUS" ]]; then
        if ! sudo service solr start >/dev/null 2>&1; then
            echo "Solr is not running and can not be started"
            exit 1;
        fi
        sleep 30s
    fi

    echo "Solr is running!"

    echo "Checking if Reactome core is available..."
    _STATUS=$(curl -H "Content-Type: application/json" --user ${_SOLR_USER}:${_SOLR_PASSWORD} --write-out "%{http_code}\n" --silent --output /dev/null http://localhost:${_SOLR_PORT}/solr/${_SOLR_CORE}/admin/ping)
    if [[ 200 != "$_STATUS" ]]; then
        echo "Reactome core is not available"
        exit 1;
    fi
    echo "Reactome core is available!"

    echo "Checking if current directory is valid project"
    if ! ${_MVN} -q -U clean package -DskipTests ; then
        if [[ ! -f ./target/Indexer-jar-with-dependencies.jar ]]; then
            echo "An error occurred when packaging the project."
            exit 1
        fi
    fi

    # Without core. Use -o <solr_core> in the java command
    _SOLR_URL=http://localhost:${_SOLR_PORT}/solr/

    if ! java -jar ./target/Indexer-jar-with-dependencies.jar --neo4jHost ${_NEO4J_HOST} \
                                                              --neo4jPort ${_NEO4J_PORT} \
                                                              --neo4jUser ${_NEO4J_USER} \
                                                              --neo4jPw ${_NEO4J_PASSWORD} \
                                                              --solrUrl ${_SOLR_URL} \
                                                              --solrCore ${_SOLR_CORE} \
                                                              --solrUser ${_SOLR_USER} \
                                                              --solrPw ${_SOLR_PASSWORD} \
                                                              --iconsDir ${_ICONS} \
                                                              --ehldDir ${_EHLDS} \
                                                              --mailDest ${_MAIL_DEST} \
                                                              --mailSmtp ${_MAIL_SMTP} \
                                                              --mailPort ${_MAIL_PORT} \
                                                              --ebeyexml ${_EBEYEXML} \
                                                              --sitemap ${_SITEMAP} \
                                                              --target ${_TARGET}; then
        echo "An error occurred during the Solr-Indexer process. Please check logs."
        exit 1
    fi

    echo "Successfully imported data to Solr!"
}

generalSummary () {
    shopt -s nocasematch
    _EBEYE="yes"
    if [[ "$_EBEYEXML" == "n" ]] || [[ "$_EBEYEXML" == "f" ]] || [[ "$_EBEYEXML" == "false" ]] || [[ "$_EBEYEXML" == "no" ]]; then
        _EBEYE="no";
    fi

    _TARGETOPT="yes"
    if [[ "$_TARGET" == "n" ]] || [[ "$_TARGET" == "f" ]] || [[ "$_TARGET" == "false" ]] || [[ "$_TARGET" == "no" ]]; then
        _TARGETOPT="no";
    fi

    _SITEMAPOPT="yes"
    if [[ "$_SITEMAP" == "n" ]] || [[ "$_SITEMAP" == "f" ]] || [[ "$_SITEMAP" == "false" ]] || [[ "$_SITEMAP" == "no" ]]; then
        _SITEMAPOPT="no";
    fi
    shopt -u nocasematch

    _SENDMAIL="NO"
    if [[ "$_MAIL_DEST" != "" ]]; then
        _SENDMAIL="YES - ";
    fi

    echo "======================================"
    echo "================ SOLR ================"
    echo "======================================"
    echo "SolR Default Home:  " ${_SOLR_HOME}
    echo "SolR Core:          " ${_SOLR_CORE}
    echo "SolR Port:          " ${_SOLR_PORT}
    echo "SolR User:          " ${_SOLR_USER}
    echo "Neo4j Host:         " ${_NEO4J_HOST}":"${_NEO4J_PORT}
    echo "Neo4j User:         " ${_NEO4J_USER}
    echo "ebeye.xml:          " ${_EBEYE}
    echo "Sitemap:            " ${_SITEMAPOPT}
    echo "Icons:              " ${_ICONS}
    echo "EHLDs:              " ${_EHLDS}
    echo "Target Core:        " ${_TARGETOPT}
    echo "Send Mail:          " ${_SENDMAIL}${_MAIL_DEST}
    echo "SMTP Server:        " ${_MAIL_SMTP}":"${_MAIL_PORT}
    echo "GitHub Branch:      " ${_GITBRANCH}
}

# -- Print variables
generalSummary

runIndexer

echo "DONE. Bye!"
