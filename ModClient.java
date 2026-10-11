package com.hmod.create_satnav.client;

import com.hmod.create_satnav.CreateSatnav;
import com.hmod.create_satnav.client.gui.ModSettingsScreen;
import com.hmod.create_satnav.client.hud.SatHud;
import com.hmod.create_satnav.client.render.OutlineRender;
import com.hmod.create_satnav.network.ClientBridge;
import com.hmod.create_satnav.network.Payloads;
import com.hmod.create_satnav.registry.ModConfig;
import com.hmod.create_satnav.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/** 客户端入口：注入 S2C 处理、注册 HUD、渲染阶段与轮询；设置界面与回放屏蔽。 */
public final class ModClient {

    private static int tick;

    public static ResourceLocation rl(String name) {
        return ResourceLocation.fromNamespaceAndPath(CreateSatnav.MOD_ID, name);
    }

    public static void init(IEventBus modBus) {
        ClientBridge.install(new ClientPayload());
        modBus.addListener((RegisterGuiLayersEvent e) ->
                e.registerAboveAll(rl("satnav_hud"), SatHud::render));
        NeoForge.EVENT_BUS.addListener(ModClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(OutlineRender::onStage);
        // 模组设置界面（模组列表 → 卫星导航 →「配置」按钮）
        ModLoadingContext.get().getActiveContainer().registerExtensionPoint(IConfigScreenFactory.class,
                (java.util.function.Supplier<IConfigScreenFactory>)
                        () -> (container, parent) -> new ModSettingsScreen(parent));
        // 勾选【回放时隐藏玩家UI】→ 回放播放中取消整帧 HUD 渲染（含本模组叠加层）
        NeoForge.EVENT_BUS.addListener(ModClient::onRenderGui);
    }

    /** 回放播放中屏蔽 HUD 与屏幕叠加：RenderGuiEvent.Pre 可取消，取消即 Gui.render 整层不画。 */
    private static void onRenderGui(RenderGuiEvent.Pre event) {
        if (ModConfig.HIDE_UI_IN_REPLAY.get() && ReplayDetect.playing()) {
            event.setCanceled(true);
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        tick++;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        // 回放播放中屏蔽玩家打开的任何 UI（设置界面本身放行，避免无法恢复勾选）
        if (mc.screen != null) {
            if (ModConfig.HIDE_UI_IN_REPLAY.get() && ReplayDetect.playing()
                    && !(mc.screen instanceof ModSettingsScreen)) {
                mc.setScreen(null);
            }
            return;
        }
        boolean holding = mc.player.getMainHandItem().is(ModItems.NAVIGATOR.get())
                || mc.player.getOffhandItem().is(ModItems.NAVIGATOR.get());
        if (holding && tick % 40 == 0) {
            PacketDistributor.sendToServer(new Payloads.RequestNavigatorPayload(0));
        }
    }

    private ModClient() {}
}
