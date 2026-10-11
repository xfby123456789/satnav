package com.hmod.create_satnav.client.gui;

import com.hmod.create_satnav.network.Payloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/**
 * 服务器配置 GUI（§3.8）：群名/公开或私人/密码/高亮颜色/申请开关 + 本机强加载模式（§5.1）
 * + 槽位信息 + 成员列表与踢出（§3.4）。
 * 公开性/加载方式/是否同意显示本地化文字（后端仍为数字）；已烤熟的土豆服务器标题独立。
 * 「回收卫星」按钮与下拉、耐久显示已按 §本轮5/8 移除，其余布局坐标保持原样。
 */
public class ServerScreen extends Screen {

    private static final int[] PALETTE = {0x44BBFF, 0x55DD66, 0xFFDD55, 0xFF8844, 0xFF5566, 0xCC66FF, 0xFF66AA, 0xAAAAAA};
    private static final String[] COLOR_NAMES = {"blue", "green", "yellow", "orange", "red", "purple", "pink", "gray"};

    /** 公开性/加载方式/是否同意 的 UI 文字映射（NBT/网络包仍保存数字，0=不加载 1=弱加载 2=强加载）。 */
    private static final String[] PUB_TEXT = {"私人", "公开"};
    private static final String[] FORCE_TEXT = {"不加载", "弱加载", "强加载"};
    private static final String[] AGREE_TEXT = {"不同意", "同意"};

    /** 与 render() 共用的行基准（init 里也要用）。 */
    private static final int INFO_Y = 88;
    private static final int MEMBERS_Y = 124;

    private Payloads.ServerGuiPayload d;
    private EditBox nameBox;
    private EditBox pwdBox;
    private boolean isPublic;
    private int colorIdx;
    private boolean applyOpen;
    private int forceMode;

    public ServerScreen(Payloads.ServerGuiPayload d) {
        super(Component.translatable("screen.create_satnav.server." + d.kind()));
        this.d = d;
        this.isPublic = d.isPublic();
        this.applyOpen = d.applyOpen();
        this.forceMode = d.forceMode();
        colorIdx = 0;
        for (int i = 0; i < PALETTE.length; i++) if (PALETTE[i] == d.color()) colorIdx = i;
    }

    public boolean matches(String posKey) {
        return d.posKey().equals(posKey);
    }

    public void refresh(Payloads.ServerGuiPayload p) {
        this.d = p;
        this.isPublic = p.isPublic();
        this.applyOpen = p.applyOpen();
        this.forceMode = p.forceMode();
        for (int i = 0; i < PALETTE.length; i++) if (PALETTE[i] == p.color()) colorIdx = i;
        rebuildWidgets();
    }

    private int left() { return (this.width - 280) / 2; }
    private int top() { return (this.height - 220) / 2; }

    @Override
    protected void init() {
        int l = left(), t = top();
        if (!d.canEdit()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.create_satnav.close"),
                    b -> onClose()).bounds(l + 92, t + 196, 96, 16).build());
            return;
        }
        nameBox = new EditBox(font, l + 40, t + 22, 148, 16, Component.translatable("gui.create_satnav.g_name"));
        nameBox.setMaxLength(32);
        nameBox.setValue(d.groupName());
        pwdBox = new EditBox(font, l + 40, t + 42, 148, 16, Component.translatable("gui.create_satnav.g_pwd"));
        pwdBox.setMaxLength(16);
        pwdBox.setValue(d.password());
        addRenderableWidget(nameBox);
        addRenderableWidget(pwdBox);
        // UI 展示本地化文字，保存/传输仍是 boolean / int
        addRenderableWidget(Button.builder(Component.translatable("gui.create_satnav.visibility",
                        PUB_TEXT[isPublic ? 1 : 0]),
                b -> { isPublic = !isPublic; rebuildWidgets(); }).bounds(l + 192, t + 22, 76, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.create_satnav.color." + COLOR_NAMES[colorIdx]),
                b -> { colorIdx = (colorIdx + 1) % PALETTE.length; rebuildWidgets(); }).bounds(l + 192, t + 42, 60, 16).build());
        // 是否同意（入群申请自动通过）：UI 展示文字，boolean 原样提交
        addRenderableWidget(Button.builder(Component.translatable("gui.create_satnav.apply",
                        AGREE_TEXT[applyOpen ? 1 : 0]),
                b -> { applyOpen = !applyOpen; rebuildWidgets(); }).bounds(l + 20, t + 64, 90, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.create_satnav.forcemode",
                        FORCE_TEXT[Math.max(0, Math.min(2, forceMode))]),
                b -> { forceMode = (forceMode + 1) % 3; rebuildWidgets(); }).bounds(l + 118, t + 64, 90, 16).build());
        // 成员踢出行
        int y = t + MEMBERS_Y + 11;
        for (int i = 0; i < d.memberNames().size() && i < 6; i++) {
            final String uuid = d.memberUuids().get(i);
            addRenderableWidget(Button.builder(Component.literal("✕"), b -> {
                try {
                    PacketDistributor.sendToServer(new Payloads.KickPlayerPayload(
                            d.posKey(), Payloads.UUIDWrapper.of(UUID.fromString(uuid))));
                } catch (IllegalArgumentException ignored) {
                }
                PacketDistributor.sendToServer(new Payloads.RequestServerGuiPayload(d.posKey()));
            }).bounds(l + 150, y - 1, 16, 12).build());
            y += 11;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.create_satnav.save"),
                b -> {
                    PacketDistributor.sendToServer(new Payloads.ServerGuiUpdatePayload(
                            d.posKey(), nameBox.getValue(), isPublic, pwdBox.getValue(),
                            PALETTE[colorIdx], applyOpen, forceMode));
                    PacketDistributor.sendToServer(new Payloads.RequestServerGuiPayload(d.posKey()));
                }).bounds(l + 8, t + 176, 70, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.create_satnav.close"),
                b -> onClose()).bounds(l + 202, t + 176, 70, 16).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        int l = left(), t = top();
        g.drawString(font, this.title, l + 4, t + 8, 0xE0E0E0, false);
        g.drawString(font, Component.translatable("gui.create_satnav.name_hint"), l + 8, t + 26, 0xAAAAAA, false);
        g.drawString(font, Component.translatable("gui.create_satnav.pwd_hint"), l + 8, t + 46, 0xAAAAAA, false);
        int y = t + INFO_Y;
        g.drawString(font, Component.translatable("gui.create_satnav.slots", d.used(), d.capacity()).getString()
                        + "   " + Component.translatable("gui.create_satnav.bound_sats", d.boundSatCount()).getString(),
                l + 8, y, 0xE0E0E0, false);
        if (!d.memberNames().isEmpty()) {
            y = t + MEMBERS_Y;
            g.drawString(font, Component.translatable("gui.create_satnav.members_title"), l + 8, y, 0xAAAAAA, false);
            y += 11;
            for (int i = 0; i < d.memberNames().size() && i < 6; i++) {
                g.drawString(font, "· " + d.memberNames().get(i), l + 12, y, 0xE0E0E0, false);
                y += 11;
            }
        }
        if (!d.canEdit()) {
            g.drawString(font, Component.translatable("gui.create_satnav.readonly"), l + 8, t + 176, 0xAAAAAA, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
