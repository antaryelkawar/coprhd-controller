/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

/**
 * Task completer class for remove volumes from CG
 *
 */
public class BlockConsistencyGroupRemoveVolumeCompleter extends BlockConsistencyGroupUpdateCompleter{

    private static final long serialVersionUID = -871023109512730999L;
    private static final Logger log = LoggerFactory.getLogger(BlockConsistencyGroupRemoveVolumeCompleter.class);
    private List<URI> removedVolumeList = null;
    
    public BlockConsistencyGroupRemoveVolumeCompleter(URI cgURI, List<URI>removedVolumesList, String opId) {
        super(cgURI, opId);
        this.removedVolumeList = removedVolumesList;
    }
    
    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded)
            throws DeviceControllerException {
        log.info("Updating removed volume replicationGroupInstance");
        try {
            super.complete(dbClient, status, coded);
            if (status == Status.ready) {
                for (URI voluri : removedVolumeList) {
                    Volume volume = dbClient.queryObject(Volume.class, voluri);
                    if (volume != null && !volume.getInactive()) {
                        volume.setReplicationGroupInstance(NullColumnValueGetter.getNullStr());
                        dbClient.updateObject(volume);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed updating status. BlockConsistencyGroupRemoveVolume {}, for task "
                    + getOpId(), getId(), e);
        }
    }

}