package com.hmod.create_satnav.blockentity;

import com.hmod.create_satnav.block.SatelliteBlock;
import com.hmod.create_satnav.network.SatNetworkData;
import com.hmod.create_satnav.network.SatelliteLink;
import com.hmod.create_satnav.registry.ModBlockEntities;
import com.hmod.create_satnav.registry.ModComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 卫星方块实体（§6）。持有绑定关系（groupRoot+owner）；
 * 激活判定（§本轮4）：已绑定服务群且群仍存在 → 立即激活工作，不再需要送入高空。
 * 绑定关系通过数据组件跟随掉落物流转（§3.1）。
 */
public class SatelliteBlockEntity extends BlockEntity {

    private SatelliteLink link;      // 绑定关系（地面用连接器与服务器配对后写入）
    private boolean active;          // 当前是否处于激活态
    private int tickCounter = 19;    // 首个 tick 即判定

    public SatelliteBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SATELLITE.get(), pos, state);
    }

    @Nullable
    public SatelliteLink getLink() { return link; }

    public void setLink(SatelliteLink link) {
        this.link = link;
        setChanged();
        if (getLevel() instanceof ServerLevel sl) {
            refreshRegistration(sl);
            updateActivation(sl, getBlockState());   // §本轮4：绑定/解绑当场生效，不等周期 tick
        }
    }

    public boolean isActive() { return active; }

    /** 卫星键（供 PairingManager 与网络数据共用）。 */
    public String networkKey() {
        return getLevel() instanceof ServerLevel sl ? SatNetworkData.key(sl, getBlockPos()) : "";
    }

    /** 区块加载时补登记：绑定关系指向的群若仍存在，把卫星重新挂回服务区/网络。 */
    @Override
    public void onLoad() {
        super.onLoad();
        if (getLevel() instanceof ServerLevel sl) {
            refreshRegistration(sl);
            updateActivation(sl, sl.getBlockState(getBlockPos()));
        }
    }

    /** 网络登记：satelliteGroup/satelliteOwner 与激活集合维护。 */
    public void refreshRegistration(ServerLevel level) {
        SatNetworkData data = SatNetworkData.get(level);
        String k = networkKey();
        if (k.isEmpty()) return;
        if (link != null && link.bound() && data.groups.containsKey(link.groupRoot())) {
            data.satelliteGroup.put(k, link.groupRoot());
            data.satelliteOwner.put(k, link.owner());
            // 重新放置/区块重载：若槽位丢了，找回一个空槽（§3.3）
            if (!data.satelliteServer.containsKey(k)) {
                String server = data.findFreeServer(link.groupRoot(), null);
                if (server != null) data.satelliteServer.put(k, server);
            }
            data.setDirty();
        } else if (link == null || !link.bound()) {
            data.satelliteGroup.remove(k);
            data.satelliteOwner.remove(k);
            // 解绑即释放服务器槽位占用：否则土豆服务器(上限1)会被已解绑卫星的死占用顶满，
            // 服务端「达到上限拒绝绑定」判定失准（§绑定数量限制）
            data.satelliteServer.remove(k);
            data.setDirty();
        }
    }

    /** 服务端周期 tick（每秒一次兜底判定：群解散/绑定变化时自动激活/失活）。 */
    public static void serverTick(Level level, BlockPos pos, BlockState state, SatelliteBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) return;
        if (++be.tickCounter >= 20) {
            be.tickCounter = 0;
            be.updateActivation(sl, state);
        }
    }

    /** §本轮4 定案：绑定即激活；解绑或群解散即失活。不再校验高度/维度。 */
    public void updateActivation(ServerLevel level, BlockState state) {
        boolean shouldActive = link != null && link.bound()
                && SatNetworkData.get(level).groups.containsKey(link.groupRoot());
        if (shouldActive == active && state.getValue(SatelliteBlock.ACTIVE) == shouldActive) return;
        active = shouldActive;
        SatNetworkData data = SatNetworkData.get(level);
        String k = networkKey();
        if (shouldActive) {
            data.activeSatellites.add(k);
            level.setBlockAndUpdate(getBlockPos(), state.setValue(SatelliteBlock.ACTIVE, true));
        } else {
            data.activeSatellites.remove(k);
            level.setBlockAndUpdate(getBlockPos(), state.setValue(SatelliteBlock.ACTIVE, false));
        }
        data.setDirty();
        setChanged();
    }

    // ---- NBT + 数据组件流转 ----
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("active", active);
        if (link != null) {
            CompoundTag lc = new CompoundTag();
            lc.putString("root", link.groupRoot());
            lc.putUUID("owner", link.owner());
            tag.put("link", lc);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.active = tag.getBoolean("active");
        if (tag.contains("link", 10)) {
            CompoundTag lc = tag.getCompound("link");
            this.link = new SatelliteLink(lc.getString("root"), lc.getUUID("owner"));
        }
    }

    /** 掉落/收纳时把绑定关系写入组件（§3.1 全程保持）。 */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        if (link != null) builder.set(ModComponents.SATELLITE_LINK.get(), link);
    }

    /** 从物品放置回来时恢复绑定关系。 */
    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput componentInput) {
        SatelliteLink l = componentInput.get(ModComponents.SATELLITE_LINK.get());
        if (l != null) this.link = l;
    }
}
