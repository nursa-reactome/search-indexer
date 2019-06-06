package org.reactome.server.tools.indexer.util;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.reactome.server.tools.indexer.exception.IndexerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */
public class SolrUtility {
    private static final Logger logger = LoggerFactory.getLogger("importLogger");

    /**
     * Get solr connection using authentication
     *
     * @param user     solr user
     * @param password solr password
     * @param url      solr url
     * @return solr connection
     */
    public static SolrClient getSolrClient(String user, String password, String url) {
        if (user != null && !user.isEmpty() && password != null && !password.isEmpty()) {
            HttpClientBuilder builder = HttpClientBuilder.create().addInterceptorFirst(new PreemptiveAuthInterceptor());
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(user, password);
            credentialsProvider.setCredentials(AuthScope.ANY, credentials);
            HttpClient client = builder.setDefaultCredentialsProvider(credentialsProvider).build();
            return new HttpSolrClient.Builder(url).withHttpClient(client).build();
        }

        return new HttpSolrClient.Builder(url).build();
    }

    /**
     * Cleaning Solr Server (removes all current Data)
     *
     * @throws IndexerException not cleaning the indexer means the indexer will failed.
     */
    public static void cleanSolrIndex(String solrCore, SolrClient solrClient) throws IndexerException {
        cleanSolrIndex(solrCore, solrClient, "*:*");
    }

    /**
     * Cleaning Solr Server (removes all current Data)
     *
     * @throws IndexerException not cleaning the indexer means the indexer will failed.
     */
    public static void cleanSolrIndex(String solrCore, SolrClient solrClient, String query) throws IndexerException {
        try {
            logger.info("["+ solrCore +"] - Cleaning solr index");
            solrClient.deleteByQuery(solrCore,query);
            commitSolrServer(solrCore, solrClient);
            logger.info("["+ solrCore +"] - Solr index has been cleaned");
        } catch (SolrServerException | IOException e) {
            logger.error("["+ solrCore +"] Error occurred while cleaning the SolrServer", e);
            throw new IndexerException("["+ solrCore +"] Error occurred while cleaning the SolrServer", e);
        }
    }

    /**
     * Closes connection to Solr Server
     */
    public static void closeSolrServer(SolrClient solrClient) {
        try {
            solrClient.close();
            logger.info("SolrServer shutdown");
        } catch (IOException e) {
            logger.error("an error occurred while closing the SolrServer", e);
        }
    }

    /**
     * Commits Data that has been added till now to Solr Server
     *
     * @throws IndexerException not committing could mean that this Data will not be added to Solr
     */
    public static void commitSolrServer(String solrCore, SolrClient solrClient) throws IndexerException {
        try {
            solrClient.commit(solrCore);
            logger.info("["+ solrCore +"] Solr index has been committed and flushed to disk");
        } catch (Exception e) {
            logger.error("["+ solrCore +"] Error occurred while committing", e);
            throw new IndexerException("["+ solrCore +"] Could not commit", e);
        }
    }
}
