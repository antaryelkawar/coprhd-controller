/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

import com.emc.storageos.model.valid.EnumType;

@Cf("StorageHADomain")
public class VirtualNASServer extends DiscoveredDataObject {
    // storageSystem, which it belongs
    private URI _storageDeviceURI;
    // Name of the Adapter (Clariion+APM156345420001+SP_A)
    private String _vNASServerName;
    // Serial Number of Adapter
    private String _serialNumber;
    // Slot Number
    private String _slotNumber;
    // Number of Ports
    private String _numberofPorts;
    // Protocol
    private String _protocol;
    // SP_A
    private String _name;

    private String adapterType;

    private StringSet _fileSharingProtocols;

    // Virtual or Physical
    private Boolean _virtual;

    // parent Domain if it is virtual
    private URI _parentDomainURI;

    // Defines the supported port types.
    public static enum NASTechnologyType {
        VDM("VDM"),
        vFILER("vFiler"),
        vServer("vServer"),
        Isilon("Isilon"),
        VNXe("VNXe"),
        UNKNOWN("N/A");

        private String vNASTechType;

        private NASTechnologyType(String haDomType) {
        	vNASTechType = haDomType;
        }

        public String getvNASTechType() {
            return vNASTechType;
        }

        private static NASTechnologyType[] copyValues = values();

        public static String getHADomainTypeName(String name) {
            for (NASTechnologyType type : copyValues) {
                if (type.getvNASTechType().equalsIgnoreCase(name)) {
                    return type.name();
                }
            }
            return UNKNOWN.toString();
        }

    };
    
 // Defines the supported port types.
    public static enum NasState {
        VdmLoaded("Loded"),
        VdmMounted("Mounted"),
        VdmTempUnLoaded("Temporarily unloaded"),
        VdmPermUnLoaded("Permanently unloaded"),
        UNKNOWN("N/A");
        
        private String NasState;

        private NasState(String state) {
        	NasState = state;
        }

        public String getNasState() {
            return NasState;
        }

        private static NasState[] copyValues = values();

        public static String getNasState(String name) {
            for (NasState type : copyValues) {
                if (type.getNasState().equalsIgnoreCase(name)) {
                    return type.name();
                }
            }
            return UNKNOWN.toString();
        }
    };


    private StringMap _metrics;

    /**********************************************
     * AlternateIDIndex - HADomainName *
     * RelationIndex - StorageDevice *
     * *
     **********************************************/

    @RelationIndex(cf = "RelationIndex", type = StorageSystem.class)
    @Name("storageDevice")
    public URI getStorageDeviceURI() {
        return _storageDeviceURI;
    }

    public void setStorageDeviceURI(URI storageDeviceURI) {
        _storageDeviceURI = storageDeviceURI;
        setChanged("storageDevice");
    }

    @Name("haDomainName")
    public String getName() {
        return _vNASServerName;
    }

    public void setName(String haDomainName) {
        _vNASServerName = haDomainName;
        setChanged("haDomainName");
    }

    public void setSerialNumber(String serialNumber) {
        _serialNumber = serialNumber;
        setChanged("serialNumber");
    }

    @Name("serialNumber")
    public String getSerialNumber() {
        return _serialNumber;
    }

    public void setSlotNumber(String slotNumber) {
        _slotNumber = slotNumber;
        setChanged("slotNumber");
    }

    @Name("slotNumber")
    public String getSlotNumber() {
        return _slotNumber;
    }

    public void setNumberofPorts(String numberofPorts) {
        _numberofPorts = numberofPorts;
        setChanged("ports");
    }

    @Name("ports")
    public String getNumberofPorts() {
        return _numberofPorts;
    }

    public void setProtocol(String protocol) {
        _protocol = protocol;
        setChanged("protocol");
    }

    @Name("protocol")
    public String getProtocol() {
        return _protocol;
    }

    public void setFileSharingProtocols(StringSet fileSharingProtocols) {
        _fileSharingProtocols = fileSharingProtocols;
        setChanged("fileSharingProtocols");
    }

    @Name("fileSharingProtocols")
    public StringSet getFileSharingProtocols() {
        return _fileSharingProtocols;
    }

    public void setAdapterName(String name) {
        this._name = name;
        setChanged("adapterName");
    }

    @Name("adapterName")
    public String getAdapterName() {
        return _name;
    }

    @EnumType(NASTechnologyType.class)
    @Name("adapterType")
    public String getAdapterType() {
        return adapterType;
    }

    public void setAdapterType(String type) {
        this.adapterType = type;
        setChanged("adapterType");
    }

    @Name("virtual")
    public Boolean getVirtual() {
        return (_virtual != null) && _virtual;
    }

    public void setVirtual(Boolean virtual) {
        this._virtual = virtual;
        setChanged("virtual");
    }

    // @RelationIndex(cf = "RelationIndex", type = StorageHADomain.class)
    @Name("parentHADomain")
    public URI getParentHADomainURI() {
        return _parentDomainURI;
    }

    public void setParentHADomainURI(URI parentDomainURI) {
        _parentDomainURI = parentDomainURI;
        setChanged("parentHADomain");
    }

    @Name("metrics")
    public StringMap getMetrics() {
        if (_metrics == null) {
            _metrics = new StringMap();
        }
        return _metrics;
    }

    public void setMetrics(StringMap metrics) {
        this._metrics = metrics;
        setChanged("metrics");
    }

}
