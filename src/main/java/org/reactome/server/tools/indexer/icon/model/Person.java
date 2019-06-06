package org.reactome.server.tools.indexer.icon.model;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlValue;

/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */

public class Person {
    private String name;
    private String role;
    private String url;
    private String orcidId;

    public String getName() {
        return name;
    }

    @XmlValue
    public void setName(String name) {
        this.name = name;
    }

    public String getRole() {
        return role;
    }

    @XmlAttribute
    public void setRole(String role) {
        this.role = role;
    }

    public String getUrl() {
        return url;
    }

    @XmlAttribute
    public void setUrl(String url) {
        this.url = url;
    }

    public String getOrcidId() {
        return orcidId;
    }

    @XmlAttribute(name = "orcid")
    public void setOrcidId(String orcidId) {
        this.orcidId = orcidId;
    }
}
