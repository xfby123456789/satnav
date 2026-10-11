package com.hmod.create_satnav.event;

import com.hmod.create_satnav.blockentity.ServerBlockEntity;
import com.hmod.create_satnav.logic.GroupActions;
import com.hmod.create_satnav.network.ModNetwork;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.server.level.ServerPlayer;

/**
 * 全局周期任务：每 20 tick 扫一遍已加载服务器（强加载 + 土豆火焰转化），
 * 每 40 tick 广播一次服务器轮廓颜色；玩家进服/换维度时补发导航器状态。
 */
public final class GlobalEvents {

    private static int tick;

    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tick % 20 != 0) return;
        long now = System.currentTimeMillis();
        for (ServerBlockEntity be : ServerBlockEntity.LIVE.values()) {
            try {
                be.periodicTick(now);
            } catch (Exception ignored) {
                // 单个服务器周期任务异常不影响全网
            }
        }
        if (tick % 2 == 0) {
            ModNetwork.broadcastOutlines(event.getServer());
        }
    }

    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            GroupActions.onRequestNavigator(sp, 0);   // HUD 初始状态
        }
    }

    public static void onPlayerChangedDim(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            GroupActions.onRequestNavigator(sp, 0);
        }
    }

    private GlobalEvents() {}
}
