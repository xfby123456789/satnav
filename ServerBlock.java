package com.hmod.create_satnav.block;

import com.hmod.create_satnav.blockentity.ServerBlockEntity;
import com.hmod.create_satnav.logic.ServerInteractions;
import com.hmod.create_satnav.network.SatNetworkData;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 服务器方块（§5：标准 / 土豆 / 创造 共用一套实现，用 Kind 区分）。
 * 空手右键打开服务器配置 GUI（§3.8）。
 * §本轮9：土豆服务器被原版火焰来源引燃（接触火方块、火焰蔓延、燃烧实体攻击引燃等），
 * 燃烧一段时间后转化为「烤土豆服务器」（BAKED 状态），保留原位置与全部组网绑定数据。
 */
public class ServerBlock extends BaseEntityBlock {

    public static final MapCodec<ServerBlock> CODEC =
            simpleCodec(props -> new ServerBlock(SatNetworkData.Kind.STANDARD, props));

    /** 土豆服务器是否已烤熟（烤土豆服务器）。只对土豆服务器实际生效，其余 Kind 恒为 false。 */
    public static final BooleanProperty BAKED = BooleanProperty.create("baked");

    /** 占位形状：略小于整格，观感更像机柜。 */
    private static final VoxelShape SHAPE = Shapes.or(
            box(1, 0, 1, 15, 1, 15),
            box(2, 1, 2, 14, 15, 14),
            box(1, 15, 1, 15, 16, 15));

    private final SatNetworkData.Kind kind;

    public ServerBlock(SatNetworkData.Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(BAKED, false));
    }

    public SatNetworkData.Kind kind() {
        return kind;
    }

    /** 当前状态是否为烤土豆服务器（仅土豆方块可能为 true）。 */
    public boolean isBaked(BlockState state) {
        return kind == SatNetworkData.Kind.POTATO && state.getValue(BAKED);
    }

    @Override
    public MapCodec<ServerBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(BAKED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ServerBlockEntity(pos, state);
    }

    /** 空手右键：打开服务器配置 GUI。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            ServerInteractions.openServerGui((ServerLevel) level, pos, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * 手持物品右键：手持连接器时返回 SKIP_DEFAULT_BLOCK_INTERACTION，
     * 跳过默认方块交互（useWithoutItem 会打开配置 GUI 并抢占右键），
     * 直接交给连接器的 useOn 执行双向配对（§3.2，不区分先后顺序）。
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        return connectorHeld(stack, player)
                ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** 主手持有连接器，或主手空闲且副手持有连接器：均视为"正在用连接器配对"。 */
    private static boolean connectorHeld(ItemStack stack, @Nullable Player player) {
        net.minecraft.world.item.Item connector = com.hmod.create_satnav.registry.ModItems.CONNECTOR.get();
        if (stack.is(connector)) return true;
        return stack.isEmpty() && player != null && player.getOffhandItem().is(connector);
    }

    /** §本轮9：像 TNT 被点燃那样——着火的玩家攻击土豆服务器即引燃它（燃烧计时由方块实体推进）。 */
    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (level.isClientSide || kind != SatNetworkData.Kind.POTATO || isBaked(state)) return;
        if (player.isOnFire() && level.getBlockEntity(pos) instanceof ServerBlockEntity be) {
            be.ignite();
        }
    }

    /** 方块被破坏/爆炸：退出网络，重新放置 = 全新设备（定案）。 */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof ServerBlockEntity be) {
                be.onNetworkRemoved();
            }
            super.onRemove(state, level, pos, newState, moving);
        }
    }
}
