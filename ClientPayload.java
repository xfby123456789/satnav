package com.hmod.create_satnav.client;

import com.hmod.create_satnav.client.gui.NavigatorScreen;
import com.hmod.create_satnav.client.gui.ServerScreen;
import com.hmod.create_satnav.network.ClientBridge;
import com.hmod.create_satnav.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/** S2C 数据包的真实客户端处理（经 ClientBridge 注入，服务端永不加载）。 */
public final class ClientPayload implements ClientBridge.Handler {

    @Override
    public void onNavigatorInfo(Player player, Payloads.NavigatorInfoPayload p) {
        ClientSatState.setNav(p);
        if (p.openFlags() < 1) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof NavigatorScreen ns) {
            ns.refresh(p);
        } else {
            mc.setScreen(new NavigatorScreen(p));
        }
    }

    @Override
    public void onServerGui(Player player, Payloads.ServerGuiPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (ReplayDetect.playing()) {   // 回放状态：拒绝打开服务器界面，避免与回放摄像机/界面冲突
            if (mc.player != null) mc.player.displayClientMessage(
                    Component.literal("录像回放中，禁止打开服务器界面"), true);
            return;
        }
        if (mc.screen instanceof ServerScreen ss && ss.matches(p.posKey())) {
            ss.refresh(p);
        } else {
            mc.setScreen(new ServerScreen(p));
        }
    }

    @Override
    public void onOutlines(Player player, Payloads.OutlinesPayload p) {
        Map<String, ClientSatState.Outline> map = new HashMap<>();
        for (String entry : p.entries()) {
            String[] parts = entry.split("\\|");
            if (parts.length < 2) continue;
            if (parts[1].equals("SAT")) {
                map.put(parts[0], new ClientSatState.Outline(0xFFFFFF, true));
            } else {
                try {
                    int color = Integer.parseInt(parts[1]) & 0xFFFFFF;
                    map.put(parts[0], new ClientSatState.Outline(color, false));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        ClientSatState.OUTLINES = Map.copyOf(map);
    }
}
