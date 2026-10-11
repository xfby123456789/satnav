package com.hmod.create_satnav;

import com.hmod.create_satnav.network.ModNetwork;
import com.hmod.create_satnav.registry.ModBlockEntities;
import com.hmod.create_satnav.registry.ModBlocks;
import com.hmod.create_satnav.registry.ModComponents;
import com.hmod.create_satnav.registry.ModConfig;
import com.hmod.create_satnav.registry.ModItems;
import com.hmod.create_satnav.registry.ModTabs;
import com.hmod.create_satnav.event.GlobalEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 卫星导航（SatNav）主入口，独立模组，不依赖其他模组。
 * modid 固定为 create_satnav；游戏版本 1.21.1 / NeoForge。
 */
@Mod(CreateSatnav.MOD_ID)
public final class CreateSatnav {

    public static final String MOD_ID = "create_satnav";

    public CreateSatnav(IEventBus modBus, ModContainer container) {
        // 配置（§9：server 侧 + client 侧）
        ModConfig.register(container);
        // 注册顺序：方块 → 方块实体 → 物品 → 数据组件 → 创造标签页
        ModBlocks.BLOCKS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModComponents.COMPONENTS.register(modBus);
        ModTabs.TABS.register(modBus);
        // 网络消息（自定义 payload）
        modBus.addListener(ModNetwork::onRegisterPayloadHandlers);
        // 游戏总线：强加载/火焰转化周期扫描、轮廓同步、进服与换维度补发
        NeoForge.EVENT_BUS.addListener(GlobalEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(GlobalEvents::onPlayerJoin);
        NeoForge.EVENT_BUS.addListener(GlobalEvents::onPlayerChangedDim);
    }
}
