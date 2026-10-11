package com.hmod.create_satnav.registry;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * §9 配置表。server 侧统一参数 + client 侧显示参数。
 * 耐久相关参数已随耐久机制一并移除（§本轮8），不保留任何耐久死配置。
 */
public final class ModConfig {

    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    // ---- server 侧 ----
    public static final ModConfigSpec.IntValue SATELLITE_RADIUS;      // 卫星服务区半径（格），默认 30
    public static final ModConfigSpec.IntValue DEST_LIMIT;            // 目的地每玩家上限，默认 20
    public static final ModConfigSpec.IntValue PAIRING_TIMEOUT_SEC;   // 连接器配对超时（秒），默认 60
    public static final ModConfigSpec.IntValue FORCELOAD_IDLE_DAYS;   // 强加载闲置卸载阈值（天），默认 3

    // ---- client 侧 ----
    /** §本轮5 复选配置项【回放时隐藏玩家UI】：勾选后 Flashback/Replay 回放播放中
     *  屏蔽所有玩家 UI 与屏幕叠加渲染（干净旁观视角）；不勾选维持原有逻辑。 */
    public static final ModConfigSpec.BooleanValue HIDE_UI_IN_REPLAY;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("卫星导航 · 服务器侧参数");

        b.push("satnav");
        SATELLITE_RADIUS = b.comment("卫星服务区半径（格）").defineInRange("satelliteRadius", 30, 1, 1024);
        DEST_LIMIT = b.comment("导航器目的地每玩家上限").defineInRange("destinationLimit", 20, 1, 512);
        PAIRING_TIMEOUT_SEC = b.comment("连接器配对超时（秒）").defineInRange("pairingTimeoutSeconds", 60, 5, 3600);
        FORCELOAD_IDLE_DAYS = b.comment("服务器区块闲置自动卸载天数").defineInRange("forceloadIdleDays", 3, 0, 3650);
        b.pop();

        SERVER_SPEC = b.build();

        ModConfigSpec.Builder cb = new ModConfigSpec.Builder();
        cb.comment("卫星导航 · 客户端参数");
        cb.push("satnav");
        HIDE_UI_IN_REPLAY = cb.comment("回放（Flashback / Replay Mod）播放时隐藏玩家UI与屏幕叠加渲染，仅保留干净旁观视角")
                .define("hideUiDuringReplayPlayback", false);
        cb.pop();
        CLIENT_SPEC = cb.build();
    }

    private ModConfig() {}

    public static void register(ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, SERVER_SPEC);
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, CLIENT_SPEC);
    }
}
