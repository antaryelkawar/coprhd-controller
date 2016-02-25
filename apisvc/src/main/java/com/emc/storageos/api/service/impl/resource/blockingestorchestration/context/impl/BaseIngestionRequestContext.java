/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedConsistencyGroup;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;

/**
 * Base implementation of IngestionRequestContext.
 * 
 * @see IngestionRequestContext
 */
public class BaseIngestionRequestContext implements IngestionRequestContext {

    private static Logger _logger = LoggerFactory.getLogger(BaseIngestionRequestContext.class);
    private DbClient _dbClient;

    private Iterator<URI> _unManagedVolumeUrisToProcessIterator;
    private Map<String, VolumeIngestionContext> _processedUnManagedVolumeMap;

    private VirtualPool _vpool;
    private VirtualArray _virtualArray;
    private Project _project;
    private TenantOrg _tenant;
    private String _vplexIngestionMethod;
    private Map<String, StringBuffer> _taskStatusMap;

    private Map<String, StorageSystem> _storageSystemCache;
    private List<URI> _exhaustedStorageSystems;
    private List<URI> _exhaustedPools;

    private List<UnManagedVolume> _unManagedVolumesToBeDeleted;
    private Map<String, BlockObject> _blockObjectsToBeCreatedMap;
    private Map<String, List<DataObject>> _dataObjectsToBeUpdatedMap;
    private Map<String, List<DataObject>> _dataObjectsToBeCreatedMap;

    private VolumeIngestionContext _currentVolumeIngestionContext;
    private URI _currentUnManagedVolumeUri;

    // export ingestion related items
    private boolean _exportGroupCreated = false;
    private ExportGroup _exportGroup;
    private URI _host;
    private URI _cluster;
    private List<Initiator> _deviceInitiators;
    List<BlockObject> _objectsIngestedByExportProcessing;

    /**
     * Constructor.
     * 
     * @param dbClient a reference to the database client
     * @param unManagedVolumeUrisToProcess the UnmanagedVolumes to be processed by this request
     * @param vpool the VirtualPool to use for ingestion from the client request
     * @param virtualArray the VirtualArray to use for ingestion from the client request
     * @param project the Project to use for ingestion from the client request
     * @param tenant the TenantOrg to use for ingestion from the client request
     * @param vplexIngestionMethod the VPLEX ingestion method from the client request
     */
    public BaseIngestionRequestContext(DbClient dbClient, List<URI> unManagedVolumeUrisToProcess, VirtualPool vpool,
            VirtualArray virtualArray, Project project, TenantOrg tenant, String vplexIngestionMethod) {
        _dbClient = dbClient;
        _unManagedVolumeUrisToProcessIterator = unManagedVolumeUrisToProcess.iterator();
        _vpool = vpool;
        _virtualArray = virtualArray;
        _project = project;
        _tenant = tenant;
        _vplexIngestionMethod = vplexIngestionMethod;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Iterator#hasNext()
     */
    @Override
    public boolean hasNext() {
        return _unManagedVolumeUrisToProcessIterator.hasNext();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Iterator#next()
     */
    @Override
    public UnManagedVolume next() {
        _currentUnManagedVolumeUri = _unManagedVolumeUrisToProcessIterator.next();
        UnManagedVolume currentVolume = _dbClient.queryObject(UnManagedVolume.class, _currentUnManagedVolumeUri);
        if (null != currentVolume) {
            this.setCurrentUnmanagedVolume(currentVolume);
        }
        return currentVolume;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Iterator#remove()
     */
    @Override
    public void remove() {
        _unManagedVolumeUrisToProcessIterator.remove();
    }

    /**
     * Instantiates the correct VolumeIngestionContext type for the
     * current UnManagedVolume being processed, based on the UnManagedVolume type.
     */
    protected static class VolumeIngestionContextFactory {

        public static VolumeIngestionContext getVolumeIngestionContext(UnManagedVolume unManagedVolume,
                DbClient dbClient, IngestionRequestContext parentRequestContext) {
            if (null == unManagedVolume) {
                return null;
            }

            // order is actually important here because a VPLEX volume could also be RP-enabled
            if (VolumeIngestionUtil.isRpVplexVolume(unManagedVolume)) {
                return new RpVplexVolumeIngestionContext(unManagedVolume, dbClient, parentRequestContext);
            } else if (VolumeIngestionUtil.isVplexVolume(unManagedVolume)) {
                return new VplexVolumeIngestionContext(unManagedVolume, dbClient, parentRequestContext);
            } else if (VolumeIngestionUtil.checkUnManagedResourceIsRecoverPointEnabled(unManagedVolume)) {
                return new RecoverPointVolumeIngestionContext(unManagedVolume, dbClient, parentRequestContext);
            } else {
                return new BlockVolumeIngestionContext(unManagedVolume, dbClient);
            }
        }

    }

    /**
     * Private setter for the current UnManagedVolume, used by this class' implementation
     * of Iterator<UnManagedVolume>. This method will set the current VolumeIngestionContext.
     * 
     * @param unManagedVolume the UnManagedVolume to set
     */
    private void setCurrentUnmanagedVolume(UnManagedVolume unManagedVolume) {
        _currentVolumeIngestionContext =
                VolumeIngestionContextFactory.getVolumeIngestionContext(unManagedVolume, _dbClient, this);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getCurrentUnmanagedVolume()
     */
    @Override
    public UnManagedVolume getCurrentUnmanagedVolume() {
        if (_currentVolumeIngestionContext == null) {
            return null;
        }

        return _currentVolumeIngestionContext.getUnmanagedVolume();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getCurrentUnManagedVolumeUri()
     */
    @Override
    public URI getCurrentUnManagedVolumeUri() {
        return _currentUnManagedVolumeUri;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getVolumeContext()
     */
    @Override
    public VolumeIngestionContext getVolumeContext() {
        return _currentVolumeIngestionContext;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getVolumeContext(java.lang.
     * String)
     */
    @Override
    public VolumeIngestionContext getVolumeContext(String unmanagedVolumeGuid) {
        if (getProcessedUnManagedVolumeMap().get(unmanagedVolumeGuid) != null) {
            return getProcessedUnManagedVolumeMap().get(unmanagedVolumeGuid);
        }
        return getVolumeContext();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getStorageSystem()
     */
    @Override
    public StorageSystem getStorageSystem() {

        URI storageSystemUri = getCurrentUnmanagedVolume().getStorageSystemUri();
        StorageSystem storageSystem = getStorageSystemCache().get(storageSystemUri.toString());
        if (null == storageSystem) {
            storageSystem = _dbClient.queryObject(StorageSystem.class, storageSystemUri);
            getStorageSystemCache().put(storageSystemUri.toString(), storageSystem);
        }

        return storageSystem;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getVpool()
     */
    @Override
    public VirtualPool getVpool(UnManagedVolume unmanagedVolume) {
        return _vpool;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getVarray()
     */
    @Override
    public VirtualArray getVarray(UnManagedVolume unmanagedVolume) {
        return _virtualArray;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getProject()
     */
    @Override
    public Project getProject() {
        return _project;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getTenant()
     */
    @Override
    public TenantOrg getTenant() {
        return _tenant;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getVplexIngestionMethod()
     */
    @Override
    public String getVplexIngestionMethod() {
        return _vplexIngestionMethod;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getStorageSystemCache()
     */
    @Override
    public Map<String, StorageSystem> getStorageSystemCache() {
        if (null == _storageSystemCache) {
            _storageSystemCache = new HashMap<String, StorageSystem>();
        }

        return _storageSystemCache;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getExhaustedStorageSystems()
     */
    @Override
    public List<URI> getExhaustedStorageSystems() {
        if (null == _exhaustedStorageSystems) {
            _exhaustedStorageSystems = new ArrayList<URI>();
        }

        return _exhaustedStorageSystems;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getExhaustedPools()
     */
    @Override
    public List<URI> getExhaustedPools() {
        if (null == _exhaustedPools) {
            _exhaustedPools = new ArrayList<URI>();
        }

        return _exhaustedPools;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getUnManagedVolumesToBeDeleted
     * ()
     */
    @Override
    public List<UnManagedVolume> getUnManagedVolumesToBeDeleted() {
        if (null == _unManagedVolumesToBeDeleted) {
            _unManagedVolumesToBeDeleted = new ArrayList<UnManagedVolume>();
        }

        return _unManagedVolumesToBeDeleted;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getObjectsToBeCreatedMap()
     */
    @Override
    public Map<String, BlockObject> getBlockObjectsToBeCreatedMap() {
        if (null == _blockObjectsToBeCreatedMap) {
            _blockObjectsToBeCreatedMap = new HashMap<String, BlockObject>();
        }

        return _blockObjectsToBeCreatedMap;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getDataObjectsToBeCreatedMap()
     */
    @Override
    public Map<String, List<DataObject>> getDataObjectsToBeCreatedMap() {
        if (null == _dataObjectsToBeCreatedMap) {
            _dataObjectsToBeCreatedMap = new HashMap<String, List<DataObject>>();
        }

        return _dataObjectsToBeCreatedMap;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getObjectsToBeUpdatedMap()
     */
    @Override
    public Map<String, List<DataObject>> getDataObjectsToBeUpdatedMap() {
        if (null == _dataObjectsToBeUpdatedMap) {
            _dataObjectsToBeUpdatedMap = new HashMap<String, List<DataObject>>();
        }

        return _dataObjectsToBeUpdatedMap;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getTaskStatusMap()
     */
    @Override
    public Map<String, StringBuffer> getTaskStatusMap() {
        if (null == _taskStatusMap) {
            _taskStatusMap = new HashMap<String, StringBuffer>();
        }

        return _taskStatusMap;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getProcessedUnManagedVolumeMap
     * ()
     */
    @Override
    public Map<String, VolumeIngestionContext> getProcessedUnManagedVolumeMap() {
        if (null == _processedUnManagedVolumeMap) {
            _processedUnManagedVolumeMap = new HashMap<String, VolumeIngestionContext>();
        }

        return _processedUnManagedVolumeMap;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getProcessedUnManagedVolume
     * (java.lang.String)
     */
    @Override
    public UnManagedVolume getProcessedUnManagedVolume(String nativeGuid) {
        VolumeIngestionContext volumeContext = getProcessedUnManagedVolumeMap().get(nativeGuid);
        if (null != volumeContext) {
            return volumeContext.getUnmanagedVolume();
        }

        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getProcessedBlockObject(java
     * .lang.String)
     */
    @Override
    public BlockObject getProcessedBlockObject(String unmanagedVolumeGuid) {
        String objectGUID = unmanagedVolumeGuid.replace(VolumeIngestionUtil.UNMANAGEDVOLUME, VolumeIngestionUtil.VOLUME);
        return getBlockObjectsToBeCreatedMap().get(objectGUID);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getProcessedVolumeContext(java
     * .lang.String)
     */
    @Override
    public VolumeIngestionContext getProcessedVolumeContext(String nativeGuid) {
        return getProcessedUnManagedVolumeMap().get(nativeGuid);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getErrorMessagesForVolume(java
     * .lang.String)
     */
    @Override
    public List<String> getErrorMessagesForVolume(String nativeGuid) {
        if (getVolumeContext(nativeGuid) != null) {
            return getVolumeContext(nativeGuid).getErrorMessages();
        }

        // log a warning, but still return an empty List to avoid potential null pointers.
        // the list will not be attached to the given native GUID in any way and will probably
        // be dereferenced without ever being used to generate an error message.
        _logger.warn("no unmanaged volume context was found for native GUID {}. returning an empty list.", nativeGuid);
        return new ArrayList<String>();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getObjectsIngestedByExportProcessing
     * ()
     */
    @Override
    public List<BlockObject> getObjectsIngestedByExportProcessing() {
        if (null == _objectsIngestedByExportProcessing) {
            _objectsIngestedByExportProcessing = new ArrayList<BlockObject>();
        }

        return _objectsIngestedByExportProcessing;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#isExportGroupCreated()
     */
    @Override
    public boolean isExportGroupCreated() {
        return _exportGroupCreated;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#setExportGroupCreated(boolean)
     */
    @Override
    public void setExportGroupCreated(boolean exportGroupCreated) {
        this._exportGroupCreated = exportGroupCreated;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getExportGroup()
     */
    @Override
    public ExportGroup getExportGroup() {
        return _exportGroup;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#setExportGroup(com.emc.storageos
     * .db.client.model.ExportGroup)
     */
    @Override
    public void setExportGroup(ExportGroup exportGroup) {
        this._exportGroup = exportGroup;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getHost()
     */
    @Override
    public URI getHost() {
        return _host;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#setHost(java.net.URI)
     */
    @Override
    public void setHost(URI host) {
        this._host = host;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getCluster()
     */
    @Override
    public URI getCluster() {
        return _cluster;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#setCluster(java.net.URI)
     */
    @Override
    public void setCluster(URI cluster) {
        this._cluster = cluster;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getDeviceInitiators()
     */
    @Override
    public List<Initiator> getDeviceInitiators() {
        return _deviceInitiators;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#setDeviceInitiators(java.util
     * .List)
     */
    @Override
    public void setDeviceInitiators(List<Initiator> deviceInitiators) {
        this._deviceInitiators = deviceInitiators;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#findCreatedBlockObject(java.lang.
     * String)
     */
    @Override
    public BlockObject findCreatedBlockObject(String nativeGuid) {

        if (URIUtil.isValid(nativeGuid)) {
            _logger.warn("the nativeGuid {} argument is a valid URI. caller maybe meant to use findCreatedBlockObject(URI)?", nativeGuid);
        }

        BlockObject blockObject = getBlockObjectsToBeCreatedMap().get(nativeGuid);

        // if the block object wasn't found in this context's created object map
        // then we will do a deep dive and see if there are any created object maps
        // nested below (for example, for vplex backend or recover point ingestion)
        if (blockObject == null) {
            _logger.info("a block object for native GUID {} wasn't found at the top, digging deeper...", nativeGuid);
            VolumeIngestionContext currentVolumeContext = getVolumeContext();
            if (currentVolumeContext instanceof IngestionRequestContext) {
                _logger.info("looking for block object with native GUID {} in the current volume context...", nativeGuid);
                blockObject = ((IngestionRequestContext) currentVolumeContext).getBlockObjectsToBeCreatedMap().get(nativeGuid);
                if (blockObject != null) {
                    _logger.info("\tfound block object: " + blockObject.forDisplay());
                    return blockObject;
                }
            }
        }

        if (blockObject == null){
            _logger.info("a block object for native GUID {} still not found, checking all volume contexts...", nativeGuid);
            for (VolumeIngestionContext volumeContext : this.getProcessedUnManagedVolumeMap().values()) {
                if (volumeContext instanceof IngestionRequestContext) {
                    _logger.info("the volume context for {} also contains created objects, searching...", 
                            volumeContext.getUnmanagedVolume().getNativeGuid());
                    blockObject = ((IngestionRequestContext) volumeContext).getBlockObjectsToBeCreatedMap().get(nativeGuid);
                    if (blockObject != null) {
                        _logger.info("\tfound block object: " + blockObject.forDisplay());
                        return blockObject;
                    }
                }
            }
        }

        if (blockObject == null) {
            _logger.info("could not find a block object for native GUID {} anywhere.", nativeGuid);
        }
        return blockObject;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#findCreatedBlockObject(java.net.URI)
     */
    @Override
    public BlockObject findCreatedBlockObject(URI uri) {

        if (!URIUtil.isValid(uri)) {
            _logger.warn("URI ({}) for findCreatedBlockObject is null or invalid", uri);
            return null;
        }

        for (BlockObject bo : getBlockObjectsToBeCreatedMap().values()) {
            if (bo.getId() != null && uri.toString().equals(bo.getId().toString())) {
                _logger.info("\tfound block object: " + bo.forDisplay());
                return bo;
            }
        }

        _logger.info("a block object for uri {} wasn't found at the top, digging deeper...", uri);
        VolumeIngestionContext currentVolumeContext = getVolumeContext();
        if (currentVolumeContext != null && currentVolumeContext instanceof IngestionRequestContext) {
            _logger.info("looking for block object with uri {} in the current volume context...", uri);
            for (BlockObject bo : ((IngestionRequestContext) currentVolumeContext).getBlockObjectsToBeCreatedMap().values()) {
                if (bo.getId() != null && uri.toString().equals(bo.getId().toString())) {
                    _logger.info("\tfound block object: " + bo.forDisplay());
                    return bo;
                }
            }
        }

        _logger.info("a block object for uri {} still not found, checking all volume contexts...", uri);
        for (VolumeIngestionContext volumeContext : this.getProcessedUnManagedVolumeMap().values()) {
            if (volumeContext instanceof IngestionRequestContext) {
                for (BlockObject bo : ((IngestionRequestContext) volumeContext).getBlockObjectsToBeCreatedMap().values()) {
                    if (bo.getId() != null && uri.toString().equals(bo.getId().toString())) {
                        _logger.info("\tfound block object: " + bo.forDisplay());
                        return bo;
                    }
                }
            }
        }

        try {
            BlockObject bo = (BlockObject) _dbClient.queryObject(uri);
            if (bo != null) {
                _logger.info("\tfound block object in the database: " + bo.forDisplay());
                return bo;
            }
        } catch (ClassCastException ex) {
            _logger.warn("Failed to find a block object for URI {}: {}", uri, ex.getLocalizedMessage());
        }

        _logger.info("could not find a block object for uri {} anywhere.", uri);
        return null;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#findAllProcessedUnManagedVolumes()
     */
    @Override
    public List<UnManagedVolume> findAllUnManagedVolumesToBeDeleted() {
        _logger.info("assembling a List of all unmanaged volumes to be deleted");

        List<UnManagedVolume> allUnManagedVolumesToBeDeleted = new ArrayList<UnManagedVolume>();
        
        _logger.info("\tadding local unmanaged volumes to be deleted: " + this.getUnManagedVolumesToBeDeleted());
        allUnManagedVolumesToBeDeleted.addAll(this.getUnManagedVolumesToBeDeleted());

        VolumeIngestionContext currentVolumeContext = getVolumeContext();
        if (currentVolumeContext != null && currentVolumeContext instanceof IngestionRequestContext) {
            _logger.info("checking current volume ingestion context {}", 
                    currentVolumeContext.getUnmanagedVolume().forDisplay());
            for (UnManagedVolume unmanagedSubVolume : 
                ((IngestionRequestContext) currentVolumeContext).getUnManagedVolumesToBeDeleted()) {
                _logger.info("\t\tadding current volume context UnManagedVolume {}",unmanagedSubVolume.forDisplay());
                allUnManagedVolumesToBeDeleted.add(unmanagedSubVolume);
            }
        }

        for (VolumeIngestionContext volumeContext : this.getProcessedUnManagedVolumeMap().values()) {
            if (volumeContext instanceof IngestionRequestContext) {
                for (UnManagedVolume unmanagedSubVolume : 
                    ((IngestionRequestContext) volumeContext).getUnManagedVolumesToBeDeleted()) {
                    _logger.info("\t\tadding sub context UnManagedVolume {}",unmanagedSubVolume.forDisplay());
                    allUnManagedVolumesToBeDeleted.add(unmanagedSubVolume);
                }
            }
        }

        return allUnManagedVolumesToBeDeleted;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#findInUpdatedObjects(java.net.URI)
     */
    @Override
    public DataObject findInUpdatedObjects(URI uri) {
        _logger.info("looking everywhere for an already-loaded object to updated with URI " + uri);
        
        for (List<DataObject> objectsToBeUpdated : this.getDataObjectsToBeUpdatedMap().values()) {
            for (DataObject o : objectsToBeUpdated) {
                if (o.getId().equals(uri)) {
                    _logger.info("\tfound data object in base ingestion request context: " + o.forDisplay());
                    return o;
                }
            }
        }

        VolumeIngestionContext currentVolumeContext = getVolumeContext();
        if (currentVolumeContext != null && currentVolumeContext instanceof IngestionRequestContext) {
            _logger.info("checking current volume ingestion context {}", 
                    currentVolumeContext.getUnmanagedVolume().forDisplay());
            for (List<DataObject> objectsToBeUpdated : 
                ((IngestionRequestContext) currentVolumeContext).getDataObjectsToBeUpdatedMap().values()) {
                for (DataObject o : objectsToBeUpdated) {
                    if (o.getId().equals(uri)) {
                        _logger.info("\tfound data object {} in volume ingestion context {}", 
                                o.forDisplay(), currentVolumeContext.getUnmanagedVolume().forDisplay());
                        return o;
                    }
                }
            }
        }

        for (VolumeIngestionContext volumeContext : this.getProcessedUnManagedVolumeMap().values()) {
            _logger.info("checking already-ingested volume ingestion context {}", 
                    volumeContext.getUnmanagedVolume().forDisplay());
            if (volumeContext instanceof IngestionRequestContext) {
                for (List<DataObject> objectsToBeUpdated : 
                    ((IngestionRequestContext) volumeContext).getDataObjectsToBeUpdatedMap().values()) {
                    for (DataObject o : objectsToBeUpdated) {
                        if (o.getId().equals(uri)) {
                            _logger.info("\tfound data object {} in volume ingestion context {}", 
                                    o.forDisplay(), volumeContext.getUnmanagedVolume().forDisplay());
                            return o;
                        }
                    }
                }
            }
        }

        _logger.info("\tdid not find an already-loaded object to update for URI " + uri);
        return null;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#findUnManagedConsistencyGroup(com.emc.storageos.db.client.model.BlockConsistencyGroup)
     */
    @Override
    public UnManagedConsistencyGroup findUnManagedConsistencyGroup(BlockConsistencyGroup bcg) {
        for (VolumeIngestionContext volumeContext : this.getProcessedUnManagedVolumeMap().values()) {
            List<UnManagedConsistencyGroup> umcgList = volumeContext.getUmCGObjectsToUpdate();
            if (null != umcgList && !umcgList.isEmpty()) {
                for (UnManagedConsistencyGroup umcg : umcgList) {
                    if (umcg.getLabel().equalsIgnoreCase(bcg.getLabel())) {
                        return umcg;
                    }
                }
            }
        }

        return null;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#addObjectToCreate(com.emc.storageos.db.client.model.BlockObject)
     */
    @Override
    public void addBlockObjectToCreate(BlockObject blockObject) {
        getBlockObjectsToBeCreatedMap().put(blockObject.getNativeGuid(), blockObject);
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#addObjectToUpdate(com.emc.storageos.db.client.model.DataObject)
     */
    @Override
    public void addDataObjectToUpdate(DataObject dataObject) {
        if (null == getDataObjectsToBeUpdatedMap().get(getCurrentUnmanagedVolume().getNativeGuid())) {
            getDataObjectsToBeUpdatedMap().put(getCurrentUnmanagedVolume().getNativeGuid(), new ArrayList<DataObject>());
        }
        getDataObjectsToBeUpdatedMap().get(getCurrentUnmanagedVolume().getNativeGuid()).add(dataObject);
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#addDataObjectToCreate(com.emc.storageos.db.client.model.DataObject)
     */
    @Override
    public void addDataObjectToCreate(DataObject dataObject) {
        if (null == getDataObjectsToBeCreatedMap().get(getCurrentUnmanagedVolume().getNativeGuid())) {
            getDataObjectsToBeCreatedMap().put(getCurrentUnmanagedVolume().getNativeGuid(), new ArrayList<DataObject>());
        }
        getDataObjectsToBeCreatedMap().get(getCurrentUnmanagedVolume().getNativeGuid()).add(dataObject);
    }

}
