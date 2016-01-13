/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.block.BlockSnapshotSessionRestRep;
import com.emc.vipr.client.Tasks;

public class DeactivateBlockSnapshotSession extends WaitForTasks<BlockSnapshotSessionRestRep> {
    private URI snapshotSessionId;

    public DeactivateBlockSnapshotSession(String snapshotSessionId) {
        this(uri(snapshotSessionId));
    }

    public DeactivateBlockSnapshotSession(URI snapshotSessionId) {
        super();
        this.snapshotSessionId = snapshotSessionId;
        provideDetailArgs(snapshotSessionId);
    }

    @Override
    protected Tasks<BlockSnapshotSessionRestRep> doExecute() throws Exception {
        return getClient().blockSnapshotSessions().deactivate(snapshotSessionId);
    }
}