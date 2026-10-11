package com.hmod.create_satnav.network;

import net.minecraft.world.entity.player.Player;

/**
 * S2C 转发桥：common 侧 handler 只调这里；真正的客户端处理函数
 * 由 ModClient 初始化时注入，服务端（dedicated）永远保持默认实现，
 * 从而不会触发任何客户端类的加载。
 */
public final class ClientBridge {

    public interface Handler {
        void onNavigatorInfo(Player player, Payloads.NavigatorInfoPayload p);

        void onServerGui(Player player, Payloads.ServerGuiPayload p);

        void onOutlines(Player player, Payloads.OutlinesPayload p);
    }

    private static final Handler NOOP = new Handler() {
        @Override
        public void onNavigatorInfo(Player player, Payloads.NavigatorInfoPayload p) {
        }

        @Override
        public void onServerGui(Player player, Payloads.ServerGuiPayload p) {
        }

        @Override
        public void onOutlines(Player player, Payloads.OutlinesPayload p) {
        }
    };

    public static volatile Handler handler = NOOP;

    public static void install(Handler h) {
        handler = h;
    }

    public static void onNavigatorInfo(Player player, Payloads.NavigatorInfoPayload p) {
        handler.onNavigatorInfo(player, p);
    }

    public static void onServerGui(Player player, Payloads.ServerGuiPayload p) {
        handler.onServerGui(player, p);
    }

    public static void onOutlines(Player player, Payloads.OutlinesPayload p) {
        handler.onOutlines(player, p);
    }

    private ClientBridge() {}
}
