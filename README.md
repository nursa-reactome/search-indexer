[<img src=https://user-images.githubusercontent.com/6883670/31999264-976dfb86-b98a-11e7-9432-0316345a72ea.png height=75 />](https://reactome.org)

# Search Indexer
## What is Reactome Search ? ##
Reactome Search is a project that optimizes the queries in Reactome Website. Based on Apache Lucene, Reactome Graph Database is fully indexed by Apache SolR. SolR is versatile, it's configured and parametrized to face Reactome needs and requirements, delivering a high performance and accurate result list.
The Search Project is split into 'Indexer' and 'Search':

* Indexer: query Reactome Graph Database and index PhysicalEntities, Event and Regulation into SolR documents
* Search: Spring MVC Application which queries SolR documents in order to optimize the searching for Reactome Pathway Browser.

## Table of Contents ##
 - [Download](#download)
 - [Installing SolR](#installing-solr)
 - [Updating SolR Configuration Files](#updating-solr-configuration-files)
 - [Running Reactome Indexer](#running-reactome-indexer)
 - [SolR](#solr)

## Download ##
* Download all-in-one script setup https://github.com/reactome/Search/blob/master/setup-solr.sh
* Open a terminal and navigate to the folder where the script has been downloaded
* Check script options before executing

```console
$> ./setup-solr.sh -h

OUTPUT:
setup-solr.sh <execution_type -a, -b or -c> -m <solr_passwd>
                   [-d <neo4j_host> -e <neo4j_port> —f <neo4j_user> -g <neo4j_passwd>
                   -j <solr_core> -k <sorl_port> -l <solr_user> -n <solr_version>
                   -o <interactors_db_path>
                   -p <smtp_server> -q <smtp_port> -r <mail_from>
                   -s -t
                   -u <git_branch>
                   ] -- program to auto setup the Apache Lucene Solr in Reactome environment.

where:
    -h  Program help/usage

    Execution Type:
        -a  Install SolR                DEFAULT: false
        -b  Update SolR core            DEFAULT: false
        -c  Import Reactome data        DEFAULT: false

    Arguments:
        -d  Neo4j Host                  DEFAULT: localhost
        -e  Neo4j Port                  DEFAULT: 7474
        -f  Neo4j User                  DEFAULT: neo4j
        -g  Neo4j Password              REQUIRED [Import Reactome data]

        -j  SolR Core name              DEFAULT: reactome
        -k  SolR Port                   DEFAULT: 8983
        -l  SolR User                   DEFAULT: admin
        -m  SolR Password               REQUIRED [All Execution Type]
        -n  SolR Version                DEFAULT: 6.1.0

        -o  Interactors database path   DEFAULT: /usr/local/reactomes/Reactome/production/ContentService/interactors.db

        -p  Mail SMTP Server            DEFAULT: smtp.oicr.on.ca
        -q  Mail SMTP port              DEFAULT: 25
        -r  Mail From                   DEFAULT: reactome-developer@reactome.org

        -s  XML output for EBeye        DEFAULT: false
        -t  Send indexing report mail   DEFAULT: false

        -u  Indexer GitHub Branch       DEFAULT: master
```

## Installing SolR ##

:warning: Execute script as root.
  * You may need to specify a Solr Password. Please write it down - this is mandatory for reaching out the Solr Console Site.
  * Replace the default arguments if necessary...
  * Escape special characters if they are present in the password e.g not4shar\\&, use backslash (\\).

```console
$> sudo ./setup-solr.sh -a -m <solr_pass>
```

e.g

```console
$> sudo ./setup-solr.sh -a -m not2share
```

* To validate Apache SolR installation reach out the URL http://[serverip]:[port]/solr (must ask for Basic Authentication). Please provide the user and password configured in the setup-solr.sh script
* You're now able to run the Reactome Indexer. Follow next steps.

## Updating SolR Configuration Files ##

Automatic way to updated SolR Configuration files, mainly schema.xml (requires new indexing) and solrconfig.xml

:warning: Execute script as root.
  * You may need to specify a Solr Password used during Solr Installation
  * Replace the default arguments if necessary...
  * Escape special characters if they are present in the password e.g not4shar\\&, use backslash (\\).

```console
$> sudo ./setup-solr.sh -b -m <solr_pass>
```

e.g

```console
$> sudo ./setup-solr.sh -b -m not2share
```

* To verify the new configuration in SolR, go to http://[serverip]:[port]/solr (must ask for Basic Authentication).
  * Select your SolR Core
  * Under core, click on Files > select one of the files and confirm that your changes have been applied.

* You're now able to run the Reactome Indexer.

## Running Reactome Indexer ##

### :white_check_mark: Pre-Requirements ###

* SolR 6.x.x properly installed using setup-solr.sh option -a
  * You should be able to access http://[serverip]:[port]/solr
* Neo4j Graph Database + Reactome Graph Database
  * [Installing Neo4j](https://github.com/reactome/graph-importer)
  * [Download Reactome Graph Database](http://reactome.org/download/current/reactome.graphdb.tgz)
* Maven Setup: https://maven.apache.org/guides/getting-started/maven-in-five-minutes.html
* Escape special characters if they are present in the password e.g not4shar\\&, use backslash (\\).

### Indexer by default :books: ###

```console
$> ./setup-solr.sh -c -m <solr_pass> -g <neo4j_passwd>
```

e.g

```console
$> ./setup-solr.sh -c -m not2share -g neo4j
```

### Indexer + Ebeye.xml ###

* Specify -s and the ebeye.xml file is going to be created.

```console
$> ./setup-solr.sh -c -m not2share -g neo4j -s
```

### Indexer + Mail Notification :envelope: ###

  * Specify -t and an email is going to be sent at the end of indexing.
  * Change the default mail configuration by setting -p -q -r

```console
$> ./setup-solr.sh -c -m not2share -g neo4j -t
```

### Indexer + GitHub Branch ###

  * Specify GitHub branch in order to run the indexer based on the code for the given branch.

```console
$> ./setup-solr.sh -c -m not2share -g neo4j -u add_new_field
```

## SolR ##

### Useful commands ###

```console
sudo service solr [stop|start|restart|status]
```

### Solr Console ###

:computer: [Console](http://localhost:8983/solr/)
