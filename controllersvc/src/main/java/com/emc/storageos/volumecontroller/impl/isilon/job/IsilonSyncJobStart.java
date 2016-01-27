/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.isilon.job;

import java.net.URI;

import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;

public class IsilonSyncJobStart extends IsilonSyncIQJob {

    @Override
    public void updateStatus(JobContext jobContext) throws Exception {
        super.updateStatus(jobContext);
    }

    public IsilonSyncJobStart(String jobId, URI storageSystemUri, TaskCompleter taskCompleter, String jobName) {
        super(jobId, storageSystemUri, taskCompleter, jobName);
    }
    

}