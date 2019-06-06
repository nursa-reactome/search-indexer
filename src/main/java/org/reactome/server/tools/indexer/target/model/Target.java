package org.reactome.server.tools.indexer.target.model;

import org.apache.solr.client.solrj.beans.Field;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */

public class Target {
    @Field
    private String identifier;

    @Field
    private List<String> accessions;

    @Field
    private List<String> geneNames;

    @Field
    private List<String> synonyms;

    @Field
    private String resource;

    public Target(String resource) {
        this.resource = resource;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public List<String> getAccessions() {
        return accessions;
    }

    public void setAccessions(List<String> accessions) {
        if (this.accessions == null) {
            this.accessions = new ArrayList<>();
        }
        for (String accession : accessions) {
            this.accessions.add(accession.trim());
        }
    }

    public List<String> getGeneNames() {
        return geneNames;
    }

    public void setGeneNames(List<String> geneNames) {
        this.geneNames = geneNames;
    }

    public List<String> getSynonyms() {
        return synonyms;
    }

    public void setSynonyms(List<String> synonyms) {
        if (this.synonyms == null) {
            this.synonyms = new ArrayList<>();
        }
        for (String synonym : synonyms) {
            this.synonyms.add(synonym.trim());
        }
    }

    public void addGene(String gn) {
        if (gn == null || gn.isEmpty()){
            throw new IllegalArgumentException("GN is incorrect: " + this.identifier);
        }

        if (this.geneNames == null) {
            this.geneNames = new ArrayList<>();
        }
        this.geneNames.add(gn.trim());
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }
}