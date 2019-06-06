package org.reactome.server.tools.indexer.target.impl;

import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.reactome.server.tools.indexer.exception.IndexerException;
import org.reactome.server.tools.indexer.target.model.Target;
import org.reactome.server.tools.indexer.target.parser.SwissProtParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.reactome.server.tools.indexer.util.SolrUtility.cleanSolrIndex;
import static org.reactome.server.tools.indexer.util.SolrUtility.commitSolrServer;

/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */
public class TargetIndexer {

    private static final Logger logger = LoggerFactory.getLogger("importLogger");
    private final String solrCoreTarget = "target";
    private String solrCoreSource;
    private SolrClient solrClient;

    /**
     * Index possible target proteins for Reactome.
     *
     * @param solrClient holds solr connection for target core
     */
    public TargetIndexer(SolrClient solrClient, String solrCoreSource) {
        this.solrClient = solrClient;
        this.solrCoreSource = solrCoreSource;
    }

    public void index() throws IndexerException {
        long start = System.currentTimeMillis();
        try {
            cleanSolrIndex(solrCoreTarget, solrClient);
            List<Target> targets = SwissProtParser.getInstance().getTargets();
            List<Target> addToSolr = new ArrayList<>();
            targets.parallelStream().forEach(target -> {
                try {
                    if (!isInReactome(target)) {
                        addToSolr.add(target);
                    }
                } catch (SolrServerException | IOException e) {
                    logger.error("["+ solrCoreTarget +"] An error occurred when creating SolR documents", e);
                }
            });

            addDocumentsToSolrServer(addToSolr);
            commitSolrServer(solrCoreTarget, solrClient);
            long end = System.currentTimeMillis() - start;
            logger.info("["+ solrCoreTarget +"] Full indexing took " + end + " .ms");
        } catch (Exception e) {
            logger.error("["+ solrCoreTarget +"] An error occurred during the data import", e);
            throw new IndexerException(e);
        }
    }

    /**
     * Safely adding Document Bean to Solr Server
     *
     * @param documents List of Documents that will be added to Solr
     *                  <p>
     *                  REMOTE_SOLR_EXCEPTION is a Runtime Exception
     */
    private void addDocumentsToSolrServer(List<Target> documents) {
        if (documents != null && !documents.isEmpty()) {
            try {
                solrClient.addBeans(solrCoreTarget, documents);
                logger.debug(documents.size() + " Documents successfully added to SolR");
            } catch (IOException | SolrServerException | HttpSolrClient.RemoteSolrException e) {
                for (Target target : documents) {
                    try {
                        solrClient.addBean(solrCoreTarget, target);
                        logger.debug("A single document was added to Solr");
                    } catch (IOException | SolrServerException | HttpSolrClient.RemoteSolrException e1) {
                        logger.error("Could not add document", e);
                    }
                }
                logger.error("Could not add document", e);
            }
        } else {
            logger.error("Solr Documents are null or empty");
        }
    }

    private boolean isInReactome(Target target) throws SolrServerException, IOException {
        QueryResponse response = solrClient.query(solrCoreSource, getSolrQuery(target));
        return response.getResults().getNumFound() > 0;
    }

    private SolrQuery getSolrQuery(Target target) {
        SolrQuery solrQuery = new SolrQuery();
        solrQuery.setRequestHandler("/search");
        solrQuery.setQuery(StringUtils.join(target.getAccessions(), "\" OR \""));
        return solrQuery;
    }
}
