package com.hmod.create_satnav.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** 创造模式物品栏：本体全部物品；创造服务器只在创造栏出现、生存无法合成（§2）。 */
public final class ModTabs {

    @SuppressWarnings("unchecked")
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "create_satnav");

    public static final Supplier<CreativeModeTab> MAIN = TABS.register("main", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.create_satnav.main"))
                    .icon(() -> new ItemStack(ModItems.SATELLITE.get()))
                    .displayItems((params, output) -> {
                        output.accept(new ItemStack(ModItems.SATELLITE.get()));
                        output.accept(new ItemStack(ModItems.CONNECTOR.get()));
                        output.accept(new ItemStack(ModItems.NAVIGATOR.get()));
                        output.accept(new ItemStack(ModItems.SERVER.get()));
                        output.accept(new ItemStack(ModItems.POTATO_SERVER.get()));
                        output.accept(new ItemStack(ModItems.CREATIVE_SERVER.get()));
                    })
                    .build());

    private ModTabs() {}
}
