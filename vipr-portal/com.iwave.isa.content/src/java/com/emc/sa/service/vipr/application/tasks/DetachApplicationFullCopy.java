/*
 * Copyright (c) 2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application.tasks;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.application.VolumeGroupFullCopyDetachParam;
import com.emc.vipr.client.Tasks;

public class DetachApplicationFullCopy extends WaitForTasks<TaskResourceRep> {
    private final URI applicationId;
    private final URI volumeId;

    public DetachApplicationFullCopy(URI applicationId, URI volumeId, String name) {
        this.applicationId = applicationId;
        this.volumeId = volumeId;
        provideDetailArgs(applicationId, name);
    }

    @Override
    protected Tasks<TaskResourceRep> doExecute() throws Exception {
        List<URI> volList = Collections.singletonList(volumeId);
        VolumeGroupFullCopyDetachParam input = new VolumeGroupFullCopyDetachParam(false, volList);
        TaskList taskList = getClient().application().detachApplicationFullCopy(applicationId, input);

        return new Tasks<TaskResourceRep>(getClient().auth().getClient(), taskList.getTaskList(),
                TaskResourceRep.class);
    }
}
