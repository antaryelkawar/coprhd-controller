/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file;

import static com.emc.sa.service.ServiceParams.FILESYSTEMS;
import static com.emc.sa.service.ServiceParams.FILE_POLICY;
import static com.emc.sa.service.ServiceParams.PROJECT;

import java.net.URI;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("DissociateFilePolicyFromFileSystem")
public class DissociateFilePolicyFromFileSystemService extends ViPRService {

    @Param(FILESYSTEMS)
    protected URI fileSystem;

    @Param(FILE_POLICY)
    protected URI filePolicy;

    @Override
    public void execute() throws Exception {
        FileStorageUtils.dissociateFilePolicy(fileSystem, filePolicy);
    }
}
