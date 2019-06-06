package org.reactome.server.tools.indexer.icon.exporter;

import com.martiansoftware.jsap.*;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.reactome.server.tools.indexer.exception.IndexerException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.reactome.server.tools.indexer.util.SolrUtility.getSolrClient;

/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */

@SuppressWarnings("Duplicates")
public class IconsExporter {

    private static final String DEF_SOLR_URL = "http://localhost:8983/solr";
    private static final String DEF_SOLR_CORE = "reactome";
    private SolrClient solrClient;
    private String solrCore;

    public IconsExporter(SolrClient solrClient, String solrCore) {
        this.solrClient = solrClient;
        this.solrCore = solrCore;
    }

    public static void main(String[] args) throws JSAPException, IndexerException {
        SimpleJSAP jsap = new SimpleJSAP(IconsExporter.class.getName(), "Mapping resources and icons",
                new Parameter[]{
                        new FlaggedOption("solrUrl", JSAP.STRING_PARSER, DEF_SOLR_URL, JSAP.REQUIRED, 'a', "solrUrl", "Url of the running Solr server"),
                        new FlaggedOption("solrCore", JSAP.STRING_PARSER, DEF_SOLR_CORE, JSAP.REQUIRED, 'b', "solrCore", "The Reactome solr core"),
                        new FlaggedOption("solrUser", JSAP.STRING_PARSER, "admin", JSAP.NOT_REQUIRED, 'c', "solrUser", "The Solr user"),
                        new FlaggedOption("solrPw", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'd', "solrPw", "The Solr password"),
                        new FlaggedOption("outputDir", JSAP.STRING_PARSER, ".", JSAP.NOT_REQUIRED, 'e', "outputDir", "The output directory"),
                }
        );

        JSAPResult config = jsap.parse(args);
        if (jsap.messagePrinted()) System.exit(1);

        SolrClient solrClient = getSolrClient(config.getString("solrUser"), config.getString("solrPw"), config.getString("solrUrl"));
        IconsExporter tsvWriter = new IconsExporter(solrClient, config.getString("solrCore"));
        tsvWriter.write(config.getString("outputDir"));
    }

    public void write(String outputDir) throws IndexerException {
        File output = new File(outputDir);
        if(!output.exists()) {
            outputDir = ".";
        }

        try {
            Map<String, Set<SimpleIcon>> iconsPerRef = getIconsPerReference();
            if (iconsPerRef != null) {
                for (String db : iconsPerRef.keySet()) {
                    String prefix = getDBPrefix(db);
                    BufferedWriter writer = new BufferedWriter(new FileWriter(outputDir + File.separator + db + "2Icon.txt"));
                    Set<SimpleIcon> icons = iconsPerRef.get(db);
                    for (SimpleIcon icon : icons) {
                        writer.write(prefix + icon.getIdentifier() + "\t" + icon.getStId() + "\t" + icon.getName());
                        writer.newLine();
                    }
                    writer.flush();
                    writer.close();
                }
            }
        } catch (IOException e ){
            throw new IndexerException(e);
        }
    }

    private String getDBPrefix(String db) {
        String ret = "";
        switch (db) {
            case "GO":
                ret = "GO:";
                break;
            case "CHEBI":
                ret = "CHEBI:";
                break;
            case "CL":
                ret = "CL:";
                break;
        }
        return ret;
    }

    /**
     * Get ALL Icons
     */
    private Map<String, Set<SimpleIcon>> getIconsPerReference() {
        Map<String, Set<SimpleIcon>> mappingByRef = new HashMap<>();
        SolrQuery query = new SolrQuery();
        query.setRequestHandler("/search");
        query.setSort("stId", SolrQuery.ORDER.asc);
        query.setFilterQueries("{!term f=type}icon");
        query.setFields("stId", "name", "iconReferences");
        query.setRows(Integer.MAX_VALUE);
        try {
            QueryResponse response = solrClient.query(solrCore, query);
            SolrDocumentList solrDocument = response.getResults();
            if (solrDocument != null && !solrDocument.isEmpty()) {
                solrDocument.forEach(doc -> {
                    String stId = (String) doc.getFieldValue("stId");
                    String name = (String) doc.getFieldValue("name");
                    Collection<Object> referencesList = doc.getFieldValues("iconReferences");
                    if (referencesList != null && !referencesList.isEmpty()) {
                        List<String> refs = referencesList.stream().map(Object::toString).collect(Collectors.toList());
                        for (String dbAndId : refs) { // keep only entries where db is present [db:id]
                            if (dbAndId.contains(":")) {
                                String db = dbAndId.split(":")[0];
                                String identifier = dbAndId.split(":")[1];
                                mappingByRef.computeIfAbsent(db, k -> new TreeSet<>()).add(new SimpleIcon(identifier, stId, name));
                            }
                        }
                    }
                });
            }
            return mappingByRef;
        } catch (SolrServerException | IOException e) {
            // nothing here
        }
        return null;
    }
}
