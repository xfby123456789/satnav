package com.hmod.create_satnav.block;

import com.hmod.create_satnav.blockentity.SatelliteBlockEntity;
import com.hmod.create_satnav.network.SatNetworkData;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.jetbrains.annotations.Nullable;

/**
 * 卫星方块（§6）。放在地面即可用连接器与服务器绑定，绑定成功后立即激活工作；
 * 解绑或所在服务群解散时自动失活；绑定关系不受影响。
 */
public class SatelliteBlock extends BaseEntityBlock {

    /** 激活状态（已绑定服务群且群仍存在）。 */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public static final MapCodec<SatelliteBlock> CODEC = simpleCodec(SatelliteBlock::new);

    public SatelliteBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    public MapCodec<SatelliteBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SatelliteBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // 每秒兜底重算一次激活状态（群解散/跨维度加载等场景）
        return level.isClientSide ? null :
                createTickerHelper(type,
                        com.hmod.create_satnav.registry.ModBlockEntities.SATELLITE.get(),
                        SatelliteBlockEntity::serverTick);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState();
    }

    /**
     * 卫星方块被移除（破坏回收）：退出激活集合与网络登记，
     * 但绑定关系仍留在掉落物上（§3.1 全程保持），落地重新放置后自动恢复。
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            SatNetworkData data = SatNetworkData.get((ServerLevel) level);
            String k = SatNetworkData.key((ServerLevel) level, pos);
            data.activeSatellites.remove(k);
            data.satelliteGroup.remove(k);
            data.satelliteOwner.remove(k);
            data.satelliteServer.remove(k);
            data.setDirty();
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
