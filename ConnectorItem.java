package com.hmod.create_satnav.item;

import com.hmod.create_satnav.block.SatelliteBlock;
import com.hmod.create_satnav.block.ServerBlock;
import com.hmod.create_satnav.logic.PairingManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 连接器（§3.2）：两步配对服务器↔服务器（合并）/ 服务器→卫星（绑定）；
 * 潜行右键服务器=拆分物理簇，潜行右键卫星=解绑。
 */
public class ConnectorItem extends Item {

    public ConnectorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState state = level.getBlockState(pos);
        boolean ours = state.getBlock() instanceof ServerBlock || state.getBlock() instanceof SatelliteBlock;
        if (!ours) return InteractionResult.PASS;
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.CONSUME;
        if (state.getBlock() instanceof ServerBlock) {
            PairingManager.onServer((net.minecraft.server.level.ServerPlayer) player,
                    (net.minecraft.server.level.ServerLevel) level, pos);
        } else {
            PairingManager.onSatellite((net.minecraft.server.level.ServerPlayer) player,
                    (net.minecraft.server.level.ServerLevel) level, pos);
        }
        return InteractionResult.SUCCESS;
    }

    /** 右键空气：作废配对暂存。 */
    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                    new com.hmod.create_satnav.network.Payloads.PairClearPayload());
        }
        return net.minecraft.world.InteractionResultHolder.success(stack);
    }
}
