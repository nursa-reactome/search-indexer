package org.reactome.server.tools.indexer;

import org.apache.solr.client.solrj.SolrClient;
import org.reactome.server.tools.indexer.exception.IndexerException;
import org.reactome.server.tools.indexer.target.impl.TargetIndexer;

import static org.reactome.server.tools.indexer.util.SolrUtility.closeSolrServer;
import static org.reactome.server.tools.indexer.util.SolrUtility.getSolrClient;

/**
 * Simple main class only to index targets
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */
public class TargetMain {
    private static final String DEF_SOLR_URL = "http://localhost:8983/solr";

    public static void main(String[] args) throws IndexerException {
        SolrClient solrClient = getSolrClient("admin", "admin", DEF_SOLR_URL);
        TargetIndexer indexer = new TargetIndexer(solrClient,"reactome");
        indexer.index();
        closeSolrServer(solrClient);
    }
}
