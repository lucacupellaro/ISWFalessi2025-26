/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zookeeper.server;

import java.io.EOFException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.apache.jute.InputArchive;
import org.apache.jute.OutputArchive;
import org.apache.jute.Record;
import org.apache.zookeeper.DigestWatcher;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.apache.zookeeper.KeeperException.NodeExistsException;
import org.apache.zookeeper.Quotas;
import org.apache.zookeeper.StatsTrack;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.Watcher.WatcherType;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooDefs.OpCode;
import org.apache.zookeeper.audit.AuditConstants;
import org.apache.zookeeper.audit.AuditEvent.Result;
import org.apache.zookeeper.audit.ZKAuditProvider;
import org.apache.zookeeper.common.PathTrie;
import org.apache.zookeeper.common.PathUtils;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Id;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.data.StatPersisted;
import org.apache.zookeeper.server.watch.IWatchManager;
import org.apache.zookeeper.server.watch.WatchManagerFactory;
import org.apache.zookeeper.server.watch.WatcherMode;
import org.apache.zookeeper.server.watch.WatcherOrBitSet;
import org.apache.zookeeper.server.watch.WatchesPathReport;
import org.apache.zookeeper.server.watch.WatchesReport;
import org.apache.zookeeper.server.watch.WatchesSummary;
import org.apache.zookeeper.txn.CheckVersionTxn;
import org.apache.zookeeper.txn.CloseSessionTxn;
import org.apache.zookeeper.txn.CreateContainerTxn;
import org.apache.zookeeper.txn.CreateTTLTxn;
import org.apache.zookeeper.txn.CreateTxn;
import org.apache.zookeeper.txn.DeleteTxn;
import org.apache.zookeeper.txn.ErrorTxn;
import org.apache.zookeeper.txn.MultiTxn;
import org.apache.zookeeper.txn.SetACLTxn;
import org.apache.zookeeper.txn.SetDataTxn;
import org.apache.zookeeper.txn.Txn;
import org.apache.zookeeper.txn.TxnDigest;
import org.apache.zookeeper.txn.TxnHeader;
import org.apache.zookeeper.util.ServiceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataTree {

    private static final Logger LOG = LoggerFactory.getLogger(DataTree.class);
    private final RateLogger rateLogger = new RateLogger(LOG, 15 * 60 * 1000);

    private final NodeHashMap nodes;
    private IWatchManager dataWatches;
    private IWatchManager childWatches;
    private final AtomicLong nodeDataSize = new AtomicLong(0);

    private static final String ROOT_ZOOKEEPER = "/";
    private static final String PROC_ZOOKEEPER = Quotas.procZookeeper;
    private static final String PROC_CHILD_ZOOKEEPER = PROC_ZOOKEEPER.substring(1);
    private static final String QUOTA_ZOOKEEPER = Quotas.quotaZookeeper;
    private static final String QUOTA_CHILD_ZOOKEEPER = QUOTA_ZOOKEEPER.substring(PROC_ZOOKEEPER.length() + 1);
    private static final String CONFIG_ZOOKEEPER = ZooDefs.CONFIG_NODE;
    private static final String CONFIG_CHILD_ZOOKEEPER = CONFIG_ZOOKEEPER.substring(PROC_ZOOKEEPER.length() + 1);

    private static final String PATH_KEY = "path";
    private static final String NODE_KEY = "node";
    private static final String ZXID_KEY = "zxid";
    private static final String DIGEST_VERSION_KEY = "digestVersion";
    private static final String DIGEST_KEY = "digest";

    private final PathTrie pTrie = new PathTrie();
    public static final int STAT_OVERHEAD_BYTES = (6 * 8) + (5 * 4);

    private final Map<Long, HashSet<String>> ephemerals = new ConcurrentHashMap<>();
    private final Set<String> containers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> ttls = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ReferenceCountedACLCache aclCache = new ReferenceCountedACLCache();

    public static final int DIGEST_LOG_LIMIT = 1024;
    public static final int DIGEST_LOG_INTERVAL = 128;

    private ZxidDigest digestFromLoadedSnapshot;
    private volatile ZxidDigest lastProcessedZxidDigest;
    private boolean firstMismatchTxn = true;
    private final List<DigestWatcher> digestWatchers = new ArrayList<>();
    private final LinkedList<ZxidDigest> digestLog = new LinkedList<>();
    private final DigestCalculator digestCalculator;

    private DataNode root = new DataNode(new byte[0], -1L, new StatPersisted());
    private final DataNode procDataNode = new DataNode(new byte[0], -1L, new StatPersisted());
    private final DataNode quotaDataNode = new DataNode(new byte[0], -1L, new StatPersisted());

    public DataTree() {
        this(new DigestCalculator());
    }

    DataTree(DigestCalculator digestCalculator) {
        this.digestCalculator = digestCalculator;
        this.nodes = new NodeHashMapImpl(digestCalculator);

        nodes.put("", root);
        nodes.putWithoutDigest(ROOT_ZOOKEEPER, root);

        root.addChild(PROC_CHILD_ZOOKEEPER);
        nodes.put(PROC_ZOOKEEPER, procDataNode);

        procDataNode.addChild(QUOTA_CHILD_ZOOKEEPER);
        nodes.put(QUOTA_ZOOKEEPER, quotaDataNode);

        addConfigNode();
        nodeDataSize.set(approximateDataSize());
        initWatchManagers();
    }

    private void initWatchManagers() {
        try {
            dataWatches = WatchManagerFactory.createWatchManager();
            childWatches = WatchManagerFactory.createWatchManager();
        } catch (Exception e) {
            LOG.error("Unexpected exception when creating WatchManager, exiting abnormally", e);
            ServiceUtils.requestSystemExit(ExitCode.UNEXPECTED_ERROR.getValue());
        }
    }

    // --- Core API Methods (Kept for compatibility) ---

    public void addConfigNode() {
        DataNode zookeeperZnode = nodes.get(PROC_ZOOKEEPER);
        if (zookeeperZnode != null) {
            zookeeperZnode.addChild(CONFIG_CHILD_ZOOKEEPER);
        }
        nodes.put(CONFIG_ZOOKEEPER, new DataNode(new byte[0], -1L, new StatPersisted()));
        try {
            setACL(CONFIG_ZOOKEEPER, ZooDefs.Ids.READ_ACL_UNSAFE, -1);
        } catch (NoNodeException e) {
            LOG.error("There's no {} znode", CONFIG_ZOOKEEPER);
        }
    }

    public void createNode(final String path, byte[] data, List<ACL> acl, long ephemeralOwner, int parentCVersion, long zxid, long time) throws NoNodeException, NodeExistsException {
        createNode(path, data, acl, ephemeralOwner, parentCVersion, zxid, time, null);
    }

    public void createNode(final String path, byte[] data, List<ACL> acl, long ephemeralOwner, int parentCVersion, long zxid, long time, Stat outputStat) throws NoNodeException, NodeExistsException {
        int lastSlash = path.lastIndexOf('/');
        String parentName = path.substring(0, lastSlash);
        String childName = path.substring(lastSlash + 1);
        StatPersisted stat = createStat(zxid, time, ephemeralOwner);
        DataNode parent = nodes.get(parentName);
        if (parent == null) throw new NoNodeException();

        synchronized (parent) {
            List<ACL> parentAcl = getACL(parent);
            Long acls = aclCache.convertAcls(acl);
            DataNode existingChild = nodes.get(path);
            if (existingChild != null) {
                existingChild.acl = acls;
                throw new NodeExistsException();
            }
            nodes.preChange(parentName, parent);
            int newCVersion = (parentCVersion == -1) ? parent.stat.getCversion() + 1 : parentCVersion;
            if (newCVersion > parent.stat.getCversion()) {
                parent.stat.setCversion(newCVersion);
                parent.stat.setPzxid(zxid);
            }
            DataNode child = new DataNode(data, acls, stat);
            parent.addChild(childName);
            nodes.postChange(parentName, parent);
            nodeDataSize.addAndGet(getNodeSize(path, child.data));
            nodes.put(path, child);
            handleEphemeralNodeCreation(path, ephemeralOwner);
            if (outputStat != null) child.copyStat(outputStat);
            handleQuotaAndWatchesOnCreate(path, parentName, childName, data, zxid, acl, parentAcl);
        }
    }

    private void handleEphemeralNodeCreation(String path, long ephemeralOwner) {
        EphemeralType ephemeralType = EphemeralType.get(ephemeralOwner);
        if (ephemeralType == EphemeralType.CONTAINER) {
            containers.add(path);
        } else if (ephemeralType == EphemeralType.TTL) {
            ttls.add(path);
            ServerMetrics.getMetrics().TTL_NODE_CREATED_COUNT.add(1);
        } else if (ephemeralOwner != 0) {
            ephemerals.computeIfAbsent(ephemeralOwner, k -> new HashSet<>()).add(path);
        }
    }

    private void handleQuotaAndWatchesOnCreate(String path, String parentName, String childName, byte[] data, long zxid, List<ACL> acl, List<ACL> parentAcl) {
        if (parentName.startsWith(QUOTA_ZOOKEEPER)) {
            if (Quotas.limitNode.equals(childName)) pTrie.addPath(Quotas.trimQuotaPath(parentName));
            if (Quotas.statNode.equals(childName)) updateQuotaForPath(Quotas.trimQuotaPath(parentName));
        }
        String lastPrefix = getMaxPrefixWithQuota(path);
        long bytes = data == null ? 0 : data.length;
        if (lastPrefix != null) updateQuotaStat(lastPrefix, bytes, 1);
        updateWriteStat(path, bytes);
        dataWatches.triggerWatch(path, EventType.NodeCreated, zxid, acl);
        childWatches.triggerWatch(parentName.isEmpty() ? ROOT_ZOOKEEPER : parentName, EventType.NodeChildrenChanged, zxid, parentAcl);
    }

    public void deleteNode(String path, long zxid) throws NoNodeException {
        int lastSlash = path.lastIndexOf('/');
        String parentName = path.substring(0, lastSlash);
        String childName = path.substring(lastSlash + 1);
        DataNode parent = nodes.get(parentName);
        if (parent == null) throw new NoNodeException();

        synchronized (parent) {
            nodes.preChange(parentName, parent);
            parent.removeChild(childName);
            if (zxid > parent.stat.getPzxid()) parent.stat.setPzxid(zxid);
            nodes.postChange(parentName, parent);
        }

        DataNode node = nodes.get(path);
        if (node == null) throw new NoNodeException();

        nodes.remove(path);
        List<ACL> acl = getACL(node);
        synchronized (node) {
            aclCache.removeUsage(node.acl);
            nodeDataSize.addAndGet(-getNodeSize(path, node.data));
            long owner = node.stat.getEphemeralOwner();
            if (EphemeralType.get(owner) == EphemeralType.CONTAINER) containers.remove(path);
            else if (EphemeralType.get(owner) == EphemeralType.TTL) { ttls.remove(path); ServerMetrics.getMetrics().TTL_NODE_DELETED_COUNT.add(1); }
            else if (owner != 0) { Set<String> s = ephemerals.get(owner); if (s != null) s.remove(path); }
        }

        if (parentName.startsWith(PROC_ZOOKEEPER) && Quotas.limitNode.equals(childName)) pTrie.deletePath(Quotas.trimQuotaPath(parentName));
        String lastPrefix = getMaxPrefixWithQuota(path);
        if (lastPrefix != null) updateQuotaStat(lastPrefix, -node.data.length, -1);
        updateWriteStat(path, 0L);

        WatcherOrBitSet processed = dataWatches.triggerWatch(path, EventType.NodeDeleted, zxid, acl);
        childWatches.triggerWatch(path, EventType.NodeDeleted, zxid, acl, processed);
        childWatches.triggerWatch("".equals(parentName) ? ROOT_ZOOKEEPER : parentName, EventType.NodeChildrenChanged, zxid, getACL(parent));
    }

    public Stat setData(String path, byte[] data, int version, long zxid, long time) throws NoNodeException {
        DataNode n = nodes.get(path);
        if (n == null) throw new NoNodeException();

        Stat s = new Stat();
        List<ACL> acl;
        byte[] lastData;
        synchronized (n) {
            acl = getACL(n);
            lastData = n.data;
            nodes.preChange(path, n);
            n.data = data;
            n.stat.setMtime(time);
            n.stat.setMzxid(zxid);
            n.stat.setVersion(version);
            n.copyStat(s);
            nodes.postChange(path, n);
        }

        String lastPrefix = getMaxPrefixWithQuota(path);
        long bytesDiff = (data == null ? 0 : data.length) - (lastData == null ? 0 : lastData.length);
        if (lastPrefix != null) updateQuotaStat(lastPrefix, bytesDiff, 0);
        nodeDataSize.addAndGet(getNodeSize(path, data) - getNodeSize(path, lastData));
        updateWriteStat(path, data == null ? 0 : data.length);
        dataWatches.triggerWatch(path, EventType.NodeDataChanged, zxid, acl);
        return s;
    }

    // --- Internal Helpers extracted for Complexity reduction ---

    public ProcessTxnResult processTxn(TxnHeader header, Record txn) {
        return processTxn(header, txn, false);
    }

    public ProcessTxnResult processTxn(TxnHeader header, Record txn, boolean isSubTxn) {
        ProcessTxnResult rc = new ProcessTxnResult();
        rc.clientId = header.getClientId();
        rc.cxid = header.getCxid();
        rc.zxid = header.getZxid();
        rc.type = header.getType();
        rc.err = 0;

        try {
            switch (header.getType()) {
                case OpCode.create: processCreateTxn(header, (CreateTxn) txn, rc); break;
                case OpCode.create2: processCreate2Txn(header, (CreateTxn) txn, rc); break;
                case OpCode.createTTL: processCreateTTLTxn(header, (CreateTTLTxn) txn, rc); break;
                case OpCode.createContainer: processCreateContainerTxn(header, (CreateContainerTxn) txn, rc); break;
                case OpCode.delete:
                case OpCode.deleteContainer:
                    DeleteTxn dt = (DeleteTxn) txn; rc.path = dt.getPath(); deleteNode(dt.getPath(), header.getZxid()); break;
                case OpCode.setData: processSetDataTxn(header, (SetDataTxn) txn, rc); break;
                case OpCode.setACL:
                    SetACLTxn saclt = (SetACLTxn) txn; rc.path = saclt.getPath();
                    rc.stat = setACL(saclt.getPath(), saclt.getAcl(), saclt.getVersion()); break;
                case OpCode.closeSession: processCloseSessionTxn(header, txn); break;
                case OpCode.error: rc.err = ((ErrorTxn) txn).getErr(); break;
                case OpCode.check: rc.path = ((CheckVersionTxn) txn).getPath(); break;
                case OpCode.multi: processMultiTxn(header, (MultiTxn) txn, rc); break;
            }
        } catch (Exception e) {
            LOG.debug("Failed: {}:{}", header, txn, e);
            rc.err = (e instanceof KeeperException) ? ((KeeperException) e).code().intValue() : Code.SYSTEMERROR.intValue();
        }
        handleTxnPostProcessing(header, txn, rc, isSubTxn);
        return rc;
    }

    private void processCreateTxn(TxnHeader header, CreateTxn txn, ProcessTxnResult rc) throws KeeperException {
        rc.path = txn.getPath();
        createNode(txn.getPath(), txn.getData(), txn.getAcl(), txn.getEphemeral() ? header.getClientId() : 0, txn.getParentCVersion(), header.getZxid(), header.getTime());
    }

    private void processCreate2Txn(TxnHeader header, CreateTxn txn, ProcessTxnResult rc) throws KeeperException {
        rc.path = txn.getPath();
        Stat s = new Stat();
        createNode(txn.getPath(), txn.getData(), txn.getAcl(), txn.getEphemeral() ? header.getClientId() : 0, txn.getParentCVersion(), header.getZxid(), header.getTime(), s);
        rc.stat = s;
    }

    private void processCreateTTLTxn(TxnHeader header, CreateTTLTxn txn, ProcessTxnResult rc) throws KeeperException {
        rc.path = txn.getPath();
        Stat s = new Stat();
        createNode(txn.getPath(), txn.getData(), txn.getAcl(), EphemeralType.TTL.toEphemeralOwner(txn.getTtl()), txn.getParentCVersion(), header.getZxid(), header.getTime(), s);
        rc.stat = s;
    }

    private void processCreateContainerTxn(TxnHeader header, CreateContainerTxn txn, ProcessTxnResult rc) throws KeeperException {
        rc.path = txn.getPath();
        Stat s = new Stat();
        createNode(txn.getPath(), txn.getData(), txn.getAcl(), EphemeralType.CONTAINER_EPHEMERAL_OWNER, txn.getParentCVersion(), header.getZxid(), header.getTime(), s);
        rc.stat = s;
    }

    private void processSetDataTxn(TxnHeader header, SetDataTxn txn, ProcessTxnResult rc) throws KeeperException {
        rc.path = txn.getPath();
        rc.stat = setData(txn.getPath(), txn.getData(), txn.getVersion(), header.getZxid(), header.getTime());
    }

    private void processCloseSessionTxn(TxnHeader header, Record txn) {
        long sessionId = header.getClientId();
        if (txn instanceof CloseSessionTxn) {
            killSession(sessionId, header.getZxid(), ephemerals.remove(sessionId), ((CloseSessionTxn) txn).getPaths2Delete());
        } else {
            killSession(sessionId, header.getZxid());
        }
    }

    private void processMultiTxn(TxnHeader header, MultiTxn txn, ProcessTxnResult rc) throws IOException, KeeperException {
        rc.multiResult = new ArrayList<>();
        boolean failed = false;
        for (Txn sub : txn.getTxns()) if (sub.getType() == OpCode.error) failed = true;

        for (Txn sub : txn.getTxns()) {
            Supplier<Record> sup = getRecordSupplier(sub.getType());
            if (sup == null) throw new IOException("Invalid Op: " + sub.getType());
            Record rec = (failed && sub.getType() != OpCode.error) ? new ErrorTxn(Code.RUNTIMEINCONSISTENCY.intValue()) : RequestRecord.fromBytes(sub.getData()).readRecord(sup);
            TxnHeader subHdr = new TxnHeader(header.getClientId(), header.getCxid(), header.getZxid(), header.getTime(), sub.getType());
            rc.multiResult.add(processTxn(subHdr, rec, true));
        }
    }

    private Supplier<Record> getRecordSupplier(int type) {
        switch (type) {
            case OpCode.create: case OpCode.create2: return CreateTxn::new;
            case OpCode.createTTL: return CreateTTLTxn::new;
            case OpCode.createContainer: return CreateContainerTxn::new;
            case OpCode.delete: case OpCode.deleteContainer: return DeleteTxn::new;
            case OpCode.setData: return SetDataTxn::new;
            case OpCode.check: return CheckVersionTxn::new;
            case OpCode.error: return ErrorTxn::new;
            default: return null;
        }
    }

    private void handleTxnPostProcessing(TxnHeader header, Record txn, ProcessTxnResult rc, boolean isSubTxn) {
        if (header.getType() == OpCode.create && rc.err == Code.NODEEXISTS.intValue()) {
            try {
                int lastSlash = rc.path.lastIndexOf('/');
                setCversionPzxid(rc.path.substring(0, lastSlash), ((CreateTxn) txn).getParentCVersion(), header.getZxid());
            } catch (NoNodeException e) { rc.err = e.code().intValue(); }
        }
        if (!isSubTxn) {
            if (rc.zxid > lastProcessedZxid) lastProcessedZxid = rc.zxid;
            if (digestFromLoadedSnapshot != null) compareSnapshotDigests(rc.zxid);
            else logZxidDigest(rc.zxid, getTreeDigest());
        }
    }

    // --- Other boilerplate maintained as is for compatibility ---
    public String getMaxPrefixWithQuota(String path) {
        String lastPrefix = pTrie.findMaxPrefix(path);
        return (ROOT_ZOOKEEPER.equals(lastPrefix) || lastPrefix.isEmpty()) ? null : lastPrefix;
    }

    public void addWatch(String basePath, Watcher watcher, int mode) {
        WatcherMode watcherMode = WatcherMode.fromZooDef(mode);
        dataWatches.addWatch(basePath, watcher, watcherMode);
        if (watcherMode != WatcherMode.PERSISTENT_RECURSIVE) childWatches.addWatch(basePath, watcher, watcherMode);
    }

    public byte[] getData(String path, Stat stat, Watcher watcher) throws NoNodeException {
        DataNode n = nodes.get(path);
        if (n == null) throw new NoNodeException();
        byte[] data;
        synchronized (n) {
            n.copyStat(stat);
            if (watcher != null) dataWatches.addWatch(path, watcher);
            data = n.data;
        }
        updateReadStat(path, data == null ? 0 : data.length);
        return data;
    }

    public Stat statNode(String path, Watcher watcher) throws NoNodeException {
        if (watcher != null) dataWatches.addWatch(path, watcher);
        DataNode n = nodes.get(path);
        if (n == null) throw new NoNodeException();
        Stat stat = new Stat();
        synchronized (n) { n.copyStat(stat); }
        updateReadStat(path, 0L);
        return stat;
    }

    public List<String> getChildren(String path, Stat stat, Watcher watcher) throws NoNodeException {
        DataNode n = nodes.get(path);
        if (n == null) throw new NoNodeException();
        List<String> children;
        synchronized (n) {
            if (stat != null) n.copyStat(stat);
            children = new ArrayList<>(n.getChildren());
            if (watcher != null) childWatches.addWatch(path, watcher);
        }
        int bytes = 0;
        for (String child : children) bytes += child.length();
        updateReadStat(path, bytes);
        return children;
    }

    public int getAllChildrenNumber(String path) {
        if (ROOT_ZOOKEEPER.equals(path)) return nodes.size() - 2;
        return (int) nodes.entrySet().parallelStream().filter(entry -> entry.getKey().startsWith(path + "/")).count();
    }

    public Stat setACL(String path, List<ACL> acl, int version) throws NoNodeException {
        DataNode n = nodes.get(path);
        if (n == null) throw new NoNodeException();
        synchronized (n) {
            Stat stat = new Stat();
            aclCache.removeUsage(n.acl);
            nodes.preChange(path, n);
            n.stat.setAversion(version);
            n.acl = aclCache.convertAcls(acl);
            n.copyStat(stat);
            nodes.postChange(path, n);
            return stat;
        }
    }

    public List<ACL> getACL(String path, Stat stat) throws NoNodeException {
        DataNode n = nodes.get(path);
        if (n == null) throw new NoNodeException();
        synchronized (n) {
            if (stat != null) n.copyStat(stat);
            return new ArrayList<>(aclCache.convertLong(n.acl));
        }
    }

    public List<ACL> getACL(DataNode node) {
        synchronized (node) { return aclCache.convertLong(node.acl); }
    }

    public int aclCacheSize() { return aclCache.size(); }

    public static class ProcessTxnResult {
        public long clientId; public int cxid; public long zxid; public int err; public int type;
        public String path; public Stat stat; public List<ProcessTxnResult> multiResult;

        @Override public boolean equals(Object o) {
            if (o instanceof ProcessTxnResult) {
                ProcessTxnResult other = (ProcessTxnResult) o;
                return other.clientId == clientId && other.cxid == cxid;
            }
            return false;
        }
        @Override public int hashCode() { return (int) ((clientId ^ cxid) % Integer.MAX_VALUE); }
    }

    public volatile long lastProcessedZxid = 0;

    void killSession(long session, long zxid) { killSession(session, zxid, ephemerals.remove(session), null); }

    void killSession(long session, long zxid, Set<String> paths2DeleteLocal, List<String> paths2DeleteInTxn) {
        if (paths2DeleteInTxn != null) deleteNodes(session, zxid, paths2DeleteInTxn);
        if (paths2DeleteLocal == null) return;
        if (paths2DeleteInTxn != null) {
            for (String path: paths2DeleteInTxn) paths2DeleteLocal.remove(path);
        }
        deleteNodes(session, zxid, paths2DeleteLocal);
    }

    void deleteNodes(long session, long zxid, Iterable<String> paths2Delete) {
        for (String path : paths2Delete) {
            try {
                deleteNode(path, zxid);
                if (ZKAuditProvider.isAuditEnabled()) ZKAuditProvider.log(ZKAuditProvider.getZKUser(), AuditConstants.OP_DEL_EZNODE_EXP, path, null, null, "0x"+Long.toHexString(session), null, Result.SUCCESS);
            } catch (NoNodeException e) {
                LOG.warn("Ignoring NoNodeException for path {} for dead session 0x{}", path, Long.toHexString(session));
            }
        }
    }

    private void getCounts(String path, Counts counts) {
        DataNode node = getNode(path);
        if (node == null) return;
        String[] children;
        int len;
        synchronized (node) {
            children = node.getChildren().toArray(new String[0]);
            len = (node.data == null ? 0 : node.data.length);
        }
        counts.count += 1;
        counts.bytes += len;
        for (String child : children) getCounts(path + "/" + child, counts);
    }

    private void updateQuotaForPath(String path) {
        Counts c = new Counts();
        getCounts(path, c);
        StatsTrack statsTrack = new StatsTrack();
        statsTrack.setBytes(c.bytes);
        statsTrack.setCount(c.count);
        String statPath = Quotas.statPath(path);
        DataNode node = getNode(statPath);
        if (node == null) return;
        synchronized (node) {
            nodes.preChange(statPath, node);
            node.data = statsTrack.getStatsBytes();
            nodes.postChange(statPath, node);
        }
    }

    private void traverseNode(String path) {
        DataNode node = getNode(path);
        String[] children;
        synchronized (node) { children = node.getChildren().toArray(new String[0]); }
        if (children.length == 0) {
            String endString = "/" + Quotas.limitNode;
            if (path.endsWith(endString)) {
                String realPath = path.substring(QUOTA_ZOOKEEPER.length(), path.indexOf(endString));
                updateQuotaForPath(realPath);
                this.pTrie.addPath(realPath);
            }
            return;
        }
        for (String child : children) traverseNode(path + "/" + child);
    }

    private void setupQuota() {
        DataNode node = getNode(QUOTA_ZOOKEEPER);
        if (node != null) traverseNode(QUOTA_ZOOKEEPER);
    }

    void serializeNode(OutputArchive oa, StringBuilder path) throws IOException {
        String pathString = path.toString();
        DataNode node = getNode(pathString);
        if (node == null) return;
        String[] children;
        DataNode nodeCopy;
        synchronized (node) {
            StatPersisted statCopy = new StatPersisted();
            statCopy.copyFrom(node.stat);
            nodeCopy = new DataNode(node.data, node.acl, statCopy);
            children = node.getChildren().toArray(new String[0]);
        }
        serializeNodeData(oa, pathString, nodeCopy);
        path.append('/');
        int off = path.length();
        for (String child : children) {
            path.delete(off, Integer.MAX_VALUE);
            path.append(child);
            serializeNode(oa, path);
        }
    }

    public void serializeNodeData(OutputArchive oa, String path, DataNode node) throws IOException {
        oa.writeString(path, PATH_KEY);
        oa.writeRecord(node, NODE_KEY);
    }

    public void serializeAcls(OutputArchive oa) throws IOException { aclCache.serialize(oa); }

    public void serializeNodes(OutputArchive oa) throws IOException {
        serializeNode(oa, new StringBuilder());
        if (root != null) oa.writeString(ROOT_ZOOKEEPER, PATH_KEY);
    }

    public void serialize(OutputArchive oa, String tag) throws IOException { serializeAcls(oa); serializeNodes(oa); }

    public void deserialize(InputArchive ia, String tag) throws IOException {
        aclCache.deserialize(ia);
        nodes.clear();
        pTrie.clear();
        nodeDataSize.set(0);
        String path = ia.readString(PATH_KEY);
        while (!ROOT_ZOOKEEPER.equals(path)) {
            deserializeNode(ia, path);
            path = ia.readString(PATH_KEY);
        }
        nodes.putWithoutDigest(ROOT_ZOOKEEPER, root);
        nodeDataSize.set(approximateDataSize());
        setupQuota();
        aclCache.purgeUnused();
    }

    private void deserializeNode(InputArchive ia, String path) throws IOException {
        DataNode node = new DataNode();
        ia.readRecord(node, NODE_KEY);
        nodes.put(path, node);
        synchronized (node) { aclCache.addUsage(node.acl); }
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash == -1) root = node;
        else {
            String parentPath = path.substring(0, lastSlash);
            DataNode parent = nodes.get(parentPath);
            if (parent == null) throw new IOException("Parent not found: " + parentPath);
            parent.addChild(path.substring(lastSlash + 1));
            long owner = node.stat.getEphemeralOwner();
            if (EphemeralType.get(owner) == EphemeralType.CONTAINER) containers.add(path);
            else if (EphemeralType.get(owner) == EphemeralType.TTL) ttls.add(path);
            else if (owner != 0) ephemerals.computeIfAbsent(owner, k -> new HashSet<>()).add(path);
        }
    }

    public synchronized void dumpWatchesSummary(PrintWriter writer) { writer.print(dataWatches.toString()); }
    public synchronized void dumpWatches(PrintWriter writer, boolean byPath) { dataWatches.dumpWatches(writer, byPath); }
    public synchronized WatchesReport getWatches() { return dataWatches.getWatches(); }
    public synchronized WatchesPathReport getWatchesByPath() { return dataWatches.getWatchesByPath(); }
    public synchronized WatchesSummary getWatchesSummary() { return dataWatches.getWatchesSummary(); }

    public void dumpEphemerals(PrintWriter writer) {
        writer.println("Sessions with Ephemerals (" + ephemerals.keySet().size() + "):");
        for (Entry<Long, HashSet<String>> entry : ephemerals.entrySet()) {
            writer.print("0x" + Long.toHexString(entry.getKey()));
            writer.println(":");
            Set<String> tmp = entry.getValue();
            if (tmp != null) synchronized (tmp) { for (String path : tmp) writer.println("\t" + path); }
        }
    }

    public void shutdownWatcher() { dataWatches.shutdown(); childWatches.shutdown(); }

    public Map<Long, Set<String>> getEphemerals() {
        Map<Long, Set<String>> ephemeralsCopy = new HashMap<>();
        for (Entry<Long, HashSet<String>> e : ephemerals.entrySet()) {
            synchronized (e.getValue()) { ephemeralsCopy.put(e.getKey(), new HashSet<>(e.getValue())); }
        }
        return ephemeralsCopy;
    }

    public void removeCnxn(Watcher watcher) { dataWatches.removeWatcher(watcher); childWatches.removeWatcher(watcher); }

    public void setWatches(long relativeZxid, List<String> dataWatches, List<String> existWatches, List<String> childWatches,
                           List<String> persistentWatches, List<String> persistentRecursiveWatches, Watcher watcher) {
        for (String path : dataWatches) {
            DataNode node = getNode(path);
            if (node == null) watcher.process(new WatchedEvent(EventType.NodeDeleted, KeeperState.SyncConnected, path));
            else if (node.stat.getMzxid() > relativeZxid) watcher.process(new WatchedEvent(EventType.NodeDataChanged, KeeperState.SyncConnected, path));
            else this.dataWatches.addWatch(path, watcher);
        }
        for (String path : existWatches) {
            if (getNode(path) != null) watcher.process(new WatchedEvent(EventType.NodeCreated, KeeperState.SyncConnected, path));
            else this.dataWatches.addWatch(path, watcher);
        }
        for (String path : childWatches) {
            DataNode node = getNode(path);
            if (node == null) watcher.process(new WatchedEvent(EventType.NodeDeleted, KeeperState.SyncConnected, path));
            else if (node.stat.getPzxid() > relativeZxid) watcher.process(new WatchedEvent(EventType.NodeChildrenChanged, KeeperState.SyncConnected, path));
            else this.childWatches.addWatch(path, watcher);
        }
        for (String path : persistentWatches) {
            this.childWatches.addWatch(path, watcher, WatcherMode.PERSISTENT);
            this.dataWatches.addWatch(path, watcher, WatcherMode.PERSISTENT);
        }
        for (String path : persistentRecursiveWatches) this.dataWatches.addWatch(path, watcher, WatcherMode.PERSISTENT_RECURSIVE);
    }

    public void setCversionPzxid(String path, int newCversion, long zxid) throws NoNodeException {
        if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        DataNode node = nodes.get(path);
        if (node == null) throw new NoNodeException(path);
        synchronized (node) {
            int targetCVersion = (newCversion == -1) ? node.stat.getCversion() + 1 : newCversion;
            if (targetCVersion > node.stat.getCversion()) {
                nodes.preChange(path, node);
                node.stat.setCversion(targetCVersion);
                node.stat.setPzxid(zxid);
                nodes.postChange(path, node);
            }
        }
    }

    public boolean containsWatcher(String path, WatcherType type, Watcher watcher) {
        switch (type) {
            case Children: return this.childWatches.containsWatcher(path, watcher, WatcherMode.STANDARD);
            case Data: return this.dataWatches.containsWatcher(path, watcher, WatcherMode.STANDARD);
            case Persistent: return this.dataWatches.containsWatcher(path, watcher, WatcherMode.PERSISTENT);
            case PersistentRecursive: return this.dataWatches.containsWatcher(path, watcher, WatcherMode.PERSISTENT_RECURSIVE);
            case Any: return this.childWatches.containsWatcher(path, watcher, null) | this.dataWatches.containsWatcher(path, watcher, null);
            default: return false;
        }
    }

    public boolean removeWatch(String path, WatcherType type, Watcher watcher) {
        switch (type) {
            case Children: return this.childWatches.removeWatcher(path, watcher, WatcherMode.STANDARD);
            case Data: return this.dataWatches.removeWatcher(path, watcher, WatcherMode.STANDARD);
            case Persistent: return this.childWatches.removeWatcher(path, watcher, WatcherMode.PERSISTENT) | this.dataWatches.removeWatcher(path, watcher, WatcherMode.PERSISTENT);
            case PersistentRecursive: return this.dataWatches.removeWatcher(path, watcher, WatcherMode.PERSISTENT_RECURSIVE);
            case Any: return this.childWatches.removeWatcher(path, watcher, null) | this.dataWatches.removeWatcher(path, watcher, null);
            default: return false;
        }
    }

    public ReferenceCountedACLCache getReferenceCountedAclCache() { return aclCache; }
    private void updateReadStat(String path, long bytes) {
        final String namespace = PathUtils.getTopNamespace(path);
        if (namespace != null) ServerMetrics.getMetrics().READ_PER_NAMESPACE.add(namespace, path.length() + bytes + STAT_OVERHEAD_BYTES);
    }
    private void updateWriteStat(String path, long bytes) {
        final String namespace = PathUtils.getTopNamespace(path);
        if (namespace != null) ServerMetrics.getMetrics().WRITE_PER_NAMESPACE.add(namespace, path.length() + bytes);
    }
    private void logZxidDigest(long zxid, long digest) {
        ZxidDigest zxidDigest = new ZxidDigest(zxid, digestCalculator.getDigestVersion(), digest);
        lastProcessedZxidDigest = zxidDigest;
        if (zxidDigest.zxid % DIGEST_LOG_INTERVAL == 0) {
            synchronized (digestLog) { digestLog.add(zxidDigest); if (digestLog.size() > DIGEST_LOG_LIMIT) digestLog.poll(); }
        }
    }

    public boolean serializeZxidDigest(OutputArchive oa) throws IOException {
        if (!ZooKeeperServer.isDigestEnabled()) return false;
        ZxidDigest zxidDigest = lastProcessedZxidDigest == null ? new ZxidDigest() : lastProcessedZxidDigest;
        zxidDigest.serialize(oa);
        return true;
    }

    public boolean deserializeZxidDigest(InputArchive ia, long startZxidOfSnapshot) throws IOException {
        if (!ZooKeeperServer.isDigestEnabled()) return false;
        try {
            ZxidDigest zxidDigest = new ZxidDigest(); zxidDigest.deserialize(ia);
            if (zxidDigest.zxid > 0) digestFromLoadedSnapshot = zxidDigest;
            else digestFromLoadedSnapshot = null;
            if (digestFromLoadedSnapshot != null && digestFromLoadedSnapshot.zxid < startZxidOfSnapshot) digestFromLoadedSnapshot = null;
            return true;
        } catch (EOFException e) { return false; }
    }

    public boolean serializeLastProcessedZxid(final OutputArchive oa) throws IOException {
        if (!ZooKeeperServer.isSerializeLastProcessedZxidEnabled()) return false;
        oa.writeLong(lastProcessedZxid, "lastZxid");
        return true;
    }

    public boolean deserializeLastProcessedZxid(final InputArchive ia)  throws IOException {
        if (!ZooKeeperServer.isSerializeLastProcessedZxidEnabled()) return false;
        try { lastProcessedZxid = ia.readLong("lastZxid"); } catch (final EOFException e) { return false; }
        return true;
    }

    public void compareSnapshotDigests(long zxid) {
        if (zxid == digestFromLoadedSnapshot.zxid) {
            if (digestCalculator.getDigestVersion() != digestFromLoadedSnapshot.digestVersion) { digestFromLoadedSnapshot = null; return; }
            if (getTreeDigest() != digestFromLoadedSnapshot.getDigest()) reportDigestMismatch(zxid);
            digestFromLoadedSnapshot = null;
        } else if (digestFromLoadedSnapshot.zxid != 0 && zxid > digestFromLoadedSnapshot.zxid) rateLogger.rateLimitLog("Digest mismatch expected txn 0x{}", Long.toHexString(digestFromLoadedSnapshot.zxid));
    }

    public boolean compareDigest(TxnHeader header, Record txn, TxnDigest digest) {
        if (!ZooKeeperServer.isDigestEnabled() || digest == null || digestFromLoadedSnapshot != null) return true;
        if (digestCalculator.getDigestVersion() != digest.getVersion()) return true;
        if (digest.getTreeDigest() != getTreeDigest()) {
            reportDigestMismatch(header.getZxid());
            return false;
        }
        return true;
    }

    public void reportDigestMismatch(long zxid) {
        ServerMetrics.getMetrics().DIGEST_MISMATCHES_COUNT.add(1);
        rateLogger.rateLimitLog("Digests are not matching. Value is Zxid.", String.valueOf(zxid));
        for (DigestWatcher watcher : digestWatchers) watcher.process(zxid);
    }

    public long getTreeDigest() { return nodes.getDigest(); }
    public ZxidDigest getLastProcessedZxidDigest() { return lastProcessedZxidDigest; }
    public ZxidDigest getDigestFromLoadedSnapshot() { return digestFromLoadedSnapshot; }
    public void addDigestWatcher(DigestWatcher digestWatcher) { digestWatchers.add(digestWatcher); }
    public List<ZxidDigest> getDigestLog() { synchronized (digestLog) { return new LinkedList<>(digestLog); } }

    public class ZxidDigest {
        long zxid; long digest; int digestVersion;
        ZxidDigest() { this(0, digestCalculator.getDigestVersion(), 0); }
        ZxidDigest(long zxid, int digestVersion, long digest) { this.zxid = zxid; this.digestVersion = digestVersion; this.digest = digest; }
        public void serialize(OutputArchive oa) throws IOException { oa.writeLong(zxid, ZXID_KEY); oa.writeInt(digestVersion, DIGEST_VERSION_KEY); oa.writeLong(digest, DIGEST_KEY); }
        public void deserialize(InputArchive ia) throws IOException {
            zxid = ia.readLong(ZXID_KEY); digestVersion = ia.readInt(DIGEST_VERSION_KEY);
            digest = (digestVersion < 2) ? (ia.readString(DIGEST_KEY) != null ? Long.parseLong(ia.readString(DIGEST_KEY), 16) : 0) : ia.readLong(DIGEST_KEY);
        }
        public long getZxid() { return zxid; }
        public int getDigestVersion() { return digestVersion; }
        public long getDigest() { return digest; }
    }

    public static StatPersisted createStat(long zxid, long time, long ephemeralOwner) {
        StatPersisted stat = new StatPersisted();
        stat.setCtime(time); stat.setMtime(time);
        stat.setCzxid(zxid); stat.setMzxid(zxid); stat.setPzxid(zxid);
        stat.setVersion(0); stat.setAversion(0); stat.setEphemeralOwner(ephemeralOwner);
        return stat;
    }

    static StatPersisted createStat(int version) {
        StatPersisted stat = new StatPersisted();
        stat.setVersion(version);
        return stat;
    }

    private static class Counts { long bytes; int count; }
}