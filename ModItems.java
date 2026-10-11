package com.hmod.create_satnav.registry;

import com.hmod.create_satnav.item.ConnectorItem;
import com.hmod.create_satnav.item.NavigatorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 物品注册（§2）。命名空间统一 create_satnav。 */
public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("create_satnav");

    // ---- 方块对应物品 ----
    public static final DeferredItem<BlockItem> SATELLITE = ITEMS.register("satellite",
            () -> new BlockItem(ModBlocks.SATELLITE.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> SERVER = ITEMS.register("server",
            () -> new BlockItem(ModBlocks.SERVER.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> POTATO_SERVER = ITEMS.register("potato_server",
            () -> new BlockItem(ModBlocks.POTATO_SERVER.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> CREATIVE_SERVER = ITEMS.register("creative_server",
            () -> new BlockItem(ModBlocks.CREATIVE_SERVER.get(), new Item.Properties()));

    // ---- 功能物品 ----
    /** 连接器：两步配对（§3.2）。 */
    public static final DeferredItem<Item> CONNECTOR = ITEMS.register("connector",
            () -> new ConnectorItem(new Item.Properties().stacksTo(1)));

    /** 导航器：网络配置 / 导航主界面（§4）。 */
    public static final DeferredItem<Item> NAVIGATOR = ITEMS.register("navigator",
            () -> new NavigatorItem(new Item.Properties().stacksTo(1)));

    private ModItems() {}
}
