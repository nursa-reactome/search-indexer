package org.reactome.server.tools.indexer.impl;

import org.apache.commons.lang3.StringUtils;
import org.neo4j.ogm.exception.MappingException;
import org.reactome.server.graph.domain.model.*;
import org.reactome.server.graph.exception.CustomQueryException;
import org.reactome.server.graph.service.AdvancedDatabaseObjectService;
import org.reactome.server.graph.service.DatabaseObjectService;
import org.reactome.server.tools.indexer.model.CrossReference;
import org.reactome.server.tools.indexer.model.IndexDocument;
import org.reactome.server.tools.indexer.model.SpeciesResult;
import org.reactome.server.tools.indexer.util.IndexerMapSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import javax.management.RuntimeErrorException;

/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */
@Service
class DocumentBuilder {
    private static final Logger logger = LoggerFactory.getLogger("importLogger");

    private static final String CONTROLLED_VOCABULARY = "controlledVocabulary.csv";

    private DatabaseObjectService databaseObjectService;
    private AdvancedDatabaseObjectService advancedDatabaseObjectService;

    private Map<Long, Set<String>> simpleEntitiesSpecies = null;

    private List<String> keywords;

    public DocumentBuilder() {
        keywords = loadFile(CONTROLLED_VOCABULARY);
        if (keywords == null) {
            logger.error("No keywords available");
        }
    }

    @Transactional
    IndexDocument createSolrDocument(Long dbId) {

        if (simpleEntitiesSpecies == null) {
            cacheSimpleEntitySpecies();
        }

        IndexDocument document = new IndexDocument();
        /*
         * Query the Graph and load only Primitives and no Relations attributes.
         * Lazy-loading will load them on demand.
         */
        DatabaseObject databaseObject;
        try {
            databaseObject = databaseObjectService.findById(dbId);
        } catch (MappingException e){
            logger.error("There has been an error mapping the object with dbId: " + dbId, e);
            return null;
        }

        // Setting common attributes
        if (databaseObject == null) {
            logger.error("Database object not found - id: " + dbId);
            return null;
        }
        document.setDbId(databaseObject.getDbId().toString());
        document.setStId(databaseObject.getStId());
        document.setOldStId(databaseObject.getOldStId());

        document.setType(getType(databaseObject));
        document.setExactType(databaseObject.getSchemaClass());

        if (databaseObject instanceof PhysicalEntity) {
            PhysicalEntity physicalEntity = (PhysicalEntity) databaseObject;

            // GENERAL ATTRIBUTES
            setNameAndSynonyms(document, physicalEntity, physicalEntity.getName());
            setLiteratureReference(document, physicalEntity.getLiteratureReference());
            setSummation(document, physicalEntity.getSummation());
            setDiseases(document, physicalEntity.getDisease());
            setCompartment(document, physicalEntity.getCompartment());
            setCrossReference(document, physicalEntity.getCrossReference());
            setSpecies(document, physicalEntity);

            // SPECIFIC FOR PHYSICAL ENTITIES
            setGoTerms(document, physicalEntity.getGoCellularComponent());
            setReferenceEntity(document, physicalEntity);

        } else if (databaseObject instanceof Event) {
            Event event = (Event) databaseObject;

            // GENERAL ATTRIBUTES
            setNameAndSynonyms(document, event, event.getName());
            setLiteratureReference(document, event.getLiteratureReference());
            setSummation(document, event.getSummation());
            setDiseases(document, event.getDisease());
            setCompartment(document, event.getCompartment());
            setCrossReference(document, event.getCrossReference());
            setSpecies(document, event);
            setAuthorAndReviewed(document, event);

            // SPECIFIC FOR EVENT
            setGoTerms(document, event.getGoBiologicalProcess());
            if (event instanceof ReactionLikeEvent) {
                ReactionLikeEvent reactionLikeEvent = (ReactionLikeEvent) event;
                setCatalystActivities(document, reactionLikeEvent.getCatalystActivity());
            }

        } else if (databaseObject instanceof Regulation) {
            Regulation regulation = (Regulation) databaseObject;

            // GENERAL ATTRIBUTES
            setNameAndSynonyms(document, regulation, regulation.getName());
            setLiteratureReference(document, regulation.getLiteratureReference());
            setSummation(document, regulation.getSummation());
            setSpecies(document, regulation);

            // SPECIFIC FOR REGULATIONS
            setRegulatedEntity(document, regulation.getRegulatedEntity());
            setRegulator(document, regulation.getRegulator());

        }

        setFireworksSpecies(document, databaseObject);

        // Keyword uses the document.getName. Name is set in the document by calling setNameAndSynonyms
        setKeywords(document);

        return document;
    }

    private void cacheSimpleEntitySpecies() {
        logger.info("Caching SimpleEntity Species");
        String query = "MATCH (n:SimpleEntity)<-[:regulatedBy|regulator|physicalEntity|entityFunctionalStatus|catalystActivity|hasMember|hasCandidate|hasComponent|repeatedUnit|input|output*]-(:ReactionLikeEvent)-[:species]->(s:Species) " +
                       "WITH n, COLLECT(DISTINCT s.displayName) AS species " +
                       "RETURN n.dbId AS dbId, species";
        try {
            Collection<SpeciesResult> speciesResultList = advancedDatabaseObjectService.customQueryForObjects(SpeciesResult.class, query, null);
            simpleEntitiesSpecies = new HashMap<>(speciesResultList.size());
            for (SpeciesResult speciesResult : speciesResultList) {
                simpleEntitiesSpecies.put(speciesResult.getDbId(), new HashSet<>(speciesResult.getSpecies()));
            }
        } catch (CustomQueryException e) {
            logger.error("Could not cache fireworks species");
        }

        logger.info("Caching SimpleEntity Species is done");
    }

    private void setFireworksSpecies(IndexDocument document, DatabaseObject databaseObject) {
        Set<String> fireworksSpecies = new HashSet<>();
        if ((databaseObject instanceof SimpleEntity)) {
            fireworksSpecies = simpleEntitiesSpecies.get(databaseObject.getDbId());
        } else {
            try {
                Method getSpecies = databaseObject.getClass().getMethod("getSpecies");
                Object species = getSpecies.invoke(databaseObject);
                // some cases like DefinedSet it has species as an attribute but it does not have value in it.
                if (species != null) {
                    if (species instanceof Collection) {
                        //noinspection Convert2streamapi,unchecked
                        for (Taxon t : (Collection<? extends Taxon>) species) {
                            fireworksSpecies.add(t.getDisplayName());
                        }
                    } else {
                        Taxon t = (Taxon) species;
                        fireworksSpecies.add(t.getDisplayName());
                    }
                }
            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                //Nothing here
            }
        }

        document.setFireworksSpecies(fireworksSpecies.isEmpty() ? null : fireworksSpecies);
    }

    private void setNameAndSynonyms(IndexDocument document, DatabaseObject databaseObject, List<String> name) {
        if (name == null || name.isEmpty()) {
            // some regulations do not have name
            document.setName(databaseObject.getDisplayName());
            return;
        }

        Iterator<String> iterator = name.iterator();
        // name is the first one of this list
        document.setName(iterator.next());
        // remove it and assign the other as synonyms.
        iterator.remove();

        if (!name.isEmpty()) {
            document.setSynonyms(name);
        }
    }

    private void setReferenceNameAndSynonyms(IndexDocument document, DatabaseObject databaseObject, List<String> referenceName) {
        if (referenceName == null || referenceName.isEmpty()) {
            document.setReferenceName(databaseObject.getDisplayName());
            return;
        }

        Iterator<String> iterator = referenceName.iterator();
        // name is the first one of this list
        document.setReferenceName(iterator.next());
        // remove it and assign the other as synonyms.
        iterator.remove();

        if (!referenceName.isEmpty()) {
            document.setReferenceSynonyms(referenceName);
        }
    }

    /**
     * Set literatureReference in SolR document based on the list of Publication
     *
     * @param document            is the SolR document.
     * @param literatureReference is the list of Publication
     */
    private void setLiteratureReference(IndexDocument document, List<Publication> literatureReference) {
        if (literatureReference == null) return;

        IndexerMapSet<String, String> mapSet = new IndexerMapSet<>();
        for (Publication publication : literatureReference) {
            mapSet.add("title", publication.getTitle());
            if (publication instanceof LiteratureReference) {
                mapSet.add("pubMedIdentifier", ((LiteratureReference) publication).getPubMedIdentifier() + "");
            } else if (publication instanceof Book) {
                Book book = (Book) publication;
                if (StringUtils.isNotEmpty(book.getISBN())) {
                    mapSet.add("ISBN", book.getISBN());
                }
            }

            // authorsList has duplicated values -> in order to replicate the previous Indexer, I am adding the DB_ID and
            // later on I will remove it. For the indexer, actually, it won't make difference indexing non potential duplicated
            // fields.
            mapSet.add("author", publication.getAuthor().stream().map(i -> i.getDbId() + "#" + i.getDisplayName()).collect(Collectors.toList()));
        }

        document.setLiteratureReferenceTitle(new ArrayList<>(mapSet.getElements("title")));
        document.setLiteratureReferencePubMedId(new ArrayList<>(mapSet.getElements("pubMedIdentifier")));
        document.setLiteratureReferenceIsbn(new ArrayList<>(mapSet.getElements("ISBN")));

        // This split by # is temporary here and may cause issues. Even getting the DB_ID won't give us the same as the previous
        // e.g The Reaction R-GGA-573294 has 2 LiteratureReference and 8 authors + 3 authors (11 authors according to gk_instance)
        // but those 3 we have 2 existing authors having the same DB_ID + 1 new author. At the end I have 9 authors. Which still
        // does not match 11. But ok... // TODO DISCUSS it.
        document.setLiteratureReferenceAuthor(mapSet.getElements("author").stream().map(i -> i.split("#")[1]).collect(Collectors.toList()));
    }

    private void setSummation(IndexDocument document, List<Summation> summations) {
        if (summations == null) return;

        String summationText = "";
        boolean first = true;
        for (Summation summation : summations) {
            if (first) {
                summationText = summation.getText();
                first = false;
            } else {
                summationText = summationText + "<br>" + summation.getText();
            }
        }

        if (!summationText.contains("computationally inferred")) {
            document.setSummation(summationText);
        } else {
            document.setInferredSummation(summationText);
        }
    }

    private void setDiseases(IndexDocument document, List<? extends ExternalOntology> diseases) {
        if (diseases == null || diseases.isEmpty()) {
            document.setIsDisease(false);
            return;
        }

        List<String> diseasesId = diseases.stream().map(ExternalOntology::getIdentifier).collect(Collectors.toList());
        diseasesId.addAll(diseases.stream().map(d -> "doid:" + d.getIdentifier()).collect(Collectors.toList()));

        document.setDiseaseId(diseasesId);
        document.setDiseaseName(diseases.stream().flatMap(e -> e.getName().stream()).collect(Collectors.toList()));
        // TODO: Create a report for those diseases which synonym is null
        document.setDiseaseSynonyms(diseases.stream().filter(f -> f.getSynonym() != null).flatMap(e -> e.getSynonym().stream()).collect(Collectors.toList()));
        document.setIsDisease(true);
    }

    private void setCompartment(IndexDocument document, List<? extends Compartment> compartments) {
        if (compartments == null || compartments.isEmpty()) return;

        document.setCompartmentName(compartments.stream().map(DatabaseObject::getDisplayName).collect(Collectors.toList()));
        document.setCompartmentAccession(compartments.stream().map(Compartment::getAccession).collect(Collectors.toList()));
    }

    private void setCrossReference(IndexDocument document, List<DatabaseIdentifier> crossReferences) {
        if (crossReferences == null || crossReferences.isEmpty()) return;

        // CrossReferencesIds add displayName which is <DB>:<ID> and also the Identifier
        List<String> crossReferencesInfo = new ArrayList<>();

        // allCrossReferences are used in the Marshaller (ebeye.xml)
        List<CrossReference> allXRefs = new ArrayList<>();
        for (DatabaseIdentifier databaseIdentifier : crossReferences) {
            crossReferencesInfo.add(databaseIdentifier.getDisplayName());
            crossReferencesInfo.add(databaseIdentifier.getIdentifier());

            CrossReference crossReference = new CrossReference();
            crossReference.setId(databaseIdentifier.getIdentifier());
            crossReference.setDbName(databaseIdentifier.getDatabaseName());
            allXRefs.add(crossReference);
        }

        document.setCrossReferences(crossReferencesInfo);
        document.setAllCrossReferences(allXRefs);
    }

    private void setGoTerms(IndexDocument document, GO_Term goTerm) {
        // The "GoTerm" field is a list - We add the plain value and the constant 'go:' concatenated to the plain value
        if (goTerm == null) return;

        if (goTerm instanceof GO_BiologicalProcess) {
            GO_BiologicalProcess gbp = (GO_BiologicalProcess) goTerm;
            document.setGoBiologicalProcessAccessions(Arrays.asList("go:".concat(gbp.getAccession()), gbp.getAccession()));
            document.setGoBiologicalProcessName(gbp.getDisplayName());
        } else if (goTerm instanceof GO_CellularComponent) {
            GO_CellularComponent gcc = (GO_CellularComponent) goTerm;
            document.setGoCellularComponentAccessions(Arrays.asList("go:".concat(gcc.getAccession()), gcc.getAccession()));
            document.setGoCellularComponentName(gcc.getDisplayName());
        } else if (goTerm instanceof GO_MolecularFunction) {
            GO_MolecularFunction gmf = (GO_MolecularFunction) goTerm;
            document.addGoMolecularFunctionName(gmf.getDisplayName());
            document.addGoMolecularFunctionAccession("go:".concat(gmf.getAccession()));
            document.addGoMolecularFunctionAccession(gmf.getAccession());
        }
    }

    private void setSpecies(IndexDocument document, DatabaseObject databaseObject) {
        Collection<? extends Taxon> speciesCollection = null;
        if (databaseObject instanceof GenomeEncodedEntity) {
            GenomeEncodedEntity genomeEncodedEntity = (GenomeEncodedEntity) databaseObject;
            if (genomeEncodedEntity.getSpecies() != null) {
                speciesCollection = Collections.singletonList(genomeEncodedEntity.getSpecies());
            }
        } else if (databaseObject instanceof EntitySet) {
            EntitySet entitySet = (EntitySet) databaseObject;
            speciesCollection = entitySet.getSpecies();
            if (entitySet.getRelatedSpecies() != null && !entitySet.getRelatedSpecies().isEmpty()) {
                document.setRelatedSpecies(entitySet.getRelatedSpecies().stream().map(Species::getDisplayName).collect(Collectors.toList()));
            }
        } else if (databaseObject instanceof Complex) {
            Complex complex = (Complex) databaseObject;
            speciesCollection = complex.getSpecies();
            if (complex.getRelatedSpecies() != null && !complex.getRelatedSpecies().isEmpty()) {
                document.setRelatedSpecies(complex.getRelatedSpecies().stream().map(Species::getDisplayName).collect(Collectors.toList()));
            }
        } else if (databaseObject instanceof SimpleEntity) {
            SimpleEntity simpleEntity = (SimpleEntity) databaseObject;
            if (simpleEntity.getSpecies() != null) {
                speciesCollection = Collections.singletonList(simpleEntity.getSpecies());
            }
        } else if (databaseObject instanceof Polymer) {
            Polymer polymer = (Polymer) databaseObject;
            speciesCollection = polymer.getSpecies();
        } else if (databaseObject instanceof Event) {
            Event event = (Event) databaseObject;
            speciesCollection = event.getSpecies();
            if (event.getRelatedSpecies() != null && !event.getRelatedSpecies().isEmpty()) {
                document.setRelatedSpecies(event.getRelatedSpecies().stream().map(Species::getDisplayName).collect(Collectors.toList()));
            }
        }

        if (speciesCollection == null || speciesCollection.isEmpty()) {
            document.setSpecies(Collections.singletonList("Entries without species"));
            return;
        }

        List<String> allSpecies = speciesCollection.stream().map(Taxon::getDisplayName).collect(Collectors.toList());
        document.setSpecies(allSpecies);

        document.setTaxId(speciesCollection.stream().map(Taxon::getTaxId).collect(Collectors.toList()));
    }

    private void setReferenceEntity(IndexDocument document, DatabaseObject databaseObject) {
        if (databaseObject == null) return;

        ReferenceEntity referenceEntity = null;
        String identifier;

        if (databaseObject instanceof EntityWithAccessionedSequence) {
            EntityWithAccessionedSequence ewas = (EntityWithAccessionedSequence) databaseObject;
            referenceEntity = ewas.getReferenceEntity();
        } else if (databaseObject instanceof OpenSet) {
            OpenSet openSet = (OpenSet) databaseObject;
            referenceEntity = openSet.getReferenceEntity();
        } else if (databaseObject instanceof SimpleEntity) {
            SimpleEntity simpleEntity = (SimpleEntity) databaseObject;
            referenceEntity = simpleEntity.getReferenceEntity();
        }

        if (referenceEntity != null) {
            identifier = referenceEntity.getIdentifier();

            if (referenceEntity instanceof ReferenceSequence) {
                ReferenceSequence referenceSequence = (ReferenceSequence) referenceEntity;
                document.setReferenceGeneNames(referenceSequence.getGeneName());
                document.setReferenceSecondaryIdentifier(referenceSequence.getSecondaryIdentifier());

                // variant Identifier has to be set in case is ReferenceIsoform
                if (referenceSequence instanceof ReferenceIsoform) {
                    ReferenceIsoform referenceIsoform = (ReferenceIsoform) referenceSequence;
                    if (StringUtils.isNotEmpty(referenceIsoform.getVariantIdentifier())) {
                        identifier = referenceIsoform.getVariantIdentifier();
                    }
                }
            }

            // Setting TYPE and EXACT TYPE for the given PhysicalEntity
            document.setType(getReferenceTypes(referenceEntity));
            document.setExactType(referenceEntity.getSchemaClass());

            if (referenceEntity.getName() != null && !referenceEntity.getName().isEmpty()) {
                setReferenceNameAndSynonyms(document, referenceEntity, referenceEntity.getName());
            }

            document.setReferenceOtherIdentifier(referenceEntity.getOtherIdentifier());

            setReferenceCrossReference(document, referenceEntity.getCrossReference());

            if (identifier != null) {
                List<String> referenceIdentifiers = new LinkedList<>();
                referenceIdentifiers.add(identifier);
                referenceIdentifiers.add(referenceEntity.getReferenceDatabase().getDisplayName() + ":" + identifier);
                document.setReferenceIdentifiers(referenceIdentifiers);
                document.setDatabaseName(referenceEntity.getReferenceDatabase().getDisplayName());

                String url = referenceEntity.getReferenceDatabase().getAccessUrl();
                if (StringUtils.isNotEmpty(url)) {
                    document.setReferenceURL(url.replace("###ID###", identifier));
                }
            }
        }
    }

    private String getReferenceTypes(ReferenceEntity referenceEntity) {
        if (referenceEntity instanceof ReferenceGeneProduct) {
            return "Protein";
        } else if (referenceEntity instanceof ReferenceDNASequence) {
            return "DNA Sequence";
        } else if (referenceEntity instanceof ReferenceRNASequence) {
            return "RNA Sequence";
        } else if (referenceEntity instanceof ReferenceMolecule || referenceEntity instanceof ReferenceGroup) {
            return "Chemical Compound";
        } else {
            return referenceEntity.getSchemaClass();
        }
    }

    private void setReferenceCrossReference(IndexDocument document, List<DatabaseIdentifier> referenceCrossReferences) {
        if (referenceCrossReferences == null || referenceCrossReferences.isEmpty()) return;

        // CrossReferencesIds add displayName which is <DB>:<ID> and also the Identifier
        List<String> crossReferencesInfo = new ArrayList<>();

        // allCrossReferences are used in the Marshaller (ebeye.xml)
        List<CrossReference> allXRefs = new ArrayList<>();
        for (DatabaseIdentifier databaseIdentifier : referenceCrossReferences) {
            crossReferencesInfo.add(databaseIdentifier.getIdentifier());

            CrossReference crossReference = new CrossReference();
            crossReference.setId(databaseIdentifier.getIdentifier());
            crossReference.setDbName(databaseIdentifier.getReferenceDatabase().getDisplayName());
            allXRefs.add(crossReference);
        }

        document.setReferenceCrossReferences(crossReferencesInfo);
        document.setAllCrossReferences(allXRefs);
    }

    private void setCatalystActivities(IndexDocument document, List<CatalystActivity> catalystActivities) {
        if (catalystActivities == null) return;

        for (CatalystActivity catalystActivity : catalystActivities) {
            setGoTerms(document, catalystActivity.getActivity());
        }
    }

    /**
     * Generic Type, the one we have in the facets.
     *
     * @return type
     */
    private String getType(DatabaseObject databaseObject) {
        if (databaseObject instanceof EntitySet) {
            // Any instance of CandidateSet, DefinedSet, OpenSet
            return "Set";
        } else if (databaseObject instanceof GenomeEncodedEntity) {
            // Any instance of GenomeEncodedEntity is setting the type based on its Reference
            return "Genes and Transcripts";
        } else if (databaseObject instanceof Pathway) {
            // Also covering TopLevelPathway
            return "Pathway";
        } else if (databaseObject instanceof ReactionLikeEvent) {
            // Also covering BlackBoxEvent, (De)Polymerisation, (Failed)Reaction
            return "Reaction";
        } else if (databaseObject instanceof Regulation) {
            // Also covering PositiveRegulation, NegativeRegulation, Requirement
            return "Regulation";
        } else {
            return databaseObject.getSchemaClass();
        }
    }

    private void setRegulatedEntity(IndexDocument document, DatabaseObject regulatedEntity) {
        if (regulatedEntity == null) return;

        // TODO: Set name as the displayName, then we avoid querying for a physical entity and get the regulated entity.
        List<String> names = new ArrayList<>();
        if (regulatedEntity instanceof CatalystActivity) {
            CatalystActivity ca = (CatalystActivity) regulatedEntity;

            // TODO: getPhysicalEntity is wrong. We should invoke List - getCatalizedEvents(). DISCUSS.
            names = ca.getPhysicalEntity().getName();
        } else if (regulatedEntity instanceof Event) {
            Event ev = (Event) regulatedEntity;
            names = ev.getName();
        } else {
            names.add(regulatedEntity.getDisplayName());
        }

        if (names != null && !names.isEmpty()) document.setRegulatedEntity(names.get(0));

        if (StringUtils.isNotEmpty(regulatedEntity.getStId())) {
            document.setRegulatedEntityId(regulatedEntity.getStId());
        } else {
            document.setRegulatedEntityId(regulatedEntity.getDbId().toString());
        }
    }

    private void setAuthorAndReviewed(IndexDocument document, Event event) {
        if (event.getAuthored() == null && event.getReviewed() == null) return;

        // Using a set to avoid duplication
        Set<String> authorAndReviewerNames = new HashSet<>();
        Set<String> authorAndReviewerOrcid = new HashSet<>();

        if (event.getAuthored() != null) {
            authorAndReviewerNames.addAll(event.getAuthored().stream().filter(f -> f.getAuthor() != null).flatMap(e -> e.getAuthor().stream().map(p -> (StringUtils.isEmpty(p.getFirstname()) ? p.getInitial() : p.getFirstname()) + " " + p.getSurname())).collect(Collectors.toList()));
            authorAndReviewerOrcid.addAll(event.getAuthored().stream().filter(f -> f.getAuthor() != null).flatMap(e -> e.getAuthor().stream().filter(p -> p.getOrcidId() != null).map(Person::getOrcidId)).collect(Collectors.toList()));
        }

        if (event.getReviewed() != null) {
            authorAndReviewerNames.addAll(event.getReviewed().stream().filter(f -> f.getAuthor() != null).flatMap(e -> e.getAuthor().stream().map(p -> (StringUtils.isEmpty(p.getFirstname()) ? p.getInitial() : p.getFirstname()) + " " + p.getSurname())).collect(Collectors.toList()));
            authorAndReviewerOrcid.addAll(event.getReviewed().stream().filter(f -> f.getAuthor() != null).flatMap(e -> e.getAuthor().stream().filter(p -> p.getOrcidId() != null).map(Person::getOrcidId)).collect(Collectors.toList()));
        }

        document.setAuthor(authorAndReviewerNames.isEmpty() ? null : authorAndReviewerNames);
        document.setAuthorOrcid(authorAndReviewerOrcid.isEmpty() ? null : authorAndReviewerOrcid);
    }

    private void setRegulator(IndexDocument document, DatabaseObject regulator) {
        if (regulator == null) return;

        List<String> names = new ArrayList<>();
        if (regulator instanceof CatalystActivity) {
            CatalystActivity ca = (CatalystActivity) regulator;
            names = ca.getPhysicalEntity().getName();
        } else if (regulator instanceof Event) {
            Event ev = (Event) regulator;
            names = ev.getName();
        } else if (regulator instanceof PhysicalEntity) {
            PhysicalEntity pe = (PhysicalEntity) regulator;
            names = pe.getName();
        } else {
            names.add(regulator.getDisplayName());
        }

        if (names != null && !names.isEmpty()) document.setRegulator(names.get(0));

        if (StringUtils.isNotEmpty(regulator.getStId())) {
            document.setRegulatorId(regulator.getStId());
        } else {
            document.setRegulatorId(regulator.getDbId().toString());
        }
    }


    /**
     * Keyword rely on document.getName. Make sure you are invoking document.setName before setting the keywords
     *
     * @param document solr document
     */
    private void setKeywords(IndexDocument document) {
        if (keywords == null) return;

        // TODO: Flo says the way it is implemented is not nice. Right we check into a static file with defined vocabulary. Would be nice if we check which reactions are in a bind reaction e.g and then add it as keyword.
        document.setKeywords(keywords.stream().filter(keyword -> document.getName().toLowerCase().contains(keyword.toLowerCase())).collect(Collectors.toList()));
    }

    /**
     * Load a file that is present in classpath
     *
     * @param fileName the file name
     * @return the content file in a List of String
     */
    private List<String> loadFile(String fileName) {
        try {
            List<String> list = new ArrayList<>();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/" + fileName)));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                list.add(line);
            }
            bufferedReader.close();
            logger.debug(list.toString());
            return list;
        } catch (IOException e) {
            logger.error("An error occurred when loading the controlled vocabulary file", e);
        }
        return null;
    }

    @Autowired
    public void setDatabaseObjectService(DatabaseObjectService databaseObjectService) {
        this.databaseObjectService = databaseObjectService;
    }

    @Autowired
    public void setAdvancedDatabaseObjectService(AdvancedDatabaseObjectService advancedDatabaseObjectService) {
        this.advancedDatabaseObjectService = advancedDatabaseObjectService;
    }
}
