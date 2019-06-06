package org.reactome.server.tools.indexer.icon.model;

import javax.xml.bind.annotation.*;
import java.util.List;

/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */

@XmlRootElement(name = "metadata")
@XmlType(propOrder={"categories","person","name","description","info","references","synonyms","skip"})
public class Icon {
    private String id;
    private String stId;
    private String name;
    private String type = "Icon";
    private String species;
    private String description;
    private String info = "https://reactome.org/icon-info";
    private List<Category> categories;
    private List<Reference> references;
    private List<Person> person;
    private List<String> ehlds;
    private List<Synonym> synonyms;
    private Boolean skip;

    public String getId() {
        return id;
    }

    @XmlTransient
    public void setId(String id) {
        this.id = id;
    }

    public String getStId() {
        return stId;
    }

    @XmlTransient
    public void setStId(String stId) {
        this.stId = stId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlTransient
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @XmlTransient
    public String getSpecies() {
        return species;
    }

    public void setSpecies(String species) {
        this.species = species;
    }

    public String getDescription() {
        return description;
    }

    @XmlElement
    public void setDescription(String description) {
        this.description = description;
    }

    public String getInfo() {
        return info;
    }

    @XmlElement
    public void setInfo(String info) {
        this.info = info;
    }

    public List<Category> getCategories() {
        return categories;
    }

    @XmlElementWrapper(name = "categories")
    @XmlElement(name = "category")
    public void setCategories(List<Category> categories) {
        this.categories = categories;
    }

    public List<Reference> getReferences() {
        return references;
    }

    @XmlElementWrapper(name = "references")
    @XmlElement(name = "reference")
    public void setReferences(List<Reference> references) {
        this.references = references;
    }

    public List<Person> getPerson() {
        return person;
    }

    @XmlElement(name = "person")
    public void setPerson(List<Person> person) {
        this.person = person;
    }

    @XmlTransient
    public List<String> getEhlds() {
        return ehlds;
    }

    public void setEhlds(List<String> ehlds) {
        this.ehlds = ehlds;
    }

    public List<Synonym> getSynonyms() {
        return synonyms;
    }

    @XmlElementWrapper(name = "synonyms")
    @XmlElement(name = "synonym")
    public void setSynonyms(List<Synonym> synonyms) {
        this.synonyms = synonyms;
    }

    @XmlElement
    public Boolean isSkip() {
        return skip;
    }

    public void setSkip(Boolean skip) {
        this.skip = skip;
    }
}
