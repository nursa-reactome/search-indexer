package org.reactome.server.tools.indexer.icon.impl;

import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.reactome.server.tools.indexer.icon.model.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */
@Service
class IconDocumentBuilder {

    private String solrCore;
    private SolrClient solrClient;

    public IconDocumentBuilder() {
    }

    IconDocumentBuilder(String solrCore, SolrClient solrClient) {
        this.solrCore = solrCore;
        this.solrClient = solrClient;
    }

    IconDocument createIconSolrDocument(Icon icon) {
        IconDocument document = new IconDocument();
        document.setDbId(icon.getId());
        document.setStId(icon.getStId());
        document.setSummation(icon.getDescription());
        document.setName(icon.getName()); // solr applies highlighting and we may need the name without it. use iconName to get plain name
        document.setIconName(icon.getName()); // plain name, no highlighting on it
        document.setIconCategories(icon.getCategories().stream().sorted().map(Category::getName).collect(Collectors.toList()));
        document.setType(icon.getType());
        document.setExactType(icon.getType());
        document.setSpecies(Collections.singletonList(icon.getSpecies()));
        document.setIconEhlds(icon.getEhlds());

        if (icon.getSynonyms() != null) {
            document.setIconSynonyms(icon.getSynonyms().stream().map(Synonym::getName).collect(Collectors.toList()));
        }

        if (icon.getReferences() != null) {
            List<String> references = new ArrayList<>();
            for (Reference reference : icon.getReferences()) {
                if(reference.getId().contains(reference.getDb())) { // db as part of the id
                    String id = reference.getId();
                    id = id.replace(reference.getDb() + ":", ""); //remove db from the id and save id only
                    references.add(id);
                    references.add(reference.getId()); // also save whole id (db:id)
                } else {
                    references.add(reference.getId()); // id
                    references.add(reference.toString()); //db:id
                }
            }
            document.setIconReferences(references);
            document.setIconPhysicalEntities(getIconPhysicalEntities(references));
        }

        List<Person> iconPerson = icon.getPerson();
        for (Person person : iconPerson) {
            if (person.getRole().equalsIgnoreCase("curator")) {
                document.setIconCuratorName(person.getName());
                document.setIconCuratorOrcidId(person.getOrcidId());
                document.setIconCuratorUrl(person.getUrl());
            } else if (person.getRole().equalsIgnoreCase("designer")) {
                document.setIconDesignerName(person.getName());
                document.setIconDesignerOrcidId(person.getOrcidId());
                document.setIconDesignerUrl(person.getUrl());
            }
        }

        return document;
    }

    /**
     * Based on the annotated references in the XMLs
     * we will search them in the Reactome core and retrieve the ST_ID, NAME, EXACT_TYPE, COMPARTMENT.
     *
     * @return stId(s) mapping the icon and the physical entities
     */
    private Set<String> getIconPhysicalEntities(List<String> references) {
        Set<String> ret = new HashSet<>();
        SolrQuery query = new SolrQuery();
        query.setRequestHandler("/iconPEStId");
        query.setQuery(StringUtils.join(references, " OR "));
        query.setRows(300);
        query.setFields("stId, name, exactType, compartmentName");
        try {
            QueryResponse response = solrClient.query(solrCore, query);
            SolrDocumentList solrDocument = response.getResults();
            if (solrDocument != null && !solrDocument.isEmpty()) {
                /* if query returns more than 300 docs, query all of them */
                if (solrDocument.getNumFound() > 300) {
                    query.setRows(((Long) solrDocument.getNumFound()).intValue());
                    response = solrClient.query(solrCore, query);
                    solrDocument = response.getResults();
                }
                solrDocument.forEach(doc -> {
                    String stId = (String) doc.getFieldValue("stId");
                    String name = (String) doc.getFieldValue("name");
                    String type = (String) doc.getFieldValue("exactType");
                    String compartments = null; // interactors don't have compartment
                    Collection<Object> compartmentsList = doc.getFieldValues("compartmentName");
                    if (compartmentsList != null && !compartmentsList.isEmpty()) {
                        compartments = compartmentsList.stream().map(Object::toString).collect(Collectors.joining(", "));
                    }

                    // stIds is multivalued field, additionally we add name and type.
                    ret.add(stId + "#" + type + "#" + name + "#" + compartments);
                });
            }
        } catch (SolrServerException | IOException e) {
            // nothing here
        }
        return ret;
    }
}
