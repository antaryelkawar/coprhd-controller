/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.application;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

@XmlAccessorType(XmlAccessType.PROPERTY)
@XmlRootElement(name = "volume_group")
public class VolumeGroupRestRep extends DataObjectRestRep {
    private String description;
    private Set<String> roles;
    private RelatedResourceRep parent;
    private URI sourceStorageSystem;
    private URI sourceVirtualPool;
    private String migrationType;
    private String migrationGroupBy;

    @XmlElement
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @XmlElementWrapper(name = "roles")
    /**
     * Roles of the volume group
     * 
     * @valid none
     */
    @XmlElement(name = "role")
    public Set<String> getRoles() {
        if (roles == null) {
            roles = new HashSet<String>();
        }
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    /**
     * Related parent volume group
     * 
     * @valid none
     */
    @XmlElement(name = "parent")
    @JsonProperty("parent")
    public RelatedResourceRep getParent() {
        return parent;
    }

    public void setParent(RelatedResourceRep parent) {
        this.parent = parent;
    }

    @XmlElement(name = "sourceStorageSystem")
    public URI getSourceStorageSystem() {
        return sourceStorageSystem;
    }

    public void setSourceStorageSystem(URI sourceStorageSystem) {
        this.sourceStorageSystem = sourceStorageSystem;
    }

    @XmlElement(name = "sourceVirtualPool")
    public URI getSourceVirtualPool() {
        return sourceVirtualPool;
    }

    public void setSourceVirtualPool(URI sourceVirtualPool) {
        this.sourceVirtualPool = sourceVirtualPool;
    }

    @XmlElement(name = "migrationType")
    public String getMigrationType() {
        return migrationType;
    }

    public void setMigrationType(String migrationType) {
        this.migrationType = migrationType;
    }

    @XmlElement(name = "migrationGroupBy")
    public String getMigrationGroupBy() {
        return migrationGroupBy;
    }

    public void setMigrationGroupBy(String migrationGroupBy) {
        this.migrationGroupBy = migrationGroupBy;
    }
}
