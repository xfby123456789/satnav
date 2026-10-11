package com.hmod.create_satnav.network;

import com.hmod.create_satnav.CreateSatnav;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 全存档网络数据（§3 组网与连接 / §5 服务器 / §6 卫星服务区）。
 * 保存在主世界 region 下的 SavedData，服务器重启自动恢复全部连接（§3.4 定案）。
 *
 * 数据模型（§3.6 定案）：群级共享——玩家/卫星连接关系全群可见。
 */
public final class SatNetworkData extends SavedData {

    /** 土豆服务器容量（无限槽用）；标准=3、土豆=1、创造=无限。 */
    public static final int CAP_STANDARD = 3;
    public static final int CAP_POTATO = 1;
    public static final int CAP_CREATIVE = 1000000;

    public enum Kind { STANDARD, POTATO, CREATIVE }

    /** 服务群（群主 = 创建并命名该群的玩家，§3.5 定案）。rootKey 为群内一台服务器坐标。 */
    public static final class Group {
        public String rootKey;
        public UUID owner;
        public String name = "";
        public boolean isPublic = true;
        public String password = "";
        public int color = 0x44BBFF;   // 默认蓝色，§3.8 轮廓颜色
        public boolean applyOpen = false; // §3.9 连接申请开关（开启=报备日志自动通过）
        public Group(String rootKey) { this.rootKey = rootKey; }
    }

    /** 导航器目的地（§4.6）。 */
    public static final class Destination {
        public String groupRoot;
        public String name;
        public UUID creator;
        public int x, y, z;
        public Destination() {}
        public Destination(String groupRoot, String name, UUID creator, BlockPos pos) {
            this.groupRoot = groupRoot; this.name = name; this.creator = creator;
            this.x = pos.getX(); this.y = pos.getY(); this.z = pos.getZ();
        }
    }

    // ---- 映射表（键均为 "维度名;x,y,z" 形式）----
    public final Map<String, Group> groups = new LinkedHashMap<>();
    public final Map<String, String> serverGroup = new HashMap<>();      // 服务器 -> 群 root
    public final Map<String, Kind> serverKind = new HashMap<>();         // 服务器 -> 类型
    public final Map<String, String> satelliteGroup = new HashMap<>();   // 卫星 -> 群 root
    public final Map<String, UUID> satelliteOwner = new HashMap<>();     // 卫星 -> 绑定者（归属判定，§3.3）
    public final Set<String> activeSatellites = new HashSet<>();         // 当前处于放置区的卫星
    /**
     * 逻辑在轨卫星键集合（历史存档兼容：旧版本经火箭发射入轨的卫星记录）。
     * 这些卫星在世界中没有任何方块 / 方块实体 / 实体，
     * 只在网络数据里保留一条「已到达轨道高度」的记录；键格式与普通卫星一致
     * （X/Z = 发射点，Y = 轨道高度），服务区判定按 XZ 投影，覆盖范围等价。
     */
    public final Set<String> orbitalSatellites = new HashSet<>();
    public final Map<UUID, String> playerGroup = new HashMap<>();        // 玩家 -> 群 root（§3.6）
    public final Map<UUID, String> playerServer = new HashMap<>();       // 玩家 -> 占用的服务器槽位
    public final Map<UUID, String> playerNames = new HashMap<>();        // 玩家 -> 最近显示名（成员列表离线可见）
    public final Map<String, String> satelliteServer = new HashMap<>();  // 卫星 -> 占用的服务器槽位
    public final List<Destination> destinations = new ArrayList<>();     // 目的地（§4.6）
    public final List<String> applyLog = new ArrayList<>();              // 连接申请报备日志（§3.9，环形上限）

    // ------------------------------------------------------------------
    // 键工具
    // ------------------------------------------------------------------
    /** 键格式：完整维度 id + ';' + 坐标 long（readPos 反向解析用）。 */
    public static String key(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + ";" + pos.asLong();
    }

    /** 键所属维度 id 字符串（客户端/跨维度判定时用）。 */
    public static String dimOf(String key) {
        int i = key.indexOf(';');
        return i < 0 ? key : key.substring(0, i);
    }

    public static String dimOf(ServerLevel level) {
        return level.dimension().location().toString();
    }

    public static UUID ownerOfGroup(Group g) { return g == null ? null : g.owner; }

    /** 六面直接接触的偏移（§5：相邻自动同群，不含斜角）。 */
    private static final int[][] OFFSETS6 = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    // ------------------------------------------------------------------
    // 服务器入网 / 相邻自动组群（§5 通用：六面直接接触自动同群）
    // ------------------------------------------------------------------
    public void serverPlaced(ServerLevel level, BlockPos pos, Kind kind, UUID placer) {
        String k = key(level, pos);
        serverKind.put(k, kind);
        String existing = serverGroup.get(k);
        if (existing != null && groups.containsKey(existing)) {
            // 已有记录（区块重载）：直接沿用
            return;
        }
        // 1) 收集六面相邻、已在网络中的服务器
        List<String> neighbors = new ArrayList<>();
        for (int[] o : OFFSETS6) {
            String nk = key(level, pos.offset(o[0], o[1], o[2]));
            if (serverKind.containsKey(nk) && serverGroup.containsKey(nk)) neighbors.add(nk);
        }
        if (neighbors.isEmpty()) {
            Group g = new Group(k);
            g.owner = placer;
            groups.put(k, g);
            serverGroup.put(k, k);
        } else {
            // 2) 合并：以第一个邻居所在群为主群，其余并入
            String root = serverGroup.get(neighbors.get(0));
            for (String nk : neighbors) {
                String other = serverGroup.get(nk);
                if (!other.equals(root)) mergeGroups(root, other);
            }
            serverGroup.put(k, root);
            Group g = groups.get(root);
            if (g != null && g.owner == null) g.owner = placer;
        }
        setDirty();
    }

    public void serverRemoved(ServerLevel level, BlockPos pos) {
        String k = key(level, pos);
        serverKind.remove(k);
        // 释放该服务器上的槽位占用
        satelliteServer.entrySet().removeIf(e -> e.getValue().equals(k));
        playerServer.entrySet().removeIf(e -> e.getValue().equals(k));
        // 卫星若绑定在本机：保留 satelliteGroup（群内漂移），不改
        serverGroup.remove(k);
        // 若该服务器是群 root：重指派 root 到同群任一服务器，否则解散空群
        Group owned = groups.remove(k);
        if (owned != null) {
            String newRoot = null;
            for (Map.Entry<String, String> e : serverGroup.entrySet()) {
                if (e.getValue().equals(k)) { newRoot = e.getKey(); break; }
            }
            if (newRoot != null) {
                reRootGroup(k, newRoot);
                owned.rootKey = newRoot;
                groups.put(newRoot, owned);
            }
        }
        // 清理无服务器的空群（其连接全部断开）
        cleanupGroups();
        setDirty();
    }

    private void reRootGroup(String oldRoot, String newRoot) {
        serverGroup.replaceAll((s, r) -> r.equals(oldRoot) ? newRoot : r);
        satelliteGroup.replaceAll((s, r) -> r.equals(oldRoot) ? newRoot : r);
        playerGroup.replaceAll((p, r) -> r.equals(oldRoot) ? newRoot : r);
        for (Destination d : destinations) if (d.groupRoot.equals(oldRoot)) d.groupRoot = newRoot;
    }

    private void cleanupGroups() {
        List<String> empty = new ArrayList<>();
        for (String root : groups.keySet()) {
            boolean hasServer = false;
            for (String r : serverGroup.values()) if (r.equals(root)) { hasServer = true; break; }
            if (!hasServer) empty.add(root);
        }
        for (String root : empty) {
            groups.remove(root);
            // 逻辑入轨的卫星没有方块实体、不会被 onRemove 清理：群解散时在此注销（§6.5）
            List<String> sats = new ArrayList<>();
            for (Map.Entry<String, String> e : satelliteGroup.entrySet()) {
                if (root.equals(e.getValue())) sats.add(e.getKey());
            }
            for (String s : sats) {
                if (orbitalSatellites.remove(s)) {
                    activeSatellites.remove(s);
                    satelliteOwner.remove(s);
                    satelliteServer.remove(s);
                }
            }
            satelliteGroup.entrySet().removeIf(e -> e.getValue().equals(root));
            satelliteServer.entrySet().removeIf(e -> isServerOfRoot(e.getValue(), root));
            playerGroup.entrySet().removeIf(e -> e.getValue().equals(root));
            playerServer.entrySet().removeIf(e -> isServerOfRoot(e.getValue(), root));
            destinations.removeIf(d -> d.groupRoot.equals(root));
        }
    }

    private boolean isServerOfRoot(String serverKey, String root) {
        return root.equals(serverGroup.get(serverKey));
    }

    /** 合并两个服务群（§3.2 集群↔集群合并）。dst 群吸收 src 群。 */
    public void mergeGroups(String dstRoot, String srcRoot) {
        Group src = groups.remove(srcRoot);
        if (src == null) return;
        serverGroup.replaceAll((s, r) -> r.equals(srcRoot) ? dstRoot : r);
        satelliteGroup.replaceAll((s, r) -> r.equals(srcRoot) ? dstRoot : r);
        playerGroup.replaceAll((p, r) -> r.equals(srcRoot) ? dstRoot : r);
        for (Destination d : destinations) if (d.groupRoot.equals(srcRoot)) d.groupRoot = dstRoot;
        setDirty();
    }

    /**
     * 拆分：对群内服务器用连接器潜行右键，把与它六面接触、物理相连的服务器簇
     * 独立为新群（§3.2 定案：合并服务群可拆分，保留各自独立群）。
     * BFS 在键空间进行，不要求区块已加载。
     */
    public void splitCluster(ServerLevel level, BlockPos member, UUID operator) {
        String mk = key(level, member);
        String root = serverGroup.get(mk);
        if (root == null) return;                       // 未入网
        Group g = groups.get(root);
        if (g == null) return;
        // 1) BFS 同群 + 六面接触的簇
        Set<String> cluster = new HashSet<>();
        Deque<BlockPos> q = new ArrayDeque<>();
        cluster.add(mk);
        q.add(member);
        while (!q.isEmpty()) {
            BlockPos p = q.poll();
            for (int[] o : OFFSETS6) {
                String nk = key(level, p.offset(o[0], o[1], o[2]));
                if (!cluster.contains(nk) && root.equals(serverGroup.get(nk))) {
                    cluster.add(nk);
                    q.add(SatNetworkData.readPos(nk));
                }
            }
        }
        boolean wholeGroup = true;
        for (Map.Entry<String, String> e : serverGroup.entrySet()) {
            if (e.getValue().equals(root) && !cluster.contains(e.getKey())) { wholeGroup = false; break; }
        }
        if (wholeGroup) return;                         // 整群本就是一个簇，无需拆分
        // 2) 新群沿用原配置（改名/密码/颜色同步语义：拆分后各自独立，先继承再各自修改）
        String newRoot = mk;
        Group ng = new Group(newRoot);
        ng.owner = operator;
        ng.name = g.name;
        ng.isPublic = g.isPublic;
        ng.password = g.password;
        ng.color = g.color;
        ng.applyOpen = g.applyOpen;
        for (String sk : cluster) serverGroup.put(sk, newRoot);
        // 3) 占用落在被拆服务器上的卫星/玩家随之进入新群
        for (Map.Entry<String, String> e : satelliteServer.entrySet()) {
            if (cluster.contains(e.getValue())) {
                satelliteGroup.put(e.getKey(), newRoot);
            }
        }
        for (Map.Entry<UUID, String> e : playerServer.entrySet()) {
            if (cluster.contains(e.getValue())) playerGroup.put(e.getKey(), newRoot);
        }
        groups.put(newRoot, ng);
        // 4) 若旧 root 被拆走：给留下的成员重指派 root
        if (cluster.contains(root)) {
            String remainRoot = null;
            for (Map.Entry<String, String> e : serverGroup.entrySet()) {
                if (e.getValue().equals(root)) { remainRoot = e.getKey(); break; }
            }
            groups.remove(root);
            if (remainRoot != null) {
                Group old = new Group(remainRoot);
                old.owner = g.owner;
                old.name = g.name;
                old.isPublic = g.isPublic;
                old.password = g.password;
                old.color = g.color;
                old.applyOpen = g.applyOpen;
                reRootGroup(root, remainRoot);
                groups.put(remainRoot, old);
            }
        }
        setDirty();
    }

    // ------------------------------------------------------------------
    // 槽位（§3.3：按单台服务器计，占用互斥、不预留）
    // ------------------------------------------------------------------
    public int slotCapacity(String serverKey) {
        Kind kind = serverKind.get(serverKey);
        if (kind == null) return 0;
        return switch (kind) {
            case STANDARD -> CAP_STANDARD;
            case POTATO -> CAP_POTATO;
            case CREATIVE -> CAP_CREATIVE;
        };
    }

    public int slotUsed(String serverKey) {
        int n = 0;
        for (String s : satelliteServer.values()) if (s.equals(serverKey)) n++;
        for (String s : playerServer.values()) if (s.equals(serverKey)) n++;
        return n;
    }

    // ---- 卫星绑定数量上限（本轮定案：土豆 1 颗 / 标准 3 颗 / 创造无限，服务端校验）----
    /** 单台服务器的卫星绑定上限（只看卫星，不含玩家连接槽）。 */
    public int satCapacity(String serverKey) {
        Kind kind = serverKind.get(serverKey);
        if (kind == null) return 0;
        return switch (kind) {
            case STANDARD -> CAP_STANDARD;   // 普通服务器最多 3 颗
            case POTATO -> CAP_POTATO;       // 土豆服务器最多 1 颗
            case CREATIVE -> CAP_CREATIVE;
        };
    }

    /** 当前占用这台服务器的卫星数量。 */
    public int satellitesOnServer(String serverKey) {
        int n = 0;
        for (String s : satelliteServer.values()) if (s.equals(serverKey)) n++;
        return n;
    }

    /** 群内任找一台「卫星绑定未满」的服务器；找不到 = 已达上限，调用方拒绝绑定。 */
    public String findFreeSatServer(String root, java.util.function.Predicate<String> extraFilter) {
        for (Map.Entry<String, String> e : serverGroup.entrySet()) {
            if (!e.getValue().equals(root)) continue;
            if (satellitesOnServer(e.getKey()) < satCapacity(e.getKey())
                    && (extraFilter == null || extraFilter.test(e.getKey()))) {
                return e.getKey();
            }
        }
        return null;
    }

    /** 群内任找一台有空槽的服务器（卫星登记/玩家加入都走这里）。 */
    public String findFreeServer(String root, java.util.function.Predicate<String> extraFilter) {
        for (Map.Entry<String, String> e : serverGroup.entrySet()) {
            if (!e.getValue().equals(root)) continue;
            if (slotUsed(e.getKey()) < slotCapacity(e.getKey())
                    && (extraFilter == null || extraFilter.test(e.getKey()))) {
                return e.getKey();
            }
        }
        return null;
    }

    /** 列出群内已绑定卫星键。 */
    public List<String> satellitesOfGroup(String root) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : satelliteGroup.entrySet()) {
            if (e.getValue().equals(root)) out.add(e.getKey());
        }
        return out;
    }

    /** 列出群内已连接玩家。 */
    public List<UUID> playersOfGroup(String root) {
        List<UUID> out = new ArrayList<>();
        for (Map.Entry<UUID, String> e : playerGroup.entrySet()) {
            if (e.getValue().equals(root)) out.add(e.getKey());
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 卫星激活（§6.1/§6.2）
    // ------------------------------------------------------------------
    public boolean isSatelliteActive(ServerLevel level, BlockPos pos) {
        return activeSatellites.contains(key(level, pos));
    }

    /** 纯逻辑入轨的卫星键（世界中没有对应方块）。 */
    public boolean isOrbital(String satKey) {
        return orbitalSatellites.contains(satKey);
    }

    /** 距 (x,z) 最近的激活卫星（同维度），供 §4.7 就近优先与面板显示信号来源。 */
    public String nearestActiveSatellite(ServerLevel level, double x, double z) {
        String best = null;
        double bestD = Double.MAX_VALUE;
        for (String k : activeSatellites) {
            BlockPos p = readPos(k);
            if (p == null || !dimOf(k).equals(dimOf(level))) continue;
            double d = Math.hypot(p.getX() + 0.5 - x, p.getZ() + 0.5 - z);
            if (d < bestD) { bestD = d; best = k; }
        }
        return best;
    }

    /** §4.1 服务可用判定：XZ 投影柱体，忽略 Y。区块内任意一格落入圆盘即计入（§6.2 默认）。 */
    public boolean inServiceArea(ServerLevel level, double x, double z, int radius) {
        for (String k : activeSatellites) {
            if (!dimOf(k).equals(dimOf(level))) continue;
            BlockPos p = readPos(k);
            if (p == null) continue;
            double dx = p.getX() + 0.5 - x;
            double dz = p.getZ() + 0.5 - z;
            if (dx * dx + dz * dz <= (radius + 0.5) * (radius + 0.5)) return true;
        }
        return false;
    }

    public static BlockPos readPos(String key) {
        try {
            int i = key.indexOf(';');
            return BlockPos.of(Long.parseLong(key.substring(i + 1)));
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // 目的地（§4.6：公开/私人复用服务群名称体系；每人上限 20 可配）
    // ------------------------------------------------------------------
    public List<Destination> destinationsOf(String root) {
        List<Destination> out = new ArrayList<>();
        for (Destination d : destinations) if (d.groupRoot.equals(root)) out.add(d);
        return out;
    }

    public int countByCreator(UUID player) {
        int n = 0;
        for (Destination d : destinations) if (d.creator.equals(player)) n++;
        return n;
    }

    /** 群的展示名：无名时用「未命名@坐标」兜底。 */
    public static String displayName(Group g) {
        if (g == null) return "";
        if (g.name != null && !g.name.isEmpty()) return g.name;
        BlockPos p = readPos(g.rootKey);
        return p == null ? "未命名服务群" : "未命名@" + p.getX() + "," + p.getY() + "," + p.getZ();
    }

    /** 按展示名查找群（公开列表加入用）。返回 root 键，找不到返回 null。 */
    public String findGroupByName(String name) {
        if (name == null || name.isEmpty()) return null;
        for (Map.Entry<String, Group> e : groups.entrySet()) {
            if (displayName(e.getValue()).equals(name)) return e.getKey();
        }
        return null;
    }

    /** 报备日志追加（§3.9：环形上限 50 条）。 */
    public void addApplyEntry(String entry) {
        applyLog.add(entry);
        while (applyLog.size() > 50) applyLog.remove(0);
        setDirty();
    }

    // ------------------------------------------------------------------
    // 持久化
    // ------------------------------------------------------------------
    private static final SavedData.Factory<SatNetworkData> FACTORY =
            new SavedData.Factory<>(SatNetworkData::new, SatNetworkData::load);

    public static SatNetworkData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, "create_satnav_network");
    }

    private SatNetworkData() {}

    private static void putStrings(ListTag list, Iterable<String> items) {
        for (String s : items) list.add(net.minecraft.nbt.StringTag.valueOf(s));
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        // groups
        ListTag gl = new ListTag();
        for (Group g : groups.values()) {
            CompoundTag c = new CompoundTag();
            c.putString("root", g.rootKey);
            if (g.owner != null) c.putUUID("owner", g.owner);
            c.putString("name", g.name);
            c.putBoolean("pub", g.isPublic);
            c.putString("pwd", g.password);
            c.putInt("color", g.color);
            c.putBoolean("apply", g.applyOpen);
            gl.add(c);
        }
        tag.put("groups", gl);
        // map: serverGroup / satelliteGroup / satelliteOwner / playerGroup / playerServer / satelliteServer
        tag.put("serverGroup", mapToStringTag(serverGroup));
        tag.put("satelliteGroup", mapToStringTag(satelliteGroup));
        tag.put("playerGroup", mapUuidToStringTag(playerGroup));
        tag.put("playerServer", mapUuidToStringTag(playerServer));
        tag.put("satelliteServer", mapToStringTag(satelliteServer));
        tag.put("satelliteOwner", mapUuidToStringTag2(satelliteOwner));
        ListTag sl = new ListTag();
        for (Map.Entry<String, Kind> e : serverKind.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("k", e.getKey());
            c.putString("kind", e.getValue().name());
            sl.add(c);
        }
        tag.put("servers", sl);
        ListTag al = new ListTag();
        for (String k : activeSatellites) al.add(net.minecraft.nbt.StringTag.valueOf(k));
        tag.put("activeSats", al);
        ListTag ol = new ListTag();                        // §6.5 逻辑在轨卫星（无方块/无实体）
        for (String k : orbitalSatellites) ol.add(net.minecraft.nbt.StringTag.valueOf(k));
        tag.put("orbitalSats", ol);
        ListTag dl = new ListTag();
        for (Destination d : destinations) {
            CompoundTag c = new CompoundTag();
            c.putString("g", d.groupRoot);
            c.putString("n", d.name);
            c.putUUID("c", d.creator);
            c.putInt("x", d.x); c.putInt("y", d.y); c.putInt("z", d.z);
            dl.add(c);
        }
        tag.put("dests", dl);
        ListTag logl = new ListTag();
        putStrings(logl, applyLog);
        tag.put("applyLog", logl);
        return tag;
    }

    private static ListTag mapToStringTag(Map<String, String> m) {
        ListTag l = new ListTag();
        for (Map.Entry<String, String> e : m.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("a", e.getKey());
            c.putString("b", e.getValue());
            l.add(c);
        }
        return l;
    }

    private static ListTag mapUuidToStringTag(Map<UUID, String> m) {
        ListTag l = new ListTag();
        for (Map.Entry<UUID, String> e : m.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("a", e.getKey());
            c.putString("b", e.getValue());
            l.add(c);
        }
        return l;
    }

    private static ListTag mapUuidToStringTag2(Map<String, UUID> m) {
        ListTag l = new ListTag();
        for (Map.Entry<String, UUID> e : m.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("a", e.getKey());
            c.putUUID("b", e.getValue());
            l.add(c);
        }
        return l;
    }

    private static SatNetworkData load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        SatNetworkData d = new SatNetworkData();
        for (Tag t : tag.getList("groups", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            Group g = new Group(c.getString("root"));
            if (c.hasUUID("owner")) g.owner = c.getUUID("owner");
            g.name = c.getString("name");
            g.isPublic = c.getBoolean("pub");
            g.password = c.getString("pwd");
            g.color = c.getInt("color");
            g.applyOpen = c.getBoolean("apply");
            d.groups.put(g.rootKey, g);
        }
        for (Tag t : tag.getList("serverGroup", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            d.serverGroup.put(c.getString("a"), c.getString("b"));
        }
        for (Tag t : tag.getList("satelliteGroup", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            d.satelliteGroup.put(c.getString("a"), c.getString("b"));
        }
        for (Tag t : tag.getList("playerGroup", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            d.playerGroup.put(c.getUUID("a"), c.getString("b"));
        }
        for (Tag t : tag.getList("playerServer", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            d.playerServer.put(c.getUUID("a"), c.getString("b"));
        }
        for (Tag t : tag.getList("satelliteServer", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            d.satelliteServer.put(c.getString("a"), c.getString("b"));
        }
        for (Tag t : tag.getList("satelliteOwner", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            d.satelliteOwner.put(c.getString("a"), c.getUUID("b"));
        }
        for (Tag t : tag.getList("servers", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            d.serverKind.put(c.getString("k"), Kind.valueOf(c.getString("kind")));
        }
        for (Tag t : tag.getList("activeSats", Tag.TAG_STRING)) d.activeSatellites.add(((net.minecraft.nbt.StringTag) t).getAsString());
        for (Tag t : tag.getList("orbitalSats", Tag.TAG_STRING)) {
            String k = ((net.minecraft.nbt.StringTag) t).getAsString();
            d.orbitalSatellites.add(k);
            d.activeSatellites.add(k);            // 逻辑在轨 = 一直生效，无需方块实体重新判定
        }
        for (Tag t : tag.getList("dests", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            Destination dest = new Destination(c.getString("g"), c.getString("n"), c.getUUID("c"), new BlockPos(c.getInt("x"), c.getInt("y"), c.getInt("z")));
            d.destinations.add(dest);
        }
        for (Tag t : tag.getList("applyLog", Tag.TAG_STRING)) d.applyLog.add(((net.minecraft.nbt.StringTag) t).getAsString());
        return d;
    }
}
