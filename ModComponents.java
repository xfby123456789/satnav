package com.hmod.create_satnav.registry;

import com.hmod.create_satnav.network.SatelliteLink;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 数据组件：绑定关系跟随物品/方块实体流转（§3.1 地面绑定、全程保持）。 */
public final class ModComponents {

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents("create_satnav");

    /** 卫星的绑定信息（groupRoot 群键 + 绑定者 UUID），存于卫星方块实体与掉落物。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SatelliteLink>> SATELLITE_LINK =
            COMPONENTS.registerComponentType("satellite_link",
                    b -> b.persistent(SatelliteLink.CODEC).networkSynchronized(SatelliteLink.STREAM_CODEC));

    private ModComponents() {}
}
