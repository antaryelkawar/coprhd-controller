/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.aix.tasks;

import com.iwave.ext.linux.command.powerpath.PowermtConfigCommand;
import com.iwave.ext.linux.command.powerpath.PowermtRestoreCommand;
import com.iwave.ext.linux.command.powerpath.PowermtSaveCommand;

public class UpdatePowerPathEntries extends AixExecutionTask<Void> {

    public UpdatePowerPathEntries() {
        setName("UpdatePowerPathEntries.name");
    }

    @Override
    public void execute() throws Exception {
        executeCommand(new PowermtConfigCommand(), MEDIUM_TIMEOUT);
        executeCommand(new PowermtRestoreCommand(), MEDIUM_TIMEOUT);
        executeCommand(new PowermtSaveCommand(), MEDIUM_TIMEOUT);
        setDetail("powermt config; powermt restore; powermt save;");
    }
}
