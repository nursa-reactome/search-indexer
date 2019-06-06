package org.reactome.server.tools.indexer.impl;

import org.apache.commons.lang.StringUtils;
import org.reactome.server.graph.domain.model.Person;
import org.reactome.server.graph.domain.result.PersonAuthorReviewer;
import org.reactome.server.tools.indexer.model.IndexDocument;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */
@Service
class PersonDocumentBuilder {
    private static final String TYPE = "Person";

    IndexDocument createPersonSolrDocument(PersonAuthorReviewer par) {
        IndexDocument document = new IndexDocument();
        Person person = par.getPerson();
        document.setDbId(person.getDbId()+"");
        document.setName(getName(person));
        document.setType(TYPE);
        document.setExactType(TYPE);
        document.setAuthoredPathways(par.getAuthoredPathways());
        document.setAuthoredReactions(par.getAuthoredReactions());
        document.setReviewedPathways(par.getReviewedPathways());
        document.setReviewedReactions(par.getReviewedReactions());
        document.setOrcidId(person.getOrcidId());
        document.setSpecies(Collections.singletonList("Entries without species"));
        return document;
    }

    private String getName(Person person) {
        if (StringUtils.isNotEmpty(person.getFirstname()) && StringUtils.isNotEmpty(person.getSurname())) {
            return person.getFirstname() + " " + person.getSurname();
        }
        return person.getDisplayName();
    }
}
