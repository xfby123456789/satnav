package com.hmod.create_satnav.client.render;

import com.hmod.create_satnav.client.ClientSatState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * 世界内高亮（§3.8 定案）：服务器画群色轮廓 + 半透明填充盒（不受深度遮挡），
 * 激活卫星画白色轮廓；选中航点画金色轮廓 + AR 导航引导（光柱 + 指向箭标）。
 * 服务器群「区域边框」整体高亮渲染与「回收卫星」服务区高亮环已按 §本轮5/6 移除。
 * 高亮与 AR 引导全部只在客户端渲染，不改任何服务端数据。
 */
public final class OutlineRender {

    private static final double RANGE_SQ = 128.0 * 128.0;

    public static void onStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        String dim = mc.level.dimension().location().toString();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        var buffer = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffer.getBuffer(RenderType.LINES);
        VertexConsumer fb = buffer.getBuffer(RenderType.debugFilledBox());

        var outlines = ClientSatState.OUTLINES;
        if (!outlines.isEmpty()) {
            for (var entry : outlines.entrySet()) {
                String key = entry.getKey();
                int semi = key.indexOf(';');
                if (semi < 0) continue;
                if (!key.substring(0, semi).equals(dim)) continue;
                BlockPos pos = SatKeyPos.of(key);
                if (pos == null) continue;
                if (pos.distToCenterSqr(cam.x, cam.y, cam.z) > RANGE_SQ) continue;
                var o = entry.getValue();
                float r, g, b;
                if (o.satellite()) {
                    r = 0.85f; g = 1.0f; b = 0.9f;
                } else {
                    r = ((o.color() >> 16) & 0xFF) / 255f;
                    g = ((o.color() >> 8) & 0xFF) / 255f;
                    b = (o.color() & 0xFF) / 255f;
                }
                LevelRenderer.renderLineBox(ps, vc, new AABB(pos).inflate(0.002), r, g, b, 1f);
                // 服务器：叠加群色半透明填充盒 —— 无深度测试，隔着墙/机柜模型也看得见
                if (!o.satellite()) {
                    LevelRenderer.addChainedFilledBoxVertices(ps, fb,
                            pos.getX(), pos.getY(), pos.getZ(),
                            pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0,
                            r, g, b, 0.35f);
                }
            }
        }

        BlockPos wp = ClientSatState.waypoint();
        if (wp != null) {
            LevelRenderer.renderLineBox(ps, vc, new AABB(wp).inflate(0.004), 1f, 0.83f, 0.1f, 1f);
            // ---- AR 导航（§12 磁石式）：只要玩家在服务区内就始终引导，目标 XY 不限距离 ----
            // 玩家离开服务区 → navActive=false → 引导与光柱即时消失，导航已终止。
            if (ClientSatState.navActive() && mc.player != null) {
                drawArGuide(mc, ps, fb, wp);
            }
        }

        // 本阶段内立即 flush 两路缓冲：不依赖后续批处理点（轮廓「无法高亮」的另一半原因）
        buffer.endBatch(RenderType.LINES);
        buffer.endBatch(RenderType.debugFilledBox());
    }

    /**
     * AR 引导绘制：目标点金色光柱 + 玩家与目标之间沿线排布的指向箭标（人眼朝向的 V 形），
     * 全部用无深度遮挡的半透明小方块拼成，随时间脉动，营造「增强现实」指路效果。
     */
    private static void drawArGuide(Minecraft mc, PoseStack ps, VertexConsumer fb, BlockPos wp) {
        Vec3 p = mc.player.position();
        double tx = wp.getX() + 0.5, tz = wp.getZ() + 0.5;
        double dx = tx - p.x, dz = tz - p.z;
        double distSq = dx * dx + dz * dz;
        double dist = Math.sqrt(distSq);                                // §12：不做距离上限判定，多远都指路
        double ang = Math.atan2(dz, dx);                       // 水平指向目标角
        long now = System.currentTimeMillis();
        float pulse = 0.45f + 0.3f * (float) Math.sin(now / 220.0);

        // 目标光柱：由下往上的脉动金色方块柱（穿墙可见）
        double by = wp.getY() + 1.15;
        for (int i = 0; i < 14; i++) {
            float a = pulse * (1f - i / 18f);
            LevelRenderer.addChainedFilledBoxVertices(ps, fb,
                    tx - 0.22, by + i * 0.95, tz - 0.22,
                    tx + 0.22, by + i * 0.95 + 0.55, tz + 0.22,
                    1.0f, 0.85f, 0.25f, a);
        }
        if (dist < 2.5) return;                                 // 已到位：只留光柱

        // 指向箭标：沿「玩家→目标」路径排最多 3 个 V 形（4/7/10 格处），近处优先
        double eyeY = p.y + 0.55;
        double[] steps = {4.0, 7.0, 10.0};
        for (int ci = 0; ci < steps.length; ci++) {
            double s = steps[ci];
            if (s > dist) break;
            double cx = p.x + dx / dist * s;
            double cz = p.z + dz / dist * s;
            float cp = 0.5f + 0.3f * (float) Math.sin(now / 220.0 + ci * 0.9);
            drawChevron(ps, fb, cx, eyeY, cz, ang, 1f, 0.83f, 0.2f, cp);
        }
    }

    /** 一个 V 形箭标：顶点指向目标，两臂向后展开 ±30°，各由 4 个小方块组成。 */
    private static void drawChevron(PoseStack ps, VertexConsumer fb,
                                    double cx, double cy, double cz, double dirAng,
                                    float r, float g, float b, float alpha) {
        double tipAng = dirAng + Math.PI;                      // 臂从顶点向后展开
        for (int side = -1; side <= 1; side += 2) {
            double armAng = tipAng + side * (Math.PI / 6.0);
            for (int k = 1; k <= 4; k++) {
                double d = k * 0.3;
                double x = cx + Math.cos(armAng) * d;
                double z = cz + Math.sin(armAng) * d;
                LevelRenderer.addChainedFilledBoxVertices(ps, fb,
                        x - 0.13, cy - 0.13, z - 0.13,
                        x + 0.13, cy + 0.13, z + 0.13,
                        r, g, b, alpha);
            }
        }
        // 顶点本身
        double x = cx + Math.cos(dirAng) * 0.35;
        double z = cz + Math.sin(dirAng) * 0.35;
        LevelRenderer.addChainedFilledBoxVertices(ps, fb,
                x - 0.16, cy - 0.16, z - 0.16, x + 0.16, cy + 0.16, z + 0.16,
                r, g, b, Math.min(1f, alpha + 0.2f));
    }

    /** 键 → 方块坐标的小工具（避免在客户端引用服务端类）。 */
    static final class SatKeyPos {
        static BlockPos of(String key) {
            try {
                int i = key.indexOf(';');
                return BlockPos.of(Long.parseLong(key.substring(i + 1)));
            } catch (Exception e) {
                return null;
            }
        }
    }

    private OutlineRender() {}
}
