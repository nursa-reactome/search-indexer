package org.reactome.server.tools.indexer.icon.model;

import javax.xml.bind.annotation.XmlValue;
import java.util.Objects;

/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */

public class Category implements Comparable<Category>{

    private String name;

    public Category() {
    }

    public Category(String name) {
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
        Category category = (Category) o;
        return Objects.equals(this.name, category.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public int compareTo(Category o) {
        return this.getName().compareTo(o.getName());
    }
}
