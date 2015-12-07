/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils.attrmatchers;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.emc.storageos.db.client.model.RemoteDirectorGroup.SupportedCopyModes;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePool.CopyTypes;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StorageSystem.SupportedFileReplicationTypes;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Collections2;
import com.google.common.collect.ListMultimap;

public class FileReplicationAttrMatcher extends AttributeMatcher {
    private static final Logger _logger = LoggerFactory.getLogger(FileReplicationAttrMatcher.class);
    private static final String STORAGE_DEVICE = "storageDevice";
    private static final String SUPPORTED_COPY_TYPES = "supportedCopyTypes";

    @Override
    protected boolean isAttributeOn(Map<String, Object> attributeMap) {
        return (null != attributeMap && attributeMap.containsKey(Attributes.file_replication_type.toString()));
    }

    private Set<String> getPoolUris(List<StoragePool> matchedPools) {
        Set<String> poolUris = new HashSet<String>();
        for (StoragePool pool : matchedPools) {
            poolUris.add(pool.getId().toString());
        }
        return poolUris;
    }

    @Override
    protected List<StoragePool> matchStoragePoolsWithAttributeOn(
            List<StoragePool> allPools, Map<String, Object> attributeMap) {
    	
        Map<String, List<String>> remoteCopySettings = (Map<String, List<String>>)
                attributeMap.get(Attributes.file_replication.toString());
               
        _logger.info("Pools matching file replication protection  Started :  {} ",
                Joiner.on("\t").join(getNativeGuidFromPools(allPools)));
        // group by storage system
        List<StoragePool> matchedPools = new ArrayList<StoragePool>();
        ListMultimap<URI, StoragePool> storageToPoolMap = ArrayListMultimap.create();
        for (StoragePool pool : allPools) {
            storageToPoolMap.put(pool.getStorageDevice(), pool);
        }
        _logger.info("Grouped Source Storage Devices : {}", storageToPoolMap.asMap().keySet());
        Set<String> remotePoolUris = null;
        ListMultimap<String, URI> remotestorageToPoolMap = null;
        ListMultimap<String, URI> remotestorageTypeMap = ArrayListMultimap.create();
        Boolean remoteReplication = false;
        if (remoteCopySettings != null && !remoteCopySettings.isEmpty()) {
        	remotePoolUris = returnRemotePoolsAssociatedWithRemoteCopySettings(remoteCopySettings, getPoolUris(allPools));
            _logger.info("Remote Pools found : {}", remotePoolUris);
            // get Remote Storage Systems associated with given remote Settings via VPool's matched or
            // assigned Pools
            Set<String> copyModes = getSupportedCopyModesFromGivenRemoteSettings(remoteCopySettings);
            
            remotestorageToPoolMap = groupStoragePoolsByStorageSystem(remotePoolUris, copyModes, true);
            _logger.info("Grouped Remote Storage Devices : {}", remotestorageToPoolMap.asMap().keySet());
            remoteReplication = true;
            // Group the remote storage system based on type!!!
            for (Entry<String, Collection<URI>> storageToPoolsEntry : remotestorageToPoolMap
                    .asMap().entrySet()) {
            	StorageSystem system = _objectCache.queryObject(StorageSystem.class, URI.create(storageToPoolsEntry.getKey()));
            	if(system != null) {
            		remotestorageTypeMap.put(system.getSystemType(), system.getId());
            	}
            }  
        }
        
        for (Entry<URI, Collection<StoragePool>> storageToPoolsEntry : storageToPoolMap
                .asMap().entrySet()) {
            StorageSystem system = _objectCache.queryObject(StorageSystem.class, storageToPoolsEntry.getKey());
            if (null == system.getSupportedReplicationTypes() || 
            		system.getSupportedReplicationTypes().isEmpty()) {
                continue;
            }
            // In case of remote replication, verify the target copies have valid storage pools.
            if(remoteReplication) {
            	if (system.getSupportedReplicationTypes().contains(SupportedFileReplicationTypes.REMOTE.toString()) ) {
            	
            		// Get the remote pool of storage system same type!!!
            		List<URI> remoteSystemsSameType = remotestorageTypeMap.get(system.getSystemType());
                    if (!remoteSystemsSameType.isEmpty() ) {
                        _logger.info(String.format("Adding Pools %s, as associated Storage System %s is remote replication Storage System",
                                Joiner.on("\t").join(storageToPoolsEntry.getValue()), system.getNativeGuid()));
                        matchedPools.addAll(storageToPoolsEntry.getValue());
                    } else {
                        _logger.info(String.format("Skipping Pools %s, as same Storage System type pools not found",
                                Joiner.on("\t").join(storageToPoolsEntry.getValue())));
                    }
                }else {
                    _logger.info(
                            "Skipping Pools {}, as associated Storage System is not replication supported",
                            Joiner.on("\t").join(storageToPoolsEntry.getValue()));
                }
            	
            }else {
            	// Local replication!!!
            	// Add all the storage pools of storage system which supports local replication!!!
            	if (system.getSupportedReplicationTypes().contains(SupportedFileReplicationTypes.LOCAL.toString())) {
            		matchedPools.addAll(storageToPoolsEntry.getValue());
            		// TODO
            		// Filter the pools which supports copy type!!
            	}else {
                    _logger.info(
                            "Skipping Pools {}, as associated Storage System is not replication supported",
                            Joiner.on("\t").join(storageToPoolsEntry.getValue()));
                }
            }
        }
        _logger.info("Pools matching file replication protection Ended: {}", Joiner.on("\t").join(getNativeGuidFromPools(matchedPools)));
        return matchedPools;
    }

    private Set<String> getSupportedCopyModesFromGivenRemoteSettings(Map<String, List<String>> remoteCopySettings) {
        Set<String> copyModes = new HashSet<String>();
        for (Entry<String, List<String>> entry : remoteCopySettings.entrySet()) {
            copyModes.addAll(entry.getValue());
        }
        return copyModes;
    }

    private boolean isRemotelyConnectedViaExpectedCopyMode(StorageSystem system, String copyMode) {
    	
            if (system.getSupportedReplicationTypes() != null
                    && system.getSupportedReplicationTypes().contains(copyMode)) {
                    _logger.info("Found Replication Mode {} in target storage system {}", copyMode, system.getLabel());
                    return true;
                }
        return false;
    }

    /**
     * Choose Pools based on remote VPool's matched or assigned Pools
     * 
     * @param remoteCopySettings
     * @return
     */
    private Set<String> returnRemotePoolsAssociatedWithRemoteCopySettings(
            Map<String, List<String>> remoteCopySettings,
            Set<String> poolUris) {
        Set<String> remotePoolUris = new HashSet<String>();
        for (Entry<String, List<String>> entry : remoteCopySettings.entrySet()) {
            VirtualPool vPool = _objectCache.queryObject(VirtualPool.class,
                    URI.create(entry.getKey()));
            if (null == vPool) {
                remotePoolUris.addAll(poolUris);
            } else if (null != vPool.getUseMatchedPools() && vPool.getUseMatchedPools()) {
                if (null != vPool.getMatchedStoragePools()) {
                    remotePoolUris.addAll(vPool.getMatchedStoragePools());
                }
            } else if (null != vPool.getAssignedStoragePools()) {
                remotePoolUris.addAll(vPool.getAssignedStoragePools());
            }
        }
        return remotePoolUris;
    }

    /**
     * Group Storage Pools by Storage System
     * 
     * @param allPoolUris
     * @return
     */
    private ListMultimap<String, URI> groupStoragePoolsByStorageSystem(Set<String> allPoolUris,
    		Set<String> copyModes, boolean replicationPools) {
        Set<String> columnNames = new HashSet<String>();
        columnNames.add(STORAGE_DEVICE);
        columnNames.add(SUPPORTED_COPY_TYPES);
        Set<String> requiredCopyType = new HashSet<String>();
       
        
        Collection<StoragePool> storagePools = _objectCache.getDbClient().queryObjectFields(StoragePool.class, columnNames,
                new ArrayList<URI>(
                        Collections2.transform(allPoolUris, CommonTransformerFunctions.FCTN_STRING_TO_URI)));
        ListMultimap<String, URI> storageToPoolMap = ArrayListMultimap.create();
        for (StoragePool pool : storagePools) {
        	if (replicationPools && pool.getSupportedCopyTypes() != null &&
        			!pool.getSupportedCopyTypes().contains(CopyTypes.ASYNC.name())) {
        		continue;
        	}
            storageToPoolMap.put(pool.getStorageDevice().toString(), pool.getId());
        }
        return storageToPoolMap;
    }

    @Override
    public Map<String, Set<String>> getAvailableAttribute(List<StoragePool> neighborhoodPools,
            URI vArrayId) {
        Map<String, Set<String>> availableAttrMap = new HashMap<String, Set<String>>(1);
        try {
            ListMultimap<URI, StoragePool> storageToPoolMap = ArrayListMultimap.create();
            for (StoragePool pool : neighborhoodPools) {
                storageToPoolMap.put(pool.getStorageDevice(), pool);
            }
            boolean foundCopyModeAll = false;
            for (Entry<URI, Collection<StoragePool>> storageToPoolsEntry : storageToPoolMap
                    .asMap().entrySet()) {
                StorageSystem system = _objectCache.queryObject(StorageSystem.class, storageToPoolsEntry.getKey());
                if (null == system.getSupportedReplicationTypes()) {
                    continue;
                }
                Set<String> copyModes = new HashSet<String>();
                if (system.getSupportedReplicationTypes().contains("REMOTE")) {
                	copyModes.add(SupportedCopyModes.ASYNCHRONOUS.toString());
                    if (availableAttrMap.get(Attributes.file_replication.toString()) == null) {
                        availableAttrMap.put(Attributes.file_replication.toString(), new HashSet<String>());
                    }
                    availableAttrMap.get(Attributes.file_replication.toString()).addAll(copyModes);
                    if (foundCopyModeAll) {
                        return availableAttrMap;
                    }
                }
            }
        } catch (Exception e) {
            _logger.error("Available Attribute failed in remote mirror protection matcher", e);
        }
        return availableAttrMap;
    }
}