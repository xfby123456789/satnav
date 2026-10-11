package com.hmod.create_satnav.logic;

import com.hmod.create_satnav.blockentity.SatelliteBlockEntity;
import com.hmod.create_satnav.blockentity.ServerBlockEntity;
import com.hmod.create_satnav.network.SatNetworkData;
import com.hmod.create_satnav.network.SatelliteLink;
import com.hmod.create_satnav.registry.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 连接器两步配对（§3.2，不区分先后顺序）：服务器与卫星谁先点都完成绑定；
 * 先点一台服务器、再点另一台服务器 = 两群合并。潜行右键服务器 = 拆分该物理簇；
 * 潜行右键卫星 = 解绑。超时（配置秒数）自动作废。
 */
public final class PairingManager {

    /** 暂存的第一击：可能是服务器（存群键），也可能是卫星（存维度+坐标）。 */
    private static final class Pending {
        final String serverKey;                  // 非空 = 先点了服务器
        final ResourceKey<Level> satDim;         // 非空 = 先点了卫星
        final BlockPos satPos;
        long at = System.currentTimeMillis();
        Pending(String serverKey, ResourceKey<Level> satDim, BlockPos satPos) {
            this.serverKey = serverKey; this.satDim = satDim; this.satPos = satPos;
        }
        static Pending server(String key) { return new Pending(key, null, null); }
        static Pending satellite(ResourceKey<Level> dim, BlockPos pos) { return new Pending(null, dim, pos); }
        boolean isSatellite() { return satPos != null; }
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    /** 右键服务器方块。 */
    public static void onServer(ServerPlayer sp, ServerLevel level, BlockPos pos) {
        UUID uuid = sp.getUUID();
        SatNetworkData data = SatNetworkData.get(level);
        String k = SatNetworkData.key(level, pos);
        String root = data.serverGroup.get(k);
        if (root == null) {
            sp.displayClientMessage(Component.translatable("msg.create_satnav.pair_no_group"), true);
            return;
        }
        SatNetworkData.Group g = data.groups.get(root);
        if (g != null && !ServerInteractions.canManage(sp, g,
                level.getBlockEntity(pos) instanceof ServerBlockEntity be ? be : null)) {
            sp.displayClientMessage(Component.translatable("msg.create_satnav.no_perm"), true);
            return;
        }
        if (sp.isShiftKeyDown()) {
            // 拆分：把与它物理接触的同群簇独立成群（§3.2）
            data.splitCluster(level, pos, uuid);
            sp.displayClientMessage(Component.translatable("msg.create_satnav.split_done"), true);
            return;
        }
        Pending p = take(uuid);
        if (p == null) {
            PENDING.put(uuid, Pending.server(k));
            sp.displayClientMessage(Component.translatable("msg.create_satnav.pair_first"), true);
            if (level.getBlockEntity(pos) instanceof ServerBlockEntity be) be.touch();
            return;
        }
        if (p.isSatellite()) {
            // 先点了卫星、现在点服务器：反向完成绑定
            ServerLevel satLevel = sp.server.getLevel(p.satDim);
            if (satLevel == null || !(satLevel.getBlockEntity(p.satPos) instanceof SatelliteBlockEntity sbe)
                    || sbe.getLink() != null && sbe.getLink().bound()) {
                sp.displayClientMessage(Component.translatable("msg.create_satnav.pair_expired"), true);
                return;
            }
            bindSatellite(sp, data, root, sbe);
            if (level.getBlockEntity(pos) instanceof ServerBlockEntity be) be.touch();
            return;
        }
        // 第二步点在另一台服务器：两群合并（§3.2 集群↔集群）
        String rootA = data.serverGroup.get(p.serverKey);
        if (rootA == null) {
            sp.displayClientMessage(Component.translatable("msg.create_satnav.pair_expired"), true);
            return;
        }
        if (!rootA.equals(root)) {
            data.mergeGroups(rootA, root);
            sp.displayClientMessage(Component.translatable("msg.create_satnav.merged"), true);
        } else {
            sp.displayClientMessage(Component.translatable("msg.create_satnav.pair_same_group"), true);
        }
        if (level.getBlockEntity(pos) instanceof ServerBlockEntity be) be.touch();
    }

    /** 右键卫星方块。 */
    public static void onSatellite(ServerPlayer sp, ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof SatelliteBlockEntity be)) return;
        UUID uuid = sp.getUUID();
        SatNetworkData data = SatNetworkData.get(level);
        if (sp.isShiftKeyDown()) {
            // 解绑：清除绑定关系（归属人本人或群主可操作）
            SatelliteLink link = be.getLink();
            if (link == null || !link.bound()) {
                sp.displayClientMessage(Component.translatable("msg.create_satnav.sat_unbound"), true);
                return;
            }
            SatNetworkData.Group g = data.groups.get(link.groupRoot());
            if (g != null && !uuid.equals(link.owner()) && !uuid.equals(g.owner) && !sp.isCreative()) {
                sp.displayClientMessage(Component.translatable("msg.create_satnav.no_perm"), true);
                return;
            }
            be.setLink(null);
            sp.displayClientMessage(Component.translatable("msg.create_satnav.sat_unbound_done"), true);
            return;
        }
        if (be.getLink() != null && be.getLink().bound()) {
            sp.displayClientMessage(Component.translatable("msg.create_satnav.sat_already_bound",
                    SatNetworkData.displayName(data.groups.get(be.getLink().groupRoot()))), true);
            return;
        }
        Pending p = take(uuid);
        if (p == null || p.isSatellite()) {
            // 第一击点卫星（或改选另一颗卫星）：暂存，等待第二步点服务器
            PENDING.put(uuid, Pending.satellite(level.dimension(), pos));
            sp.displayClientMessage(Component.literal("已选取卫星：再点一台服务器完成绑定"), true);
            return;
        }
        // 先点了服务器、现在点卫星：按原顺序完成绑定
        String root = data.serverGroup.get(p.serverKey);
        if (root == null || !data.groups.containsKey(root)) {
            sp.displayClientMessage(Component.translatable("msg.create_satnav.pair_expired"), true);
            return;
        }
        bindSatellite(sp, data, root, be);
    }

    /** 把卫星绑定进服务群（双向共用）：写链、登记归属、占用一台有空槽的服务器（§3.3），
     *  绑定当场即激活工作（§本轮4，不再需要送入高空发射流程）。
     *  服务端数量校验：土豆服务器最多 1 颗卫星、标准服务器最多 3 颗，达上限拒绝绑定。 */
    private static void bindSatellite(ServerPlayer sp, SatNetworkData data, String root, SatelliteBlockEntity be) {
        UUID uuid = sp.getUUID();
        String server = data.findFreeSatServer(root, null);
        if (server == null) {
            sp.displayClientMessage(Component.literal("绑定数量已达上限：土豆服务器最多绑定 1 颗卫星，标准服务器最多 3 颗"), true);
            return;   // 不写链、不登记：完全拒绝
        }
        be.setLink(new SatelliteLink(root, uuid));
        data.satelliteGroup.put(be.networkKey(), root);
        data.satelliteOwner.put(be.networkKey(), uuid);
        data.satelliteServer.put(be.networkKey(), server);
        data.setDirty();
        sp.displayClientMessage(Component.translatable("msg.create_satnav.sat_bound",
                SatNetworkData.displayName(data.groups.get(root))), true);
    }

    /** 右键空气：清空配对暂存。 */
    public static void clear(ServerPlayer sp) {
        if (take(sp.getUUID()) != null) {
            sp.displayClientMessage(Component.translatable("msg.create_satnav.pair_cleared"), true);
        }
    }

    /** 取出并作废暂存（含超时判断）。 */
    private static Pending take(UUID uuid) {
        Pending p = PENDING.get(uuid);
        if (p == null) return null;
        PENDING.remove(uuid);
        long timeoutMs = ModConfig.PAIRING_TIMEOUT_SEC.get() * 1000L;
        if (System.currentTimeMillis() - p.at > timeoutMs) {
            return null;   // 已超时：视作不存在（调用方得到"配对过期"反馈）
        }
        return p;
    }

    private PairingManager() {}
}
