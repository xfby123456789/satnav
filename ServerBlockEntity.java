package com.hmod.create_satnav.blockentity;

import com.hmod.create_satnav.block.ServerBlock;
import com.hmod.create_satnav.network.SatNetworkData;
import com.hmod.create_satnav.registry.ModBlockEntities;
import com.hmod.create_satnav.registry.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务器方块实体（§5）。群级关系存放在 SatNetworkData；
 * 本实体只保留强加载模式、活跃时间、放置者与土豆服务器的火焰转化状态。
 * 耐久机制已整体移除（§本轮8），损毁/修复/损耗逻辑全部不存在。
 */
public class ServerBlockEntity extends BlockEntity {

    /** 全部已加载服务器 BE 登记表，供强加载/火焰转化周期扫描（轻量 tick）。 */
    public static final Map<String, ServerBlockEntity> LIVE = new ConcurrentHashMap<>();

    /** 土豆服务器被点燃后的燃烧时长（毫秒），烧满即转化为烤土豆服务器（§本轮9）。 */
    private static final long BAKE_MS = 10_000L;

    /** §5.1 强加载模式：0=自动 1=强制常加载 2=永不加载。 */
    private int forceMode = -1;
    /** 最近活跃时间（有连接/被操作），用于闲置自动释放加载（§5.1）。 */
    private long lastActiveMs;
    /** 点燃时刻（真实毫秒）：0=未点燃。土豆服务器专用（§本轮9）。 */
    private long igniteAtMs;
    /** 放置者（服务器管理侧权限判定，§3.5【默认】）。 */
    private UUID placedBy;
    private boolean registered;

    public ServerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SERVER.get(), pos, state);
    }

    private void ensureInit() {
        if (forceMode < 0) {
            forceMode = kind() == SatNetworkData.Kind.CREATIVE ? 1 : 0; // 创造默认常加载【默认】
            lastActiveMs = System.currentTimeMillis();
        }
    }

    public SatNetworkData.Kind kind() {
        return ((ServerBlock) getBlockState().getBlock()).kind();
    }

    // ---- 访问器 ----
    public int getForceMode() { ensureInit(); return forceMode; }
    public long getLastActiveMs() { return lastActiveMs; }
    public UUID getPlacedBy() { return placedBy; }
    public void setPlacedBy(UUID id) { ensureInit(); this.placedBy = id; this.setChanged(); }
    public void setForceMode(int mode) { ensureInit(); this.forceMode = mode; this.setChanged(); }

    /** 被操作/有连接时刷新活跃时间（§5.1）。 */
    public void touch() {
        ensureInit();
        this.lastActiveMs = System.currentTimeMillis();
        this.setChanged();
    }

    /** 是否已是烤土豆服务器（读取方块 BAKED 状态）。 */
    public boolean isBaked() {
        return getBlockState().getBlock() instanceof ServerBlock sb && sb.isBaked(getBlockState());
    }

    /** 点燃土豆服务器：记录引燃时刻（重复调用无效果；已烤熟的不再变化）。 */
    public void ignite() {
        ensureInit();
        if (kind() != SatNetworkData.Kind.POTATO || igniteAtMs != 0 || isBaked()) return;
        igniteAtMs = System.currentTimeMillis();
        this.setChanged();
    }

    // ---- 网络登记 ----
    @Override
    public void onLoad() {
        super.onLoad();
        if (getLevel() != null && !getLevel().isClientSide && getLevel() instanceof ServerLevel sl) {
            ensureInit();
            LIVE.put(SatNetworkData.key(sl, getBlockPos()), this);
            if (!registered) {
                registered = true;
                SatNetworkData.get(sl).serverPlaced(sl, getBlockPos(), kind(), placedBy);
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (getLevel() instanceof ServerLevel sl) {
            LIVE.remove(SatNetworkData.key(sl, getBlockPos()));
        }
    }

    /** 方块被破坏/爆炸：退出网络（§5：重放 = 全新设备）。 */
    public void onNetworkRemoved() {
        if (getLevel() instanceof ServerLevel sl) {
            SatNetworkData.get(sl).serverRemoved(sl, getBlockPos());
        }
    }

    // ---- 周期任务（每 20 tick 由 GlobalEvents 调一次）----

    /** 本机强制加载是否已挂上（内存跟踪，键 dim;cx,cz）。 */
    public static final java.util.Set<String> FORCED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private String forceKey() {
        var level = getLevel();
        if (level == null) return "";
        var dim = level instanceof ServerLevel sl ? sl.dimension().location().toString() : level.dimension().location().toString();
        ChunkPos cp = new ChunkPos(getBlockPos());
        return dim + ";" + cp.x + "," + cp.z;
    }

    /** 周期 tick：土豆火焰转化 + 强加载管理。now 为真实毫秒。仅服务端执行。 */
    public void periodicTick(long now) {
        var level = getLevel();
        if (level == null || level.isClientSide) return;
        ServerLevel sl = level instanceof ServerLevel s ? s : null;
        if (sl == null) return;
        ensureInit();

        // 1) §本轮9：土豆服务器火焰转化——接触火方块/火焰蔓延引燃；燃烧 BAKE_MS 后转化
        if (kind() == SatNetworkData.Kind.POTATO && !isBaked()) {
            if (igniteAtMs == 0) {
                if (touchingFire(sl)) ignite();
            } else if (now - igniteAtMs >= BAKE_MS) {
                convertToBaked(sl);
            } else {
                burnParticles(sl);
            }
        }

        // 2) 强加载（§5.1：0=自动 1=强制 2=永不；创造默认强制）
        SatNetworkData data = SatNetworkData.get(sl);
        String k = SatNetworkData.key(sl, getBlockPos());
        boolean desired = switch (forceMode) {
            case 1 -> true;
            case 2 -> false;
            default -> groupActive(sl, data, k, now);
        };
        String fk = forceKey();
        if (desired && !FORCED.contains(fk)) {
            sl.getChunkSource().updateChunkForced(new ChunkPos(getBlockPos()), true);
            FORCED.add(fk);
        } else if (!desired && FORCED.contains(fk)) {
            sl.getChunkSource().updateChunkForced(new ChunkPos(getBlockPos()), false);
            FORCED.remove(fk);
        }
    }

    /** 原版火焰来源判定：六面直接接触火方块/营火，或相邻岩浆（原版点燃源）。 */
    private boolean touchingFire(ServerLevel sl) {
        BlockPos pos = getBlockPos();
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            BlockState s = sl.getBlockState(pos.relative(d));
            if (s.is(BlockTags.FIRE) || s.is(BlockTags.CAMPFIRES)
                    || s.getFluidState().is(FluidTags.LAVA)) return true;
        }
        return false;
    }

    /** 燃烧中的火焰粒子（服务端广播，让「着火燃烧一段时间」肉眼可见）。 */
    private void burnParticles(ServerLevel sl) {
        double x = getBlockPos().getX(), y = getBlockPos().getY(), z = getBlockPos().getZ();
        sl.sendParticles(ParticleTypes.FLAME, x + 0.3, y + 0.7, z + 0.3, 2, 0.3, 0.3, 0.3, 0.01);
        sl.sendParticles(ParticleTypes.SMOKE, x + 0.7, y + 1.0, z + 0.7, 1, 0.2, 0.2, 0.2, 0.01);
    }

    /** 烧满转化：同方块只切 BAKED 状态 → 原位置/原朝向/方块实体内全部组网绑定数据原样保留。 */
    private void convertToBaked(ServerLevel sl) {
        BlockState st = sl.getBlockState(getBlockPos());
        if (!(st.getBlock() instanceof ServerBlock sb) || sb.isBaked(st)) return;
        igniteAtMs = 0;
        sl.setBlockAndUpdate(getBlockPos(), st.setValue(ServerBlock.BAKED, true));
        double x = getBlockPos().getX(), y = getBlockPos().getY(), z = getBlockPos().getZ();
        sl.sendParticles(ParticleTypes.POOF, x + 0.5, y + 0.6, z + 0.5, 12, 0.3, 0.3, 0.3, 0.05);
        touch();
    }

    /** 自动模式：群内有在线成员 → 保持加载并刷新活跃时间；闲置超过阈值天数 → 释放（§5.1）。 */
    private boolean groupActive(ServerLevel sl, SatNetworkData data, String k, long now) {
        String root = data.serverGroup.get(k);
        if (root == null) return false;
        int idleDays = ModConfig.FORCELOAD_IDLE_DAYS.get();
        if (idleDays <= 0) return true;
        for (UUID p : data.playersOfGroup(root)) {
            if (sl.getServer().getPlayerList().getPlayer(p) != null) {
                lastActiveMs = now;
                return true;
            }
        }
        return now - lastActiveMs < idleDays * 86_400_000L;
    }

    // ---- NBT ----
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ensureInit();
        tag.putInt("forceMode", forceMode);
        tag.putLong("lastActive", lastActiveMs);
        if (igniteAtMs != 0) tag.putLong("igniteAt", igniteAtMs);
        if (placedBy != null) tag.putUUID("placedBy", placedBy);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("forceMode")) {
            this.forceMode = tag.getInt("forceMode");
            this.lastActiveMs = tag.getLong("lastActive");
        }
        this.igniteAtMs = tag.getLong("igniteAt");
        if (tag.hasUUID("placedBy")) this.placedBy = tag.getUUID("placedBy");
    }
}
