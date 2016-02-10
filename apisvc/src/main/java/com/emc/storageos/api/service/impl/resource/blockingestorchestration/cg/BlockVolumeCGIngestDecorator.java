/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration.cg;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.db.client.model.AbstractChangeTrackingSet;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;

/**
 * This decorator is responsible for decorating the CG with the backend/regular volume.
 * 
 * If the BlockObjects are behind RP + VPLEX, blockConsistencyGroup should belongs to RP.
 * And should decorate the CG with the VPLEX & its backend volumes protected by RP.
 * 
 *
 */
public class BlockVolumeCGIngestDecorator extends BlockCGIngestDecorator {
    private static final Logger logger = LoggerFactory.getLogger(BlockVolumeCGIngestDecorator.class);

    @Override
    public void decorateCG(BlockConsistencyGroup cg, Collection<BlockObject> associatedObjects,
            IngestionRequestContext requestContext)
            throws Exception {

        if (null == associatedObjects || associatedObjects.isEmpty()) {
            logger.info("No associated BlockObject's found to decorate for cg {}", cg.getLabel());
            return;
        }

        for (BlockObject blockObj : associatedObjects) {
            StringSetMap systemCGs = cg.getSystemConsistencyGroups();

            // No entries yet in the system consistency groups list. That's OK, we'll create it.
            if (null == systemCGs || systemCGs.isEmpty()) {
                cg.setSystemConsistencyGroups(new StringSetMap());
            }

            // This volume is not in a CG of this type
            if (blockObj.getReplicationGroupInstance() == null) {
                logger.info("BlockObject {} doesn't have replicationGroup name {}. No need to set system cg information.",
                        blockObj.getNativeGuid());
                continue;
            }

            boolean found = false;
            // Look through the existing entries in the CG and see if we find a match.
            for (Entry<String, AbstractChangeTrackingSet<String>> systemCGEntry : systemCGs.entrySet()) {
                if (systemCGEntry.getKey().equalsIgnoreCase(blockObj.getStorageController().toString())) {
                    if (systemCGEntry.getValue().contains(blockObj.getReplicationGroupInstance())) {
                        logger.info(String.format("Found BlockObject %s,%s system details in cg %s", blockObj.getNativeGuid(),
                                blockObj.getReplicationGroupInstance(), cg.getLabel()));
                        found = true;
                        break;
                    }
                }
            }

            // If we didn't find this storage:cg combo, let's add it.
            if (!found) {
                logger.info(String.format("Adding BlockObject %s/%s in CG %s", blockObj.getNativeGuid(),
                        blockObj.getReplicationGroupInstance(), cg.getLabel()));
                cg.addSystemConsistencyGroup(blockObj.getStorageController().toString(),
                        blockObj.getReplicationGroupInstance());
            }

            if (!cg.getTypes().contains(Types.LOCAL.toString())) {
                cg.getTypes().add(Types.LOCAL.toString());
            }
        }
    }

    @Override
    protected Collection<BlockObject> getAssociatedObjects(BlockConsistencyGroup cg, Collection<BlockObject> allCGBlockObjects,
            IngestionRequestContext requestContext)
            throws Exception {
        Collection<BlockObject> blockObjects = new ArrayList<BlockObject>();

        // Filter out vplex volumes
        Iterator<BlockObject> allCGBlockObjectItr = allCGBlockObjects.iterator();
        while (allCGBlockObjectItr.hasNext()) {
            BlockObject blockObject = allCGBlockObjectItr.next();
            if (blockObject instanceof Volume) {
                Volume volume = (Volume) blockObject;
                if (null == volume.getAssociatedVolumes() || volume.getAssociatedVolumes().isEmpty()) {
                    blockObjects.add(volume);
                }
            }
        }
        return blockObjects;
    }

    @Override
    public boolean isExecuteDecorator(UnManagedVolume umv, IngestionRequestContext requestContext) {
        return true;
    }

    @Override
    public void setNextDecorator(BlockCGIngestDecorator decorator) {
        this.nextCGIngestDecorator = decorator;
    }

}