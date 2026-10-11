package com.hmod.create_satnav.item;

import net.neoforged.neoforge.network.PacketDistributor;
import com.hmod.create_satnav.network.Payloads;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 导航器（§4）：右键向服务器请求数据并打开导航主界面；
 * 潜行右键打开网络配置视图（同一数据包，openFlags 区分）。
 */
public class NavigatorItem extends Item {

    public NavigatorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            int flags = player.isShiftKeyDown() ? 2 : 1;
            PacketDistributor.sendToServer(new Payloads.RequestNavigatorPayload(flags));
        }
        return InteractionResultHolder.success(stack);
    }
}
