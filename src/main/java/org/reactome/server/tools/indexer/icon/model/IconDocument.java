package org.reactome.server.tools.indexer.icon.model;

import org.apache.solr.client.solrj.beans.Field;
import org.reactome.server.tools.indexer.model.IndexDocument;

import java.util.List;
import java.util.Set;

/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */

public class IconDocument extends IndexDocument {

    @Field
    private String iconName;
    @Field
    private List<String> iconCategories;
    @Field
    private String iconCuratorName;
    @Field
    private String iconCuratorOrcidId;
    @Field
    private String iconCuratorUrl;
    @Field
    private String iconDesignerName;
    @Field
    private String iconDesignerOrcidId;
    @Field
    private String iconDesignerUrl;
    @Field
    private List<String> iconReferences;
    @Field
    private Set<String> iconPhysicalEntities;
    @Field
    private List<String> iconEhlds;
    @Field
    private List<String> iconSynonyms;

    public String getIconName() {
        return iconName;
    }

    public void setIconName(String iconName) {
        this.iconName = iconName;
    }

    public List<String> getIconCategories() {
        return iconCategories;
    }

    public void setIconCategories(List<String> iconCategories) {
        this.iconCategories = iconCategories;
    }

    public String getIconCuratorName() {
        return iconCuratorName;
    }

    public void setIconCuratorName(String iconCuratorName) {
        this.iconCuratorName = iconCuratorName;
    }

    public String getIconCuratorOrcidId() {
        return iconCuratorOrcidId;
    }

    public void setIconCuratorOrcidId(String iconCuratorOrcidId) {
        this.iconCuratorOrcidId = iconCuratorOrcidId;
    }

    public String getIconCuratorUrl() {
        return iconCuratorUrl;
    }

    public void setIconCuratorUrl(String iconCuratorUrl) {
        this.iconCuratorUrl = iconCuratorUrl;
    }

    public String getIconDesignerName() {
        return iconDesignerName;
    }

    public void setIconDesignerName(String iconDesignerName) {
        this.iconDesignerName = iconDesignerName;
    }

    public String getIconDesignerOrcidId() {
        return iconDesignerOrcidId;
    }

    public void setIconDesignerOrcidId(String iconDesignerOrcidId) {
        this.iconDesignerOrcidId = iconDesignerOrcidId;
    }

    public String getIconDesignerUrl() {
        return iconDesignerUrl;
    }

    public void setIconDesignerUrl(String iconDesignerUrl) {
        this.iconDesignerUrl = iconDesignerUrl;
    }

    public List<String> getIconReferences() {
        return iconReferences;
    }

    public void setIconReferences(List<String> iconReferences) {
        this.iconReferences = iconReferences;
    }

    public Set<String> getIconPhysicalEntities() {
        return iconPhysicalEntities;
    }

    public void setIconPhysicalEntities(Set<String> iconPhysicalEntities) {
        this.iconPhysicalEntities = iconPhysicalEntities;
    }

    public List<String> getIconEhlds() {
        return iconEhlds;
    }

    public void setIconEhlds(List<String> iconEhlds) {
        this.iconEhlds = iconEhlds;
    }

    public List<String> getIconSynonyms() {
        return iconSynonyms;
    }

    public void setIconSynonyms(List<String> iconSynonyms) {
        this.iconSynonyms = iconSynonyms;
    }
}
