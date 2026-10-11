package com.hmod.create_satnav.client.hud;

import com.hmod.create_satnav.client.ClientSatState;
import com.hmod.create_satnav.registry.ModItems;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** 手持导航器时的简易 HUD：群名、信号、航点方向与距离。 */
public final class SatHud {

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null) return;
        if (!mc.player.getMainHandItem().is(ModItems.NAVIGATOR.get())
                && !mc.player.getOffhandItem().is(ModItems.NAVIGATOR.get())) return;
        var info = ClientSatState.NAV;
        if (info == null) return;
        int x = 6;
        int y = g.guiHeight() - 70;
        if (info.joinedName().isEmpty()) {
            g.drawString(mc.font, Component.translatable("hud.create_satnav.not_joined"), x, y, 0xFFDD55, true);
            g.drawString(mc.font, Component.translatable("hud.create_satnav.hint_join"), x, y + 10, 0xBBBBBB, true);
            return;
        }
        g.drawString(mc.font, Component.translatable("hud.create_satnav.group", info.joinedName()), x, y, 0x66DDAA, true);
        if (info.signalSat().isEmpty()) {
            g.drawString(mc.font, Component.translatable("hud.create_satnav.no_signal"), x, y + 10, 0xFF8866, true);
        } else {
            g.drawString(mc.font, Component.translatable("hud.create_satnav.signal",
                    String.format("%.0f", info.signalDist())), x, y + 10, 0x88DDFF, true);
        }
        BlockPos wp = ClientSatState.waypoint();
        if (wp != null) {
            Vec3 p = mc.player.position();
            double dx = wp.getX() + 0.5 - p.x;
            double dz = wp.getZ() + 0.5 - p.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            // 相对朝向：把目标方位角减去玩家朝向角
            double bearing = Math.toDegrees(Math.atan2(-dx, dz));     // 0=正北约定
            double facing = mc.player.getYHeadRot();
            double rel = Math.toRadians(((bearing - facing) % 360 + 540) % 360 - 180);
            String arrow = relToArrow(rel);
            g.drawString(mc.font, Component.translatable("hud.create_satnav.waypoint",
                    ClientSatState.waypointLabel(), arrow, String.format("%.0f", dist)), x, y + 20, 0xFFD755, true);
            drawArStrip(g, mc, wp, p, dist, arrow);
        } else {
            // §12 修复：导航只有一种中断原因 —— 玩家本人离开卫星服务区（目标距离不再参与判定）
            long since = System.currentTimeMillis() - ClientSatState.navStopAtMs;
            if (since < 10_000L && ClientSatState.navStopReason == ClientSatState.NAV_NO_SAT) {
                g.drawString(mc.font, Component.literal("导航已终止：已离开卫星服务区域"), x, y + 20, 0xFF6655, true);
                g.drawString(mc.font, Component.literal("重新进入服务区并选择目的地后才能继续导航"), x, y + 30, 0xBBBBBB, true);
            } else {
                g.drawString(mc.font, Component.translatable("hud.create_satnav.no_waypoint"), x, y + 20, 0xBBBBBB, true);
            }
        }
    }

    /**
     * AR 导航样式条（§12 磁石式）：屏幕顶部居中，箭头 + 目标 XYZ + 水平距离 + Y 高度指示，
     * 只要玩家在服务区内就始终显示，XY 方向不限距离。
     */
    private static void drawArStrip(GuiGraphics g, Minecraft mc, BlockPos wp, Vec3 p, double dist, String arrow) {
        if (!ClientSatState.navActive()) return;               // 玩家离开服务区 → 引导与光柱即时消失
        int cx = g.guiWidth() / 2;
        // 高度指示：目标相对玩家的 Y 方向与高度差数值（比磁石多出的一项）
        double dy = wp.getY() + 0.5 - p.y;
        long dY = Math.round(dy);
        String vHint = dY > 0 ? "↑Y +" + dY : dY < 0 ? "↓Y " + dY : "Y ±0（同层）";
        String line1 = "AR 导航 " + arrow + " " + String.format("%.0f", dist) + " 格 · " + vHint;
        String line2 = "目标 X:" + wp.getX() + " Y:" + wp.getY() + " Z:" + wp.getZ();
        int w = Math.max(mc.font.width(line1), mc.font.width(line2)) + 12;
        g.fill(cx - w / 2, 6, cx + w / 2, 34, 0x90101820);     // 半透明 AR 背板
        g.fill(cx - w / 2, 6, cx + w / 2, 7, 0xFFFFD755);      // 上边线
        g.fill(cx - w / 2, 33, cx + w / 2, 34, 0xFFFFD755);    // 下边线
        g.drawString(mc.font, line1, cx - w / 2 + 6, 10, 0xFFFFD755, true);
        g.drawString(mc.font, line2, cx - w / 2 + 6, 21, 0x88DDFF, true);
    }

    /** 8 方向箭头（相对朝向角，0=正前方）。 */
    private static String relToArrow(double relRad) {
        int sector = (int) Math.floor(((relRad + Math.PI / 8) / (Math.PI / 4))) & 7;
        // rel 负值 = 目标在右侧
        return switch (sector) {
            case 0 -> "⬆";        // 前
            case 1 -> "↗";
            case 2 -> "→";
            case 3 -> "↘";
            case 4 -> "⬇";
            case 5 -> "↙";
            case 6 -> "←";
            default -> "↖";
        };
    }

    private SatHud() {}
}
