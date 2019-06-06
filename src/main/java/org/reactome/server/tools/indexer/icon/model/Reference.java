package org.reactome.server.tools.indexer.icon.model;

import javax.xml.bind.annotation.XmlElement;

/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */

public class Reference {
    private String db;
    private String id;

    public String getDb() {
        return db;
    }

    @XmlElement
    public void setDb(String db) {
        this.db = db;
    }

    public String getId() {
        return id;
    }

    @XmlElement
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        if (id.contains(db)) {
            return id;
        } else {
            return String.join(":", db, id);
        }
    }
}

