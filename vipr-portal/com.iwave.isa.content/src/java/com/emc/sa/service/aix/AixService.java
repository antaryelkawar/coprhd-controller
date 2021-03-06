/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.aix;

import java.net.URI;
import java.util.List;

import com.emc.aix.AixSystem;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.google.common.collect.Lists;

public abstract class AixService extends ViPRService {

    @Param(ServiceParams.HOST)
    protected URI hostId;

    protected Host host;

    protected List<Host> hosts;

    protected List<Initiator> hostPorts = Lists.newArrayList();

    protected AixSystem aixSystem;

    public AixSystem getAixSystem() {
        return aixSystem;
    }

    protected void initHost() {
        if (BlockStorageUtils.isCluster(hostId)) {
            throw new IllegalStateException("Aix Services do not support clustering");
        }

        host = getModelClient().hosts().findById(hostId);
        if (host == null) {
            throw new IllegalArgumentException("Host " + hostId + " not found");
        }

        hosts = Lists.newArrayList();
        logInfo("aix.service.target.host", host.getLabel());

        hostPorts = getModelClient().initiators().findByHost(host.getId());
        aixSystem = convertHost(host);
    }

    @Override
    public void init() throws Exception {
        super.init();
        initHost();
    }

    protected void acquireHostsLock() {
        acquireHostLock(host, null);
    }

    public static AixSystem convertHost(Host host) {
        AixSystem cli = new AixSystem();
        cli.setHost(host.getHostName());
        cli.setPort(host.getPortNumber());
        cli.setUsername(host.getUsername());
        cli.setPassword(host.getPassword());
        cli.setHostId(host.getId());
        return cli;
    }
}
