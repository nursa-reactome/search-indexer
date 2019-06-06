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

* Cloning...

```console
$> git clone https://github.com/reactome/search-indexer.git

$> cd search-indexer
```

## Installing SolR ##

:warning: Execute script as root.
  * You may need to specify a Solr Password. Please write it down - this is mandatory for reaching out the Solr Console Site.
  * Replace the default arguments if necessary...
  * Escape special characters if they are present in the password e.g not4shar\\&, use backslash (\\).

```console
$> sudo ./scripts/install-solr.sh solrpass=not2share
```

* Help

```console
$> sudo ./scripts/install-solr.sh help
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
$> sudo ./scripts/update-solr-config.sh solrpass=not2share
```

* Help

```console
$> sudo ./scripts/update-solr-config.sh help
```

* To verify the new configuration in SolR, go to http://[serverip]:[port]/solr (must ask for Basic Authentication).
  * Select your SolR Core
  * Under core, click on Files > select one of the files and confirm that your changes have been applied.

* You're now able to run the Reactome Indexer.

## Running Reactome Indexer ##

### :white_check_mark: Pre-Requirements ###

* SolR 6.x.x properly installed using install-solr.sh
  * You should be able to access http://[serverip]:[port]/solr
* Neo4j Graph Database + Reactome Graph Database
  * [Installing Neo4j](https://github.com/reactome/graph-importer)
  * [Download Reactome Graph Database](https://reactome.org/download/current/reactome.graphdb.tgz)
* Maven Setup: https://maven.apache.org/guides/getting-started/maven-in-five-minutes.html
* Escape special characters if they are present in the password e.g not4shar\\&, use backslash (\\).

### Indexer default configuration: ###
* Data
* Ebeye.xml
* Icons + Mapping Files
* Website Sitemap files
* Target Swissprot (Search term)

```console
$> ./scripts/run-indexer.sh neo4jpass=not2share solrpass=not4you iconsdir=/home/reactome/icons ehlddir=/home/reactome/ehlds maildest=yourmail@solr6.com
```

Note: if ```maildest``` isn't provided no notification will be sent

Note 2: if ```iconsdir``` AND ```ehlddir``` aren't provided icons won't be indexed

* Help / extra options

```console
$> sudo ./scripts/run-indexer.sh help
```

## SolR ##

### Useful commands ###

```console
sudo service solr [stop|start|restart|status]
```

### Solr Console ###

:computer: [Console](http://localhost:8983/solr/)


## Indexing Icons only


```console
$> mvn clean package

$> java -cp target/Indexer-jar-with-dependencies.jar org.reactome.server.tools.indexer.IconsMain --solrPw xxx --iconsDir /path1 --ehldDir /path2
```
