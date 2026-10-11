package com.hmod.create_satnav.network;

import com.hmod.create_satnav.logic.GroupActions;
import com.hmod.create_satnav.logic.PairingManager;
import com.hmod.create_satnav.logic.ServerInteractions;
import com.hmod.create_satnav.registry.ModConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 自定义 payload 的注册与分发。
 * C2S 全部 enqueueWork 回主线程处理；S2C 客户端侧经 ClientBridge 转发（服务端不加载客户端类）。
 */
public final class ModNetwork {

    public static int satelliteRadius() {
        return ModConfig.SATELLITE_RADIUS.get();
    }

    /** 由主入口在 mod bus 上调用。 */
    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");

        // ---------- C2S ----------
        r.playToServer(Payloads.RequestNavigatorPayload.TYPE, Payloads.RequestNavigatorPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> GroupActions.onRequestNavigator((ServerPlayer) ctx.player(), p.openFlags())));
        r.playToServer(Payloads.JoinGroupPayload.TYPE, Payloads.JoinGroupPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> GroupActions.onJoin((ServerPlayer) ctx.player(), p.name(), p.password())));
        r.playToServer(Payloads.LeaveGroupPayload.TYPE, Payloads.LeaveGroupPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> GroupActions.onLeave((ServerPlayer) ctx.player())));
        r.playToServer(Payloads.AddDestinationPayload.TYPE, Payloads.AddDestinationPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> GroupActions.onAddDestination((ServerPlayer) ctx.player(), p.name())));
        r.playToServer(Payloads.RemoveDestinationPayload.TYPE, Payloads.RemoveDestinationPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> GroupActions.onRemoveDestination((ServerPlayer) ctx.player(), p.name())));
        r.playToServer(Payloads.ServerGuiUpdatePayload.TYPE, Payloads.ServerGuiUpdatePayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> GroupActions.onServerGuiUpdate((ServerPlayer) ctx.player(), p)));
        r.playToServer(Payloads.KickPlayerPayload.TYPE, Payloads.KickPlayerPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> {
                    ServerPlayer sp = (ServerPlayer) ctx.player();
                    GroupActions.onKick(sp, p.posKey(), p.target().toUuid());
                }));
        r.playToServer(Payloads.RequestServerGuiPayload.TYPE, Payloads.RequestServerGuiPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> {
                    ServerPlayer sp = (ServerPlayer) ctx.player();
                    net.minecraft.core.BlockPos pos = SatNetworkData.readPos(p.posKey());
                    if (pos != null && sp.serverLevel().getBlockState(pos).getBlock()
                            instanceof com.hmod.create_satnav.block.ServerBlock) {
                        ServerInteractions.openServerGui(sp.serverLevel(), pos, sp);
                    }
                }));
        r.playToServer(Payloads.PairClearPayload.TYPE, Payloads.PairClearPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> PairingManager.clear((ServerPlayer) ctx.player())));

        // ---------- S2C（客户端侧经 ClientBridge 转发） ----------
        r.playToClient(Payloads.NavigatorInfoPayload.TYPE, Payloads.NavigatorInfoPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> ClientBridge.onNavigatorInfo(ctx.player(), p)));
        r.playToClient(Payloads.ServerGuiPayload.TYPE, Payloads.ServerGuiPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> ClientBridge.onServerGui(ctx.player(), p)));
        r.playToClient(Payloads.OutlinesPayload.TYPE, Payloads.OutlinesPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> ClientBridge.onOutlines(ctx.player(), p)));
    }

    public static void sendTo(ServerPlayer player, Payloads.NavigatorInfoPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendTo(ServerPlayer player, Payloads.ServerGuiPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    /** 服务器轮廓广播：每台服务器一个条目（键|颜色），每颗激活卫星一个条目（键|SAT）。 */
    public static void broadcastOutlines(MinecraftServer server) {
        if (server.getPlayerList().getPlayers().isEmpty()) return;
        SatNetworkData data = SatNetworkData.get(server.overworld());
        List<String> entries = new ArrayList<>();
        for (Map.Entry<String, String> e : data.serverGroup.entrySet()) {
            SatNetworkData.Group g = data.groups.get(e.getValue());
            if (g == null) continue;
            entries.add(e.getKey() + "|" + (g.color & 0xFFFFFF));
        }
        for (String sk : data.activeSatellites) {
            entries.add(sk + "|SAT");
        }
        PacketDistributor.sendToAllPlayers(new Payloads.OutlinesPayload(entries));
    }

    private ModNetwork() {}
}
