package com.hmod.create_satnav.client.gui;

import com.hmod.create_satnav.registry.ModConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 模组设置界面：从 模组列表 → 卫星导航 →「配置」进入。
 * 复选配置项【回放时隐藏玩家UI】：
 *   勾选 = Flashback/Replay 回放播放（第三人称录制视角）下屏蔽所有玩家 UI
 *          与屏幕叠加渲染，仅保留类似旁观者的干净视角；
 *   不勾选 = 回放正常显示玩家 UI，维持原有逻辑。
 * 仅改客户端配置，不触碰存档/网络数据。文字沿用本模组 UI 的中文字面量风格，
 * 不新增 JSON 资源。
 */
public class ModSettingsScreen extends Screen {

    private final Screen parent;

    public ModSettingsScreen(Screen parent) {
        super(Component.literal("卫星导航 · 模组设置"));
        this.parent = parent;
    }

    /** 复选框文字：☑/☐ 前缀即勾选状态。 */
    private static Component hideUiLabel() {
        return Component.literal((ModConfig.HIDE_UI_IN_REPLAY.get() ? "☑ " : "☐ ") + "回放时隐藏玩家UI");
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        addRenderableWidget(Button.builder(hideUiLabel(), b -> {
            boolean v = !ModConfig.HIDE_UI_IN_REPLAY.get();
            ModConfig.HIDE_UI_IN_REPLAY.set(v);
            ModConfig.CLIENT_SPEC.save();          // 写回客户端配置 TOML，重启后保留
            b.setMessage(hideUiLabel());
        }).bounds(cx - 110, cy - 14, 220, 20).build());
        addRenderableWidget(Button.builder(Component.literal("完成"), b -> this.minecraft.setScreen(parent))
                .bounds(cx - 50, cy + 34, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        int cy = this.height / 2;
        g.drawCenteredString(this.font, this.title, this.width / 2, cy - 60, 0xE0E0E0);
        g.drawCenteredString(this.font, "勾选后：回放播放（Flashback / Replay Mod 第三人称录制视角）",
                this.width / 2, cy - 44, 0x999999);
        g.drawCenteredString(this.font, "屏蔽全部玩家 UI 与屏幕叠加，只留干净旁观画面；不勾选维持原有逻辑",
                this.width / 2, cy - 34, 0x999999);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
