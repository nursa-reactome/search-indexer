package org.reactome.server.tools.indexer.icon.exporter;

import java.util.Objects;

/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */

public class SimpleIcon implements Comparable<SimpleIcon>{

    private String identifier;
    private String stId;
    private String name;

    SimpleIcon(String identifier, String stId, String name) {
        this.identifier = identifier;
        this.stId = stId;
        this.name = name;
    }

    String getIdentifier() {
        return identifier;
    }

    void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    String getStId() {
        return stId;
    }

    void setStId(String stId) {
        this.stId = stId;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SimpleIcon that = (SimpleIcon) o;
        return Objects.equals(identifier, that.identifier) &&
                Objects.equals(stId, that.stId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier, stId);
    }

    @Override
    public int compareTo(SimpleIcon o) {
        return this.stId.compareTo(o.getStId());
    }
}
