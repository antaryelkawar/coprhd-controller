/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl;

import java.net.URI;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.Controller;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.db.client.model.ObjectUserSecretKey;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.ClientControllerException;
import com.emc.storageos.impl.AbstractDiscoveredSystemController;
import com.emc.storageos.model.object.BucketACLUpdateParams;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.ObjectController;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl.Lock;
import com.emc.storageos.volumecontroller.impl.monitoring.MonitoringJob;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.MonitorTaskCompleter;

/**
 * This class services all object provisioning calls. Provisioning
 * calls are matched against device specific controller implementations
 * and forwarded from this implementation
 */
public class ObjectControllerImpl extends AbstractDiscoveredSystemController
        implements ObjectController {
    private final static Logger _log = LoggerFactory.getLogger(FileControllerImpl.class);

    // device specific ObjectController implementations
    private Set<ObjectController> _deviceImpl;
    private Dispatcher _dispatcher;
    private DbClient _dbClient;

    public void setDeviceImpl(Set<ObjectController> deviceImpl) {
        _deviceImpl = deviceImpl;
    }

    public void setDispatcher(Dispatcher dispatcher) {
        _dispatcher = dispatcher;
    }

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    @Override
    public void connectStorage(URI storage) throws InternalException {
        _log.debug("ObjectControllerImpl:connectStorage");
        execOb("connectStorage", storage);

    }

    @Override
    public void disconnectStorage(URI storage) throws InternalException {
        execOb("disconnectStorage", storage);

    }

    @Override
    public void discoverStorageSystem(AsyncTask[] tasks)
            throws InternalException {
        _log.debug("ObjectControllerImpl:discoverStorageSystem");
        try {
            ControllerServiceImpl.scheduleDiscoverJobs(tasks, Lock.DISCOVER_COLLECTION_LOCK, ControllerServiceImpl.DISCOVERY);
        } catch (Exception e) {
            _log.error(
                    "Problem in discoverStorageSystem due to {} ",
                    e.getMessage());
            throw ClientControllerException.fatals.unableToScheduleDiscoverJobs(tasks, e);
        }

    }

    @Override
    public void scanStorageProviders(AsyncTask[] tasks)
            throws InternalException {
        _log.debug("ObjectControllerImpl:scanStorageProviders");
        throw ClientControllerException.fatals.unableToScanSMISProviders(tasks, "ObjectController", null);

    }

    @Override
    public void startMonitoring(AsyncTask task, Type deviceType)
            throws InternalException {
        try {
            _log.debug("ObjectControllerImpl:startMonitoring");
            MonitoringJob job = new MonitoringJob();
            job.setCompleter(new MonitorTaskCompleter(task));
            job.setDeviceType(deviceType);
            ControllerServiceImpl.enqueueMonitoringJob(job);
        } catch (Exception e) {
            throw ClientControllerException.fatals.unableToMonitorSMISProvider(task, deviceType.toString(), e);
        }
    }

    @Override
    public Controller lookupDeviceController(DiscoveredSystemObject device) throws ControllerException {
        // dummy impl that returns the first one
        _log.debug("ObjectControllerImpl:lookupDeviceController");
        if (device == null) {
            throw ClientControllerException.fatals.unableToLookupStorageDeviceIsNull();
        }
        ObjectController ob = _deviceImpl.iterator().next();
        if (ob == null) {
            throw ClientControllerException.fatals.unableToLocateDeviceController("BlockController");
        }
        return ob;
    }

    private void execOb(String methodName, Object... args) throws InternalException {
        queueTask(_dbClient, StorageSystem.class, _dispatcher, methodName, args);
    }

    @Override
    public void createBucket(URI storage, URI stPool, URI bkt, String label, String namespace, Integer retention,
            Long hardQuota, Long softQuota, String owner, String opId) throws InternalException {
        _log.debug("ObjectControllerImpl:createBucket start");
        execOb("createBucket", storage, stPool, bkt, label, namespace, retention,
                hardQuota, softQuota, owner, opId);
    }

    @Override
    public void deleteBucket(URI storage, URI bucket, String task) throws InternalException {
        _log.debug("ObjectControllerImpl:deleteBucket");
        execOb("deleteBucket", storage, bucket, task);
    }

    @Override
    public void updateBucket(URI storage, URI bucket, Long softQuota, Long hardQuota, Integer retention, String task)
            throws InternalException {
        _log.debug("ObjectControllerImpl:updateBucket");
        execOb("updateBucket", storage, bucket, softQuota, hardQuota, retention, task);
    }

    @Override
    public void updateBucketACL(URI storage, URI bucket, BucketACLUpdateParams param, String opId) throws InternalException {
        _log.debug("ObjectControllerImpl:updateBucketACL start");
        execOb("updateBucketACL", storage, bucket, param, opId);
    }

    @Override
    public void deleteBucketACL(URI storage, URI bucket, String opId) throws InternalException {
        _log.debug("ObjectControllerImpl:deleteBucketACL start");
        execOb("deleteBucketACL", storage, bucket, opId);
    }

    @Override
    public ObjectUserSecretKey getUserSecretKeys(URI storage, String userId) throws InternalException {
        _log.debug("ObjectControllerImpl:getUserSecretKey start");
        // Synchronous call than queuing
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
        Controller controller = lookupDeviceController(storageSystem);
        ObjectController objController = (ObjectController) controller;
        return objController.getUserSecretKeys(storage, userId);        
    }

    @Override
    public ObjectUserSecretKey addUserSecretKey(URI storage, String userId, String secretKey) throws InternalException {
        _log.debug("ObjectControllerImpl:addUserSecretKey start");
        // Synchronous call than queuing
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
        Controller controller = lookupDeviceController(storageSystem);
        ObjectController objController = (ObjectController) controller;
        return objController.addUserSecretKey(storage, userId, secretKey);        
    }
       
}