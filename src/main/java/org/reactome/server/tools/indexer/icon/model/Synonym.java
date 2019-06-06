package org.reactome.server.tools.indexer.icon.model;

import javax.xml.bind.annotation.XmlValue;
import java.util.Objects;

/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */

public class Synonym implements Comparable<Synonym> {

    private String name;

    public Synonym() {
    }

    public Synonym(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @XmlValue
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Synonym synonym = (Synonym) o;
        return Objects.equals(this.name, synonym.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "Synonym {" +
                "name='" + name + '\'' +
                '}';
    }

    @Override
    public int compareTo(Synonym o) {
        return this.getName().compareTo(o.getName());
    }
}
