package com.hmod.create_satnav.client.gui;

import com.hmod.create_satnav.client.ClientSatState;
import net.neoforged.neoforge.network.PacketDistributor;
import com.hmod.create_satnav.network.Payloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 导航器界面（§4）：导航页 = 目的地列表（选择航点/删除/以当前位置添加）+ 信号信息；
 * 网络页 = 公开群列表、按名加入（私人群带密码）、退出群组、成员与卫星列表。
 */
public class NavigatorScreen extends Screen {

    private Payloads.NavigatorInfoPayload info;
    private int tab; // 1=导航 2=网络

    private EditBox destName;
    private EditBox joinName;
    private EditBox joinPwd;

    public NavigatorScreen(Payloads.NavigatorInfoPayload info) {
        super(Component.translatable("screen.create_satnav.navigator"));
        this.info = info;
        this.tab = Math.max(1, Math.min(2, info.openFlags() == 2 ? 2 : 1));
    }

    public void refresh(Payloads.NavigatorInfoPayload p) {
        this.info = p;
        rebuildWidgets();
    }

    private int left() { return (this.width - 260) / 2; }
    private int top() { return (this.height - 200) / 2; }

    @Override
    protected void init() {
        int l = left(), t = top();
        addRenderableWidget(Button.builder(Component.translatable("gui.create_satnav.tab_nav"),
                b -> { tab = 1; rebuildWidgets(); }).bounds(l + 4, t + 4, 80, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.create_satnav.tab_net"),
                b -> { tab = 2; rebuildWidgets(); }).bounds(l + 86, t + 4, 80, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.create_satnav.refresh"),
                b -> PacketDistributor.sendToServer(new Payloads.RequestNavigatorPayload(tab)))
                .bounds(l + 168, t + 4, 46, 16).build());
        if (tab == 1) initNavTab(l, t); else initNetTab(l, t);
    }

    // ---- 导航页 ----
    private void initNavTab(int l, int t) {
        destName = new EditBox(font, l + 8, t + 30, 130, 16, Component.translatable("gui.create_satnav.dest_name"));
        destName.setMaxLength(32);
        addRenderableWidget(destName);
        addRenderableWidget(Button.builder(Component.translatable("gui.create_satnav.dest_add"),
                b -> PacketDistributor.sendToServer(new Payloads.AddDestinationPayload(destName.getValue())))
                .bounds(l + 142, t + 30, 72, 16).build());

        List<String> dests = info.destinations();
        int y = t + 54;
        for (int i = 0; i < dests.size() && i < 8; i++) {
            final int idx = i;
            final String entry = dests.get(i);
            String label = ClientSatState.destLabel(entry)
                    + (idx == ClientSatState.selectedDest ? "  ●" : "");
            addRenderableWidget(Button.builder(Component.literal(label),
                    b -> { ClientSatState.selectDest(idx); rebuildWidgets(); })
                    .bounds(l + 8, y, 168, 14).build());
            addRenderableWidget(Button.builder(Component.literal("✕"),
                    b -> PacketDistributor.sendToServer(new Payloads.RemoveDestinationPayload(destRawName(entry))))
                    .bounds(l + 180, y, 28, 14).build());
            y += 16;
        }
    }

    /** 目的地条目 "name|x,y,z"（私人的带 "!" 前缀）→ 裸名字。 */
    static String destRawName(String entry) {
        String raw = entry.startsWith("!") ? entry.substring(1) : entry;
        int bar = raw.indexOf('|');
        return bar < 0 ? raw : raw.substring(0, bar);
    }

    // ---- 网络页 ----
    private void initNetTab(int l, int t) {
        joinName = new EditBox(font, l + 8, t + 30, 110, 16, Component.translatable("gui.create_satnav.join_name"));
        joinName.setMaxLength(32);
        joinPwd = new EditBox(font, l + 122, t + 30, 60, 16, Component.translatable("gui.create_satnav.join_pwd"));
        joinPwd.setMaxLength(16);
        addRenderableWidget(joinName);
        addRenderableWidget(joinPwd);
        addRenderableWidget(Button.builder(Component.translatable("gui.create_satnav.join"),
                b -> PacketDistributor.sendToServer(new Payloads.JoinGroupPayload(joinName.getValue(), joinPwd.getValue())))
                .bounds(l + 186, t + 30, 36, 16).build());

        int y = t + 54;
        List<String> pubs = info.publicGroups();
        for (int i = 0; i < pubs.size() && i < 5; i++) {
            final String name = pubs.get(i);
            addRenderableWidget(Button.builder(Component.literal("▶ " + name),
                    b -> PacketDistributor.sendToServer(new Payloads.JoinGroupPayload(name, "")))
                    .bounds(l + 8, y, 170, 14).build());
            y += 16;
        }
        if (!info.joinedName().isEmpty()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.create_satnav.leave"),
                    b -> PacketDistributor.sendToServer(new Payloads.LeaveGroupPayload()))
                    .bounds(l + 182, y, 40, 14).build());
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        int l = left(), t = top();
        g.drawString(font, this.title, l + 4, t - 12, 0xE0E0E0, false);
        // 状态栏
        boolean joined = !info.joinedName().isEmpty();
        String status = joined
                ? Component.translatable("gui.create_satnav.joined", info.joinedName(),
                        info.joinedPublic() ? "" : "（私人）").getString()
                : Component.translatable("gui.create_satnav.not_joined").getString();
        g.drawString(font, status, l + 8, t + 186, 0x66DDAA, false);
        String signal;
        int signalColor;
        if (!joined) {
            signal = Component.translatable("gui.create_satnav.sig_none").getString();
            signalColor = 0xBBBBBB;
        } else if (info.signalSat().isEmpty()) {
            signal = Component.translatable("gui.create_satnav.sig_out_of_area").getString();
            signalColor = 0xFF8866;
        } else {
            signal = Component.translatable("gui.create_satnav.sig_ok",
                    String.format("%.0f", info.signalDist()), info.signalSat()).getString();
            signalColor = 0x88DDFF;
        }
        g.drawString(font, signal, l + 90, t + 186 - 14, signalColor, false);
        if (tab == 2 && !info.boundSats().isEmpty()) {
            g.drawString(font, Component.translatable("gui.create_satnav.sats",
                    String.join(" ", info.boundSats())).getString(), l + 8, t + 186 - 28, 0xE0E0E0, false);
        }
        if (tab == 2 && !info.members().isEmpty()) {
            g.drawString(font, Component.translatable("gui.create_satnav.members",
                    String.join("、", info.members())).getString(), l + 8, t + 186 - 42, 0xE0E0E0, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
