/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.upgrade.impl.callback;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.BlockSnapshotSessionMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

/**
 * Test class for the BlockSnashotSessionMigration upgrade callback class.
 */
public class BlockSnapshotSessionMigrationTest extends DbSimpleMigrationTestBase {

    // Test constants.
    private static final String PROJECT_NAME = "Project";
    private static final String PARENT_NAME = "Parent";
    private static final String BASE_SNAPSHOT_NAME = "Snapshot";
    private static final String BASE_SNAPVX_SNAPSHOT_NAME = "SnapVx_Snapshot";
    private static final String BASE_SETTINGS_INSTANCE = "Settings";
    private static final String VMAX3_SYSTEM_FW_VERSION = "5977.xxx.xxx";
    private static final int SNAPSHOT_COUNT = 5;
    private static final int SNAPVX_SNAPSHOT_COUNT = 5;

    // A map of the snapshots whose system supports snapshot sessions keyed by the snapshot id.
    Map<String, BlockSnapshot> _linkedTargetsMap = new HashMap<String, BlockSnapshot>();

    // A reference to a logger.
    private static final Logger s_logger = LoggerFactory.getLogger(BlockSnapshotSessionMigrationTest.class);

    /**
     * Setup method executed before test is executed.
     * 
     * @throws IOException
     */
    @BeforeClass
    public static void setup() throws IOException {

        customMigrationCallbacks.put("2.4", new ArrayList<BaseCustomMigrationCallback>() {
            private static final long serialVersionUID = 1L;
            {
                // Add your implementation of migration callback below.
                add(new BlockSnapshotSessionMigration());
            }
        });

        DbsvcTestBase.setup();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getSourceVersion() {
        return "2.4";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getTargetVersion() {
        return "2.5";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void prepareData() throws Exception {
        s_logger.info("Preparing data for BlockSnapshotSession migration test.");

        // A list of database object instances to be created.
        ArrayList<DataObject> newObjectsToBeCreated = new ArrayList<DataObject>();

        // Create some snapshots on a storage system that does not support
        // snapshot sessions. Snapshots on VNX do not currently support
        // snapshot sessions.
        StorageSystem system = new StorageSystem();
        URI systemURI = URIUtil.createId(StorageSystem.class);
        system.setId(systemURI);
        system.setSystemType(DiscoveredDataObject.Type.vnxblock.name());
        newObjectsToBeCreated.add(system);
        for (int i = 0; i < SNAPSHOT_COUNT; i++) {
            BlockSnapshot snapshot = new BlockSnapshot();
            URI snapshotURI = URIUtil.createId(BlockSnapshot.class);
            snapshot.setId(snapshotURI);
            snapshot.setLabel(BASE_SNAPSHOT_NAME + i);
            snapshot.setSnapsetLabel(snapshot.getLabel());
            URI projectURI = URIUtil.createId(Project.class);
            snapshot.setProject(new NamedURI(projectURI, PROJECT_NAME));
            URI parentURI = URIUtil.createId(Volume.class);
            snapshot.setParent(new NamedURI(parentURI, PARENT_NAME + i));
            snapshot.setSettingsInstance(BASE_SETTINGS_INSTANCE + i);
            snapshot.setStorageController(systemURI);
            newObjectsToBeCreated.add(snapshot);
        }

        // Now create some BlockSnapshot instances on a storage system
        // that does support snapshot sessions. VMAX3 is the only storage
        // system for which we currently support snapshot sessions. We
        // set up the system so that the method on StorageSystem
        // "checkIfVmax3" returns true.
        system = new StorageSystem();
        systemURI = URIUtil.createId(StorageSystem.class);
        system.setId(systemURI);
        system.setSystemType(DiscoveredDataObject.Type.vmax.name());
        system.setFirmwareVersion(VMAX3_SYSTEM_FW_VERSION);
        newObjectsToBeCreated.add(system);
        for (int i = 0; i < SNAPVX_SNAPSHOT_COUNT; i++) {
            BlockSnapshot snapshot = new BlockSnapshot();
            URI snapshotURI = URIUtil.createId(BlockSnapshot.class);
            snapshot.setId(snapshotURI);
            snapshot.setLabel(BASE_SNAPVX_SNAPSHOT_NAME + i);
            snapshot.setSnapsetLabel(snapshot.getLabel());
            URI projectURI = URIUtil.createId(Project.class);
            snapshot.setProject(new NamedURI(projectURI, PROJECT_NAME));
            URI parentURI = URIUtil.createId(Volume.class);
            snapshot.setParent(new NamedURI(parentURI, PARENT_NAME + i));
            snapshot.setSettingsInstance(BASE_SETTINGS_INSTANCE + i);
            snapshot.setStorageController(systemURI);
            newObjectsToBeCreated.add(snapshot);
            _linkedTargetsMap.put(snapshotURI.toString(), snapshot);
        }

        // Create the database objects.
        _dbClient.createObject(newObjectsToBeCreated);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void verifyResults() throws Exception {
        s_logger.info("Verifying results for BlockSnapshotSession migration test.");
        List<URI> snapSessionURIs = _dbClient.queryByType(BlockSnapshotSession.class, true);
        Iterator<BlockSnapshotSession> snapSessionsIter = _dbClient
                .queryIterativeObjects(BlockSnapshotSession.class, snapSessionURIs, true);
        Assert.assertTrue("Did not find any snapshot sessions after migration", snapSessionsIter.hasNext());
        int sessionCount = 0;
        while (snapSessionsIter.hasNext()) {
            sessionCount++;
            BlockSnapshotSession snapSession = snapSessionsIter.next();
            Assert.assertNotNull("Snapshot session is null", snapSession);
            StringSet linkedTargets = snapSession.getLinkedTargets();
            Assert.assertNotNull("Snapshot session linked targets list is null", snapSession);
            Assert.assertFalse("Snapshot session linked targets list is empty", linkedTargets.isEmpty());
            Assert.assertEquals("Snapshot session does not have a singled linked target", linkedTargets.size(), 1);
            String linkedTargetId = linkedTargets.iterator().next();
            Assert.assertTrue("Snapshot session linked target not in linked targets map", _linkedTargetsMap.containsKey(linkedTargetId));
            BlockSnapshot linkedTarget = _linkedTargetsMap.remove(linkedTargetId);
            Assert.assertEquals("Label is not correct", linkedTarget.getLabel(), snapSession.getLabel());
            Assert.assertEquals("Session label is not correct", linkedTarget.getSnapsetLabel(), snapSession.getSessionLabel());
            Assert.assertEquals("Session instance is not correct", linkedTarget.getSettingsInstance(), snapSession.getSessionInstance());
            Assert.assertEquals("Project is not correct", linkedTarget.getProject(), snapSession.getProject());
            Assert.assertEquals("Parent is not correct", linkedTarget.getParent(), snapSession.getParent());
        }

        List<URI> snapshotURIs = _dbClient.queryByType(BlockSnapshot.class, true);
        Assert.assertEquals("Snapshot count is not correct", snapshotURIs.size(), SNAPVX_SNAPSHOT_COUNT + SNAPSHOT_COUNT);
        Assert.assertEquals("Snapshot session count is not correct", sessionCount, SNAPVX_SNAPSHOT_COUNT);
    }
}
