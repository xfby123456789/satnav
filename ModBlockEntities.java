package com.hmod.create_satnav.registry;

import com.hmod.create_satnav.blockentity.SatelliteBlockEntity;
import com.hmod.create_satnav.blockentity.ServerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 方块实体注册。 */
public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "create_satnav");

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SatelliteBlockEntity>> SATELLITE =
            BLOCK_ENTITIES.register("satellite", () ->
                    BlockEntityType.Builder.of(SatelliteBlockEntity::new, ModBlocks.SATELLITE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ServerBlockEntity>> SERVER =
            BLOCK_ENTITIES.register("server", () ->
                    BlockEntityType.Builder.of(ServerBlockEntity::new,
                            ModBlocks.SERVER.get(), ModBlocks.POTATO_SERVER.get(), ModBlocks.CREATIVE_SERVER.get())
                            .build(null));

    private ModBlockEntities() {}
}
