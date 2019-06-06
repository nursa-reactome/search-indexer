package org.reactome.server.tools.indexer.impl;

import org.apache.commons.lang3.StringUtils;
import org.reactome.server.graph.domain.model.*;
import org.reactome.server.graph.domain.result.DiagramOccurrences;
import org.reactome.server.graph.service.InteractionsService;
import org.reactome.server.tools.indexer.model.IndexDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */
@Service
class InteractorDocumentBuilder {

    private static final String TYPE = "Interactor";

    private InteractionsService interactionsService;


    IndexDocument createInteractorSolrDocument(ReferenceEntity interactor) {
        IndexDocument document = new IndexDocument();
        document.setDbId(interactor.getIdentifier()); // For interactors, dbId is the accession.
        document.setStId(interactor.getIdentifier()); // For interactors, stId is the accession.
        document.setName(getName(interactor)); // is mandatory
        document.setType(TYPE);
        document.setExactType(TYPE);

        document.setReferenceName(getReferenceName(interactor));
        document.setReferenceGeneNames(getGeneName(interactor));
        document.setReferenceSynonyms(getSecondaryIdentifier(interactor));
        document.setReferenceIdentifiers(Collections.singletonList(interactor.getIdentifier()));
        document.setReferenceURL(interactor.getUrl());

        document.setDatabaseName(interactor.getDatabaseName());
        String speciesName = getSpeciesName(interactor);
        document.setSpecies(Collections.singletonList(speciesName));

        if (interactor instanceof ReferenceIsoform) {
            String variantIdentifier = getVariantIdentifier(interactor);
            if (variantIdentifier != null && !variantIdentifier.isEmpty()) {
                // For interactors, dbId is the accession, if Isoform then get variantIdentifier
                document.setDbId(variantIdentifier);
                document.setStId(variantIdentifier);
                document.setReferenceIdentifiers(Collections.singletonList(variantIdentifier));
            }
        }

        setFireworksSpecies(document, interactor);
        setLowerLevelPathways(document, interactor, speciesName);
        setDiagramOccurrences(document, interactor);

        return document;
    }

    private void setDiagramOccurrences(IndexDocument document, ReferenceEntity re){
        String identifier = getVariantIdentifier(re);
        if (identifier == null || identifier.isEmpty()) identifier = re.getIdentifier();

        Collection<DiagramOccurrences> dgoc = interactionsService.getDiagramOccurrences(identifier);
        if (dgoc == null || dgoc.isEmpty()) return;

        List<String> diagrams = new ArrayList<>();
        List<String> occurrences = new ArrayList<>();
        //noinspection Duplicates
        for (DiagramOccurrences diagramOccurrence : dgoc) {
            diagrams.add(diagramOccurrence.getDiagram().getStId());

            String occurr = diagramOccurrence.getDiagram().getStId() + ":" + Boolean.toString(diagramOccurrence.isInDiagram());
            if (diagramOccurrence.getOccurrences() != null && !diagramOccurrence.getOccurrences().isEmpty()) {
                occurr = occurr + ":" + StringUtils.join(diagramOccurrence.getOccurrences().stream().map(DatabaseObject::getStId).collect(Collectors.toList()), ",");
            } else {
                occurr = occurr + ":#"; // no occurrences, using one char so less bytes in the solr index
            }
            if (diagramOccurrence.getInteractsWith() != null && !diagramOccurrence.getInteractsWith().isEmpty()) {
                occurr = occurr + ":" + StringUtils.join(diagramOccurrence.getInteractsWith().stream().map(DatabaseObject::getStId).collect(Collectors.toList()), ",");
            } else {
                occurr = occurr + ":#"; // empty interactsWith, using one char so less bytes in the solr index
            }
            occurrences.add(occurr);
        }
        document.setDiagrams(diagrams);
        document.setOccurrences(occurrences);
    }

    private void setLowerLevelPathways(IndexDocument document, ReferenceEntity re, String speciesName) {
        String identifier = getVariantIdentifier(re);
        if (identifier == null || identifier.isEmpty()) identifier = re.getIdentifier();

        Collection<Pathway> pathways = interactionsService.getLowerLevelPathways(identifier, speciesName);
        if (pathways == null || pathways.isEmpty()) return;

        document.setLlps(pathways.stream().map(DatabaseObject::getStId).collect(Collectors.toList()));
    }

    @SuppressWarnings("unchecked")
    private List<String> getSecondaryIdentifier(DatabaseObject databaseObject) {
        try {
            return (List<String>) databaseObject.getClass().getMethod("getSecondaryIdentifier").invoke(databaseObject);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            // Nothing here
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private List<String> getGeneName(DatabaseObject databaseObject) {
        try {
            return (List<String>) databaseObject.getClass().getMethod("getGeneName").invoke(databaseObject);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            // Nothing here
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String getVariantIdentifier(DatabaseObject databaseObject) {
        try {
            // in cases where databaseObject is instanceof ReferenceIsoform, use variantIdentifier as the main identifier
            return (String) databaseObject.getClass().getMethod("getVariantIdentifier").invoke(databaseObject);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            // Nothing here
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String getSpeciesName(DatabaseObject databaseObject) {
        try {
            return ((Species) databaseObject.getClass().getMethod("getSpecies").invoke(databaseObject)).getDisplayName();
        } catch (NullPointerException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            // Nothing here
        }
        return "Entries without species";
    }

    private String getName(ReferenceEntity interactor) {
        List<String> names = interactor.getName();
        if (names != null && !names.isEmpty()) return names.get(0);

        if (interactor instanceof ReferenceIsoform) return ((ReferenceIsoform) interactor).getVariantIdentifier();

        return interactor.getIdentifier();
    }

    private String getReferenceName(ReferenceEntity interactor) {
        List<String> names = interactor.getName();
        if (names != null && !names.isEmpty()) return names.get(0);

        return null;
    }

    @SuppressWarnings("Duplicates")
    private void setFireworksSpecies(IndexDocument document, ReferenceEntity interactor) {
        Set<String> fireworksSpecies = new HashSet<>();
        try {
            Method getSpecies = interactor.getClass().getMethod("getSpecies");
            Object species = getSpecies.invoke(interactor);
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

        // Regulation and Other Entities may not have (fireworks)species and solr won't be able to find them
        // in the Fireworks (filter query fireworksSpecies)
        if(fireworksSpecies.isEmpty()) {
            fireworksSpecies.add("Entries without species");
        }

        document.setFireworksSpecies(fireworksSpecies);
    }

    @Autowired
    public void setInteractionsService(InteractionsService interactionsService) {
        this.interactionsService = interactionsService;
    }
}
