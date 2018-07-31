package org.reactome.server.tools.indexer.impl;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.json.JSONException;
import org.json.JSONObject;
import org.reactome.server.graph.domain.model.*;
import org.reactome.server.graph.domain.result.SimpleDatabaseObject;
import org.reactome.server.graph.service.AdvancedDatabaseObjectService;
import org.reactome.server.graph.service.GeneralService;
import org.reactome.server.graph.service.SchemaService;
import org.reactome.server.interactors.database.InteractorsDatabase;
import org.reactome.server.interactors.exception.InvalidInteractionResourceException;
import org.reactome.server.interactors.model.Interaction;
import org.reactome.server.interactors.model.InteractionDetails;
import org.reactome.server.interactors.model.Interactor;
import org.reactome.server.interactors.service.InteractionService;
import org.reactome.server.interactors.service.InteractorService;
import org.reactome.server.interactors.util.InteractorConstant;
import org.reactome.server.interactors.util.Toolbox;
import org.reactome.server.tools.indexer.exception.IndexerException;
import org.reactome.server.tools.indexer.model.IndexDocument;
import org.reactome.server.tools.indexer.model.InteractorSummary;
import org.reactome.server.tools.indexer.model.ReactomeSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.sql.SQLException;
import java.util.*;

/**
 * This class is responsible for establishing connection to Solr
 * and the Graph Database. It iterates through the collection of
 * nodes, create IndexDocuments and add them to the Solr Server.
 *
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 * @author Florian Korninger <fkorn@ebi.ac.uk>
 * @version 2.0
 */
@Service
public class Indexer {
    private static final Logger logger = LoggerFactory.getLogger("importLogger");

    private static final String EBEYE_NAME = "Reactome";
    private static final String EBEYE_DESCRIPTION = "Reactome is a free, open-source, curated and peer reviewed pathway " +
            "database. Our goal is to provide intuitive bioinformatics tools for the visualization, interpretation and " +
            "analysis of pathway knowledge to support basic research, genome analysis, modeling, systems biology and " +
            "education.";

    // Collection that holds accessions from IntAct that are not in Reactome Data.
    // This collection will be used to keep interactions to those accession not in Reactome.
    private static final Set<String> accessionsNotInReactome = new HashSet<>();
    private static InteractorService interactorService;
    private static InteractionService interactionService;

    // Reactome Ids and names (ReactomeSummary) and their reference Entity accession identifier
    private final Map<String, ReactomeSummary> accessionMap = new HashMap<>();
    private final Map<Integer, String> taxonomyMap = new HashMap<>();

    private SchemaService schemaService;
    private GeneralService generalService;
    private AdvancedDatabaseObjectService advancedDatabaseObjectService;

    // Creating SolR Document querying the Graph in Transactional execution
    private DocumentBuilder documentBuilder;

    private SolrClient solrClient;
    private Marshaller marshaller;

    private Boolean xml = false;
    private long total;

    public int index() throws IndexerException {
        long start = System.currentTimeMillis();
        int entriesCount = 0;

        totalCount();

        try {
            if (xml) {
                int releaseNumber = 0;
                try {
                    releaseNumber = generalService.getDBVersion();
                } catch (Exception e) {
                    logger.error("An error occurred when trying to retrieve the release number from the database.");
                }
                marshaller.writeHeader(releaseNumber);
            }

            cleanSolrIndex();

            entriesCount += indexBySchemaClass(PhysicalEntity.class, entriesCount);
            commitSolrServer();
            cleanNeo4jCache();

            entriesCount += indexBySchemaClass(Event.class, entriesCount);
            commitSolrServer();
            cleanNeo4jCache();

            entriesCount += indexBySchemaClass(Regulation.class, entriesCount);
            commitSolrServer();
            cleanNeo4jCache();

            if (xml) {
                marshaller.writeFooter(entriesCount);
            }

            logger.info("Started importing Interactors data to SolR");
            entriesCount += indexInteractors();
            commitSolrServer();
            logger.info("Entries total: " + entriesCount);

            long end = System.currentTimeMillis() - start;
            logger.info("Full indexing took " + end + " .ms");

            System.out.println("\nData Import finished with " + entriesCount + " entries imported.");

            return entriesCount;
        } catch (Exception e) {
            logger.error("An error occurred during the data import", e);
            e.printStackTrace();
            throw new IndexerException(e);
        } finally {
            closeSolrServer();
        }
    }

    /**
     * @param clazz class to be Indexed
     * @return total of indexed items
     */
    private int indexBySchemaClass(Class<? extends DatabaseObject> clazz, int previousCount) throws IndexerException {
        long start = System.currentTimeMillis();

        logger.info("Getting all simple objects of class " + clazz.getSimpleName());
        Collection<Long> allOfGivenClass = schemaService.getDbIdsByClass(clazz);
        logger.info("[" + allOfGivenClass.size() + "] " + clazz.getSimpleName());

        final int addInterval = 1000;
        int numberOfDocuments = 0;
        int count = 0;
        List<IndexDocument> allDocuments = new ArrayList<>();
        List<Long> missingDocuments = new ArrayList<>();
        for (Long dbId : allOfGivenClass) {

            IndexDocument document = documentBuilder.createSolrDocument(dbId); // transactional
            if (document != null) {
                if (xml) marshaller.writeEntry(document);
                allDocuments.add(document);
            } else {
                missingDocuments.add(dbId);
            }

            numberOfDocuments++;
            if (numberOfDocuments % addInterval == 0 && !allDocuments.isEmpty()) {
                addDocumentsToSolrServer(allDocuments);
                allDocuments.clear();

                if (xml) {
                    try {
                        marshaller.flush();
                    } catch (IOException e) {
                        logger.error("An error occurred when trying to flush to XML", e);
                    }
                }
                logger.info(numberOfDocuments + " " + clazz.getSimpleName() + " have now been added to SolR");
            }

            count = previousCount + numberOfDocuments;
            if (count % 100 == 0) {
                updateProgressBar(count);
            }

            if (numberOfDocuments % 30000 == 0) cleanNeo4jCache();
        }

        // Add to Solr the remaining documents
        if (!allDocuments.isEmpty()) {
            addDocumentsToSolrServer(allDocuments);
        }

        long end = System.currentTimeMillis() - start;
        logger.info("Elapsed time for " + clazz.getSimpleName() + " is " + end + "ms.");

        if (!missingDocuments.isEmpty()) {
            logger.info("\nMissing documents for:\n\t" + StringUtils.join(missingDocuments, "\n\t"));
        }

        updateProgressBar(count); // done

        return numberOfDocuments;
    }

    /**
     * Count how many instances we are going to index.
     * This is going to be applied in the progress bar
     */
    private void totalCount() {
        logger.info("Counting all entries for Event, PhysicalEntities and Regulation");
        total = schemaService.countEntries(Event.class);
        total += schemaService.countEntries(PhysicalEntity.class);
        total += schemaService.countEntries(Regulation.class);
    }

    /**
     * Cleaning Solr Server (removes all current Data)
     *
     * @throws IndexerException not cleaning the indexer means the indexer will failed.
     */
    private void cleanSolrIndex() throws IndexerException {
        try {
            logger.info("Cleaning solr index");
            solrClient.deleteByQuery("*:*");
            commitSolrServer();
            logger.info("Solr index has been cleaned");
        } catch (SolrServerException | IOException e) {
            logger.error("an error occurred while cleaning the SolrServer", e);
            throw new IndexerException("an error occurred while cleaning the SolrServer", e);
        }
    }

    /**
     * Closes connection to Solr Server
     */
    private void closeSolrServer() {
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
    private void commitSolrServer() throws IndexerException {
        try {
            solrClient.commit();
            logger.info("Solr index has been committed and flushed to disk");
        } catch (Exception e) {
            logger.error("Error occurred while committing", e);
            throw new IndexerException("Could not commit", e);
        }
    }

    /**
     * Safely adding Document Bean to Solr Server
     *
     * @param documents List of Documents that will be added to Solr
     *                  <p>
     *                  REMOTE_SOLR_EXCEPTION is a Runtime Exception
     */
    private void addDocumentsToSolrServer(List<IndexDocument> documents) {
        if (documents != null && !documents.isEmpty()) {
            try {
                solrClient.addBeans(documents);
                logger.debug(documents.size() + " Documents successfully added to SolR");
            } catch (IOException | SolrServerException | HttpSolrClient.RemoteSolrException e) {
                for (IndexDocument document : documents) {
                    try {
                        solrClient.addBean(document);
                        logger.debug("A single document was added to Solr");
                    } catch (IOException | SolrServerException | HttpSolrClient.RemoteSolrException e1) {
                        logger.error("Could not add document", e);
                        logger.error("Document DBID: " + document.getDbId() + " Name " + document.getName());
                    }
                }
                logger.error("Could not add document", e);
            }
        } else {
            logger.error("Solr Documents are null or empty");
        }
    }

    public void setSolrClient(SolrClient solrClient) {
        this.solrClient = solrClient;
    }

    public Boolean getXml() {
        return xml;
    }

    public void setXml(Boolean xml) {
        this.xml = xml;
        if (xml) {
            marshaller = new Marshaller(new File("ebeye.xml"), EBEYE_NAME, EBEYE_DESCRIPTION);
        }
    }

    public void setInteractorsDatabase(InteractorsDatabase interactorsDatabase) {
        interactorService = new InteractorService(interactorsDatabase);
        interactionService = new InteractionService(interactorsDatabase);
    }

    /**
     * Save a document containing an interactor that IS NOT in Reactome and a List of Interactions
     * with Reactome proteins
     *
     * @throws IndexerException interactors are mandatory
     */
    private int indexInteractors() throws IndexerException {
        logger.info("Start indexing interactors into Solr");

        int numberOfDocuments = 0;
        try {
            List<IndexDocument> collection = new ArrayList<>();

            // Querying interactor database and retrieve all unique accession identifiers of intact-micluster file
            logger.info("Getting all accessions from Interactors Database");
            List<String> accessionsList = interactorService.getAllAccessions();

            createTaxonomyMap();

            // Removing accession identifier that are not UniProt/ChEBI accession Identifier.
            // The intact file keeps the same Intact id in this case.
            accessionsList.removeIf(str -> str.startsWith("EBI-"));

            // Queries gk_instance and create a list of accessions that are not in reactome and
            // also a map with the accession +information (stIds,names) in reactome
            createAccessionSet(accessionsList);

            System.out.println("\n[Interactors] Started adding to SolR");

            // Get Interactions for all accessions that are NOT in Reactome.
            // Keep in mind that we are only saving interactions having score higher than InteractorConstant.MINIMUM_VALID_SCORE
            // The result of this query is Map having the accession as the key and a list of interactions. Take into account the
            // Interaction domain has InteractorA and InteractorB where interactorA is ALWAYS the same as the map key.
            // e.g map K=q13501, interactorA=q13501, interactorB=p12345 (this is the interaction)
            Map<String, List<Interaction>> interactions = interactionService.getInteractions(accessionsNotInReactome, InteractorConstant.STATIC);

            logger.info("Preparing SolR documents for Interactors [" + interactions.size() + "]");
            total = interactions.size();
            int preparingSolrDocuments = 0;
            for (String accKey : interactions.keySet()) {
                Set<InteractorSummary> interactorSummarySet = new HashSet<>();

                // Interaction --> InteractorA and InteractorB where:
                //   InteractorA is the one being queried in the database
                //   InteractorB is the one that Interacts with A.
                interactions.get(accKey).stream().filter(interaction -> accessionMap.containsKey(interaction.getInteractorB().getAcc())).forEach(interaction -> {
                    InteractorSummary summary = new InteractorSummary();
                    // get reactome information from the map based on interactor B. Interactor A is the one we are creating the document
                    summary.setReactomeSummary(accessionMap.get(interaction.getInteractorB().getAcc()));
                    summary.setAccession(interaction.getInteractorB().getAcc());
                    summary.setScore(interaction.getIntactScore());

                    for (InteractionDetails interactionDetails : interaction.getInteractionDetailsList()) {
                        summary.addInteractionEvidences(interactionDetails.getInteractionAc());
                    }

                    interactorSummarySet.add(summary);
                });

                if (!interactorSummarySet.isEmpty()) {
                    // Create index document based on interactor A and the summary based on Interactor B.
                    IndexDocument indexDocument = createInteractorsDocument(interactions.get(accKey).get(0).getInteractorA(), interactorSummarySet);
                    collection.add(indexDocument);

                    numberOfDocuments++;
                }

                preparingSolrDocuments++;
                if (preparingSolrDocuments % 1000 == 0) {
                    logger.info("  >> preparing interactors SolR Documents [" + preparingSolrDocuments + "]");
                }
                if (preparingSolrDocuments % 100 == 0) {
                    updateProgressBar(preparingSolrDocuments);
                }
            }

            logger.info("  >> preparing interactors SolR Documents [" + preparingSolrDocuments + "]");

            // Save the indexDocument into Solr.
            addDocumentsToSolrServer(collection);

            logger.info(numberOfDocuments + " Interactor(s) have now been added to SolR");

            updateProgressBar(preparingSolrDocuments);

        } catch (InvalidInteractionResourceException | SQLException e) {
            throw new IndexerException(e);
        }

        return numberOfDocuments;
    }


    /**
     * Query Ensembl REST API in order to get the taxonomy lineage
     * and then get the parent.
     * <p>
     * Once we found the species we add it to the global map, it will
     * reduce the amount of queries to an external resource.
     *
     * @return the species
     */
    private String getTaxonomyLineage(Integer taxId) {
        if (taxId == 1 || taxId == 0 || taxId == -1) {
            return "Entries without species";
        }

        try {
            String urlString = "http://rest.ensembl.org/taxonomy/id/" + taxId;
            URL url = new URL(urlString);

            URLConnection connection = url.openConnection();
            HttpURLConnection httpConnection = (HttpURLConnection) connection;
            httpConnection.setRequestProperty("Content-Type", "application/json");

            InputStream response = httpConnection.getInputStream();

            String StringFromInputStream = IOUtils.toString(response, "UTF-8");
            JSONObject jsonObject = new JSONObject(StringFromInputStream);

            int parentTaxId = jsonObject.getJSONObject("parent").getInt("id");

            if (taxonomyMap.containsKey(parentTaxId)) {
                String species = taxonomyMap.get(parentTaxId);
                taxonomyMap.put(taxId, species);
                return species;
            }

            response.close();

        } catch (IOException | JSONException e) {
            String msg = e.getMessage();
            if (msg.contains("429")) { // If we hammer ensembl server than we get an 429 STATUS CODE, if that occurs we just wait 50sec.
                try {
                    Thread.sleep(50000);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
                return getTaxonomyLineage(taxId);
            } else {
                logger.info("Taxomony ID does not exist: " + msg);
            }
        }
        return "Entries without species";
    }

    /**
     * Get all species available in Reactome and add them to taxonomy map
     */
    private void createTaxonomyMap() {
        logger.info("Creating taxonomy map.");
        Collection<Species> speciesCollection = schemaService.getByClass(Species.class);
        for (Species species : speciesCollection) {
            taxonomyMap.put(Integer.parseInt(species.getTaxId()), species.getDisplayName());
        }
        logger.info("Taxonomy map is done.");
    }

    /**
     * Queries gk_instance and create a list of accessions that are not in reactome (accessionsNotInReactome) and
     * also a map with the accession +information (stIds,names) in reactome (accessionMap).
     *
     * @param accessionList all unique accessions from Interactors Database excluding those that start with EBI-. They are provided by IntAct but does not have accession.
     * @throws IndexerException interactors are mandatory
     */
    private void createAccessionSet(List<String> accessionList) throws IndexerException {

        System.out.println("\n[Interactors] Creating accession set");
        int progress = 0;

        // Making a copy of the original accession list. Accessions that exist in Reactome will be removed from this
        // collection. The final collection will hold those accessions that are not present in Reactome.
        accessionsNotInReactome.addAll(accessionList);
        Collection<String> referenceEntities;

        try {

            // Get all ReferenceEntities in Reactome Database. We have around 370000. These are the objects which have the accession.
            // Then, check if the given ref. identifier is in the accessionList (which has all the accessions from IntAct).
            String queryEntities = "MATCH (n:ReferenceEntity) RETURN DISTINCT n.identifier AS identifier";
            referenceEntities = advancedDatabaseObjectService.customQueryResults(String.class, queryEntities, null);
            total = referenceEntities.size();
            logger.info("Retrieving accessions from Reactome -- Accession list has [" + accessionList.size() + "] entries and [" + referenceEntities.size() + "] ReferenceEntities");
            for (String accession : referenceEntities) {
                if (progress % 100 == 0) {
                    updateProgressBar(progress);
                }
                progress++;

                if (!accessionList.contains(accession)) continue;

                // Removing the identifier that exists in Reactome.
                // Remember, the final collection will hold those accessions that are not present in Reactome.
                // At, we are going to get the Interactions using the accessions that are not in reactome.
                accessionsNotInReactome.remove(accession);

                // Get the referenceEntity in the referers, this instance has the accession we are interested in
                Map<String, Object> param = new HashMap<>();
                param.put("accession", accession);

                // Retrieves the referenceEntity if it is directly associated to a Reaction.
                String query = "MATCH (:ReferenceEntity{identifier:{accession}})<-[:referenceEntity]-(pe:PhysicalEntity)<-[:input|output|regulator|regulatedBy|physicalEntity|catalystActivity*]-(:ReactionLikeEvent) " +
                        "RETURN DISTINCT pe.dbId as dbId, pe.stId as stId, pe.displayName as displayName";
                Collection<SimpleDatabaseObject> ref = advancedDatabaseObjectService.customQueryForObjects(SimpleDatabaseObject.class, query, param);

                if (ref == null || ref.isEmpty()) continue;

                for (SimpleDatabaseObject simpleDatabaseObject : ref) {

                    // accessionMap is a map that has the accession as the Key
                    // and ReactomeSummary as the value. ReactomeSummary holds a list
                    // of ids (StId) and names that are refer to the accession.
                    if (accessionMap.containsKey(accession)) {
                        // If the accession is in the map, we get it and add the Id and Name into the list
                        ReactomeSummary summary = accessionMap.get(accession);
                        summary.addId(getId(simpleDatabaseObject));
                        summary.addName(simpleDatabaseObject.getDisplayName());
                    } else {
                        // Otherwise create a new one and add to the map
                        ReactomeSummary summary = new ReactomeSummary();
                        summary.addId(getId(simpleDatabaseObject));
                        summary.addName(simpleDatabaseObject.getDisplayName());
                        accessionMap.put(accession, summary);
                    }
                }
            }

            logger.info("  >> querying accessions in the Graph [" + progress + "]");

            updateProgressBar(progress); // done

        } catch (Exception e) {
            logger.error("Fetching Instances by ClassName from the Database caused an error", e);
            throw new IndexerException("Fetching Instances by ClassName from the Database caused an error", e);
        }
    }

    private String getId(SimpleDatabaseObject simpleDatabaseObject) throws Exception {
        if (StringUtils.isNotEmpty(simpleDatabaseObject.getStId())) {
            return simpleDatabaseObject.getStId();
        } else {
            logger.warn("No StableIdentifier for " + simpleDatabaseObject.getDbId() + " >> " + simpleDatabaseObject.getDisplayName());
            return simpleDatabaseObject.getDbId().toString();
        }
    }

    /**
     * Creating interactor document, where the Interactor A is the base and the B is the interactor which has
     * the reactome information.
     */
    private IndexDocument createInteractorsDocument(Interactor interactorA, Set<InteractorSummary> interactorSummarySet) {

        IndexDocument document = new IndexDocument();
        document.setDbId(interactorA.getAcc());

        // In the interactors.db we are saving the alias null if it is the same as the accession
        // Just assigning the acc in the name which is required here
        if (interactorA.getAlias() != null) {
            document.setName(interactorA.getAliasWithoutSpecies(false));
        } else {
            document.setName(interactorA.getAcc());
        }

        document.setType("Interactor");
        document.setExactType("Interactor");

        document.setSynonyms(Collections.singletonList(interactorA.getAlias()));
        document.setReferenceIdentifiers(Collections.singletonList(interactorA.getAcc()));
        document.setReferenceURL(Toolbox.getAccessionURL(interactorA.getAcc(), InteractorConstant.STATIC));
        document.setDatabaseName(Toolbox.getDatabaseName(interactorA.getAcc()));

        String species;
        if (taxonomyMap.containsKey(interactorA.getTaxid())) {
            species = taxonomyMap.get(interactorA.getTaxid());
        } else {
            species = getTaxonomyLineage(interactorA.getTaxid());
        }
        document.setSpecies(Collections.singletonList(species));

        List<String> interactionIds = new ArrayList<>();
        List<String> accessions = new ArrayList<>();
        List<String> reactomeIds = new ArrayList<>();
        List<String> reactomeNames = new ArrayList<>();
        List<Double> scores = new ArrayList<>();

        for (InteractorSummary interactorSummary : interactorSummarySet) {
            reactomeIds.add(parseList(interactorSummary.getReactomeSummary().getReactomeId()));
            reactomeNames.add(parseList(interactorSummary.getReactomeSummary().getReactomeName()));

            interactionIds.add(parseList(interactorSummary.getInteractionEvidences()));

            scores.add(interactorSummary.getScore());
            accessions.add(interactorSummary.getAccession());
        }

        document.setInteractionsIds(interactionIds);
        document.setReactomeInteractorIds(reactomeIds);
        document.setReactomeInteractorNames(reactomeNames);
        document.setScores(scores);
        document.setInteractorAccessions(accessions);

        return document;
    }

    /**
     * Saving a List into a multivalued field in SolR, but calling toString the final result will
     * be a comma-separated list. When parsing this List in the client (splitting by comma) then it may split
     * other identifiers which has comma as part of its name.
     * e.g Reactome names has comma on it.
     * "[NUDC [cytosol], NUDC [nucleoplasm], p-S274,S326-NUDC [nucleoplasm]]"
     * This multivalued field has 3 values, but splitting them by comma will result in
     * 4 values.
     * <p>
     * This parser retrieve the list as String using # as delimiter.
     */
    private String parseList(List<String> list) {
        String delimiter = "";
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            sb.append(delimiter);
            delimiter = "#";
            sb.append(s);
        }
        return sb.toString();
    }

    /**
     * Simple method that prints a progress bar to command line
     *
     * @param done Number of entries added
     */
    private void updateProgressBar(int done) {
        final int width = 55;

        String format = "\r%3d%% %s %c";
        char[] rotators = {'|', '/', '—', '\\'};
        double percent = (double) done / total;
        StringBuilder progress = new StringBuilder(width);
        progress.append('|');
        int i = 0;
        for (; i < (int) (percent * width); i++)
            progress.append("=");
        for (; i < width; i++)
            progress.append(" ");
        progress.append('|');
        System.out.printf(format, (int) (percent * 100), progress, rotators[((done - 1) % (rotators.length * 100)) / 100]);
    }

    private void cleanNeo4jCache() {
        generalService.clearCache();
    }

    @Autowired
    public void setSchemaService(SchemaService schemaService) {
        this.schemaService = schemaService;
    }

    @Autowired
    public void setGeneralService(GeneralService generalService) {
        this.generalService = generalService;
    }

    @Autowired
    public void setAdvancedDatabaseObjectService(AdvancedDatabaseObjectService advancedDatabaseObjectService) {
        this.advancedDatabaseObjectService = advancedDatabaseObjectService;
    }

    @Autowired
    public void setDocumentBuilder(DocumentBuilder documentBuilder) {
        this.documentBuilder = documentBuilder;
    }
}

