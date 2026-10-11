package com.hmod.create_satnav;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * 客户端入口（只在客户端加载）：注册界面/HUD/渲染阶段回调并注入 S2C 处理。
 * 专用服务端不会实例化本类，因此不触碰任何客户端类。
 */
@Mod(value = CreateSatnav.MOD_ID, dist = Dist.CLIENT)
public final class CreateSatnavClient {

    public CreateSatnavClient(IEventBus modBus) {
        com.hmod.create_satnav.client.ModClient.init(modBus);
    }
}
