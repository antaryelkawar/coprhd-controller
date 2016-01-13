/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.block.BlockSnapshotSessionBulkRep;
import com.emc.storageos.model.block.BlockSnapshotSessionList;
import com.emc.storageos.model.block.BlockSnapshotSessionRestRep;
import com.emc.storageos.model.block.SnapshotSessionCreateParam;
import com.emc.storageos.model.block.SnapshotSessionLinkTargetsParam;
import com.emc.storageos.model.block.SnapshotSessionRelinkTargetsParam;
import com.emc.storageos.model.block.SnapshotSessionUnlinkTargetsParam;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

/**
 * Block snapshot session resources.
 * <p>
 * Base URL: <tt>/block/snapshot-sessions</tt>
 */
public class BlockSnapshotSessions extends ProjectResources<BlockSnapshotSessionRestRep> implements
        TaskResources<BlockSnapshotSessionRestRep> {
    public BlockSnapshotSessions(ViPRCoreClient parent, RestClient client) {
        super(parent, client, BlockSnapshotSessionRestRep.class, PathConstants.BLOCK_SNAPSHOT_SESSION_URL);
    }

    @Override
    public BlockSnapshotSessions withInactive(boolean inactive) {
        return (BlockSnapshotSessions) super.withInactive(inactive);
    }

    @Override
    public BlockSnapshotSessions withInternal(boolean internal) {
        return (BlockSnapshotSessions) super.withInternal(internal);
    }

    @Override
    protected List<BlockSnapshotSessionRestRep> getBulkResources(BulkIdParam input) {
        BlockSnapshotSessionBulkRep response = client.post(BlockSnapshotSessionBulkRep.class, input, getBulkUrl());
        return defaultList(response.getBlockSnapshotSessions());
    }

    @Override
    public Tasks<BlockSnapshotSessionRestRep> getTasks(URI id) {
        return doGetTasks(id);
    }

    @Override
    public Task<BlockSnapshotSessionRestRep> getTask(URI id, URI taskId) {
        return doGetTask(id, taskId);
    }
    
    /**
     * Begins creating a snapshot session (or snapshot sessions) of a given block volume by ID.
     * <p>
     * API Call: <tt>POST /block/volumes/{volumeId}/protection/snapshot-sessions</tt>
     * 
     * @param volumeId
     *            the ID of the block volume to create a snapshot session.
     * @param param
     *            the snapshot session configuration.
     * @return tasks for monitoring the progress each snapshot session creation.
     */
    public Tasks<BlockSnapshotSessionRestRep> createForVolume(URI volumeId, SnapshotSessionCreateParam param) {
        return postTasks(param, getByVolumeUrl(), volumeId);
    }
    
    /**
     * Lists the block snapshot sessions for a given block volume.
     * <p>
     * API Call: <tt>GET /block/volumes/{volumeId}/protection/snapshot-sessions</tt>
     * 
     * @param volumeId
     *            the ID of the block volume.
     * @return the list of snapshot session references for the volume.
     */
    public List<NamedRelatedResourceRep> listByVolume(URI volumeId) {
        return getList(getByVolumeUrl(), volumeId);
    }
            
    /**
     * Gets the block snapshot sessions for a given block volume.
     * 
     * @param volumeId
     *            the ID of the block volume.
     * @return the list of block snapshot sessions for the volume.
     * 
     * @see #listByVolume(URI)
     * @see #getByRefs(java.util.Collection)
     */
    public List<BlockSnapshotSessionRestRep> getByVolume(URI volumeId) {
        return getByVolume(volumeId, null);
    }

    /**
     * Gets the block snapshot sessions for a given block volume, optionally filtering the results.
     * 
     * @param volumeId
     *            the ID of the block volume.
     * @param filter
     *            the filter to apply (may be null, for no filtering).
     * @return the list of block snapshot sessions for the volume.
     * 
     * @see #listByVolume(URI)
     * @see #getByRefs(java.util.Collection, ResourceFilter)
     */
    public List<BlockSnapshotSessionRestRep> getByVolume(URI volumeId, ResourceFilter<BlockSnapshotSessionRestRep> filter) {
        List<NamedRelatedResourceRep> refs = listByVolume(volumeId);
        return getByRefs(refs, filter);
    }
    
    /**
     * Create and link new targets to an existing BlockSnapshotSession instance.
     * <p>
     * API Call: <tt>POST /block/snapshot-sessions/{id}/link-targets</tt>
     *  
     * @param snapshotSessionId 
     *           The URI of the BlockSnapshotSession instance to which the
     *           new targets will be linked.
     * @param param 
     *           The linked target information.
     * @return a task for monitoring the progress of the operation.
     */
    public Tasks<BlockSnapshotSessionRestRep> linkTargets(URI snapshotSessionId, SnapshotSessionLinkTargetsParam linkTargetsParam) {
        return postTasks(getIdUrl() + "/link-targets", snapshotSessionId, linkTargetsParam);
    }
    
    /**
     * Re-link a target to either it's current snapshot session or to a different 
     * snapshot session of the same source.
     * <p>
     * API Call: <tt>POST /block/snapshot-sessions/{id}/relink-targets</tt>
     *  
     * @param snapshotSessionId 
     *           The URI of the BlockSnapshotSession instance to which the
     *           the targets will be relinked.
     * @param param 
     *           The linked target information.
     * @return a task for monitoring the progress of the operation.
     */
    public Tasks<BlockSnapshotSessionRestRep> relinkTargets(URI snapshotSessionId, SnapshotSessionRelinkTargetsParam relinkTargetsParam) {
        return postTasks(getIdUrl() + "/relink-targets", snapshotSessionId, relinkTargetsParam);
    }
    
    /**
     * Unlink target volumes from an existing BlockSnapshotSession instance and 
     * optionally delete those target volumes.
     * <p>
     * API Call: <tt>POST /block/snapshot-sessions/{id}/unlink-targets</tt>
     *  
     * @param snapshotSessionId 
     *           The URI of the BlockSnapshotSession instance to which the
     *           new targets are currently linked.
     * @param param 
     *           The linked target information.
     * @return a task for monitoring the progress of the operation.
     */
    public Tasks<BlockSnapshotSessionRestRep> unlinkTargets(URI snapshotSessionId, SnapshotSessionUnlinkTargetsParam unlinkTargetsParam) {
        return postTasks(getIdUrl() + "/unlink-targets", snapshotSessionId, unlinkTargetsParam);
    }
    
    /**
     * Restores the data on the array snapshot point-in-time copy represented by the
     * BlockSnapshotSession instance with the passed id, to the snapshot session source
     * object.
     * 
     * <p>
     * API Call: <tt>POST /block/snapshot-sessions/{id}/restore</tt>
     * 
     * @param id
     *            the ID of the snapshot session to restore.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<BlockSnapshotSessionRestRep> restore(URI id) {
        return postTask(getIdUrl() + "/restore", id);
    }
    
    /**
     * Begins deactivating a given block snapshot session by ID.
     * <p>
     * API Call: <tt>POST /block/snapshot-sessions/{id}/deactivate</tt>
     * 
     * @param id
     *            the ID of the snapshot session to deactivate.
     * @return a task for monitoring the progress of the operation.
     */
    public Tasks<BlockSnapshotSessionRestRep> deactivate(URI id) {
        return doDeactivateWithTasks(id);
    }
    
    /**
     * Gets a list of block snapshot session references from the given URL (path + args).
     * 
     * @param path
     *            the path to get.
     * @param args
     *            the arguments for the path.
     * @return the list of snapshot session references.
     */
    protected List<NamedRelatedResourceRep> getList(String path, Object... args) {
        BlockSnapshotSessionList response = client.get(BlockSnapshotSessionList.class, path, args);
        return defaultList(response.getSnapSessionRelatedResourceList());
    }
    
    /**
     * Gets the URL for looking up block snapshot sessions by volume: <tt>/block/volumes/{volumeId}/protection/snapshot-sessions</tt>
     * 
     * @return the URL for snapshots by volume.
     */
    protected String getByVolumeUrl() {
        return PathConstants.BLOCK_VOLUMES_URL + "/{volumeId}/protection/snapshot-sessions";
    }
}