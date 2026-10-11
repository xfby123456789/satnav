package com.hmod.create_satnav.registry;

import com.hmod.create_satnav.block.SatelliteBlock;
import com.hmod.create_satnav.block.ServerBlock;
import com.hmod.create_satnav.network.SatNetworkData;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 方块注册：卫星 + 三款服务器（§2/§5/§6）。 */
public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("create_satnav");

    /** 卫星（§6）。激活状态用 ACTIVE 属性驱动发光。 */
    public static final DeferredBlock<SatelliteBlock> SATELLITE = BLOCKS.register("satellite",
            () -> new SatelliteBlock(BlockBehaviour.Properties.of()
                    .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                    .strength(3.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(SatelliteBlock.ACTIVE) ? 8 : 0)));

    /** 标准服务器（§5）。 */
    public static final DeferredBlock<ServerBlock> SERVER = BLOCKS.register("server",
            () -> new ServerBlock(SatNetworkData.Kind.STANDARD, serverProps()));

    /** 土豆服务器（§5）。 */
    public static final DeferredBlock<ServerBlock> POTATO_SERVER = BLOCKS.register("potato_server",
            () -> new ServerBlock(SatNetworkData.Kind.POTATO, serverProps()));

    /** 创造服务器（§5：仅创造物品栏，无法合成）。 */
    public static final DeferredBlock<ServerBlock> CREATIVE_SERVER = BLOCKS.register("creative_server",
            () -> new ServerBlock(SatNetworkData.Kind.CREATIVE, serverProps()));

    private static BlockBehaviour.Properties serverProps() {
        return BlockBehaviour.Properties.of()
                .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                .strength(3.0F, 6.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL);
    }

    private ModBlocks() {}
}
