package com.hmod.create_satnav.logic;

import com.hmod.create_satnav.network.ModNetwork;
import com.hmod.create_satnav.network.Payloads;
import com.hmod.create_satnav.network.SatNetworkData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 导航器侧全部服务器端动作（§3.4/§3.9/§4.2/§4.6）：
 * 数据包构建、入群/退群、目的地增删、踢出、群设置保存。
 */
public final class GroupActions {

    /** 收到 C2S 请求：回发导航器数据包（openFlags 原样带回，客户端据此决定开哪个界面）。 */
    public static void onRequestNavigator(ServerPlayer sp, int flags) {
        ModNetwork.sendTo(sp, buildNavigator(sp, flags));
    }

    public static Payloads.NavigatorInfoPayload buildNavigator(ServerPlayer sp, int flags) {
        ServerLevel level = sp.serverLevel();
        SatNetworkData data = SatNetworkData.get(level);
        UUID uuid = sp.getUUID();
        data.playerNames.put(uuid, sp.getGameProfile().getName());

        String joined = "";
        boolean pub = false;
        List<String> members = new ArrayList<>();
        List<String> boundSats = new ArrayList<>();
        String signalSat = "";
        double signalDist = -1;

        String root = data.playerGroup.get(uuid);
        if (root != null && !data.groups.containsKey(root)) {
            data.playerGroup.remove(uuid);
            data.playerServer.remove(uuid);
            data.setDirty();
            root = null;
        }
        if (root != null) {
            SatNetworkData.Group g = data.groups.get(root);
            joined = SatNetworkData.displayName(g);
            pub = g.isPublic;
            for (UUID m : data.playersOfGroup(root)) {
                String nm = data.playerNames.get(m);
                members.add(nm != null ? nm : m.toString().substring(0, 8));
                if (members.size() >= 16) break;
            }
            for (String sk : data.satellitesOfGroup(root)) {
                BlockPos p = SatNetworkData.readPos(sk);
                if (p != null) boundSats.add(p.getX() + "," + p.getY() + "," + p.getZ());
                if (boundSats.size() >= 16) break;
            }
            // §12：服务区判定 = 服务端真实数据结构 activeSatellites（每颗激活卫星的覆盖圆盘，
            // 按玩家实时 XZ 判定、忽略 Y）。signalSat 为空即代表玩家已离开服务区。
            String sat = data.nearestActiveSatellite(level, sp.getX(), sp.getZ());
            if (sat != null && data.inServiceArea(level, sp.getX(), sp.getZ(), ModNetwork.satelliteRadius())) {
                BlockPos p = SatNetworkData.readPos(sat);
                if (p != null) {
                    signalSat = p.getX() + "," + p.getY() + "," + p.getZ();
                    signalDist = Math.hypot(p.getX() + 0.5 - sp.getX(), p.getZ() + 0.5 - sp.getZ());
                }
            }
        }

        List<String> publicGroups = new ArrayList<>();
        for (SatNetworkData.Group g : data.groups.values()) {
            if (g.isPublic) {
                publicGroups.add(SatNetworkData.displayName(g));
                if (publicGroups.size() >= 64) break;
            }
        }
        List<String> dests = new ArrayList<>();
        if (root != null) {
            for (SatNetworkData.Destination d : data.destinationsOf(root)) {
                String who = d.creator.equals(uuid) ? "" : "!";
                dests.add(who + d.name + "|" + d.x + "," + d.y + "," + d.z);
            }
        }
        return new Payloads.NavigatorInfoPayload(flags, joined, pub, publicGroups, dests,
                members, boundSats, signalSat, signalDist);
    }

    // ------------------------------------------------------------------
    // 加入 / 离开
    // ------------------------------------------------------------------
    /** §4.2：公开群按名加入；私人群需密码。开启申请开关时写入报备日志并自动通过（§3.9）。 */
    public static void onJoin(ServerPlayer sp, String name, String password) {
        ServerLevel level = sp.serverLevel();
        SatNetworkData data = SatNetworkData.get(level);
        UUID uuid = sp.getUUID();
        String root = data.findGroupByName(name);
        if (root == null) {
            sp.displayClientMessage(Component.translatable("msg.create_satnav.join_notfound"), true);
            return;
        }
        SatNetworkData.Group g = data.groups.get(root);
        if (!g.isPublic && !g.password.equals(password == null ? "" : password)) {
            sp.displayClientMessage(Component.translatable("msg.create_satnav.join_wrongpwd"), true);
            return;
        }
        String server = data.findFreeServer(root, null);
        if (server == null) {
            sp.displayClientMessage(Component.translatable("msg.create_satnav.join_full"), true);
            return;
        }
        if (data.playerGroup.containsKey(uuid)) onLeaveInternal(data, uuid);
        data.playerGroup.put(uuid, root);
        data.playerServer.put(uuid, server);
        data.playerNames.put(uuid, sp.getGameProfile().getName());
        data.addApplyEntry(sp.getGameProfile().getName() + " -> " + SatNetworkData.displayName(g));
        data.setDirty();
        sp.displayClientMessage(Component.translatable("msg.create_satnav.joined", SatNetworkData.displayName(g)), true);
        ModNetwork.sendTo(sp, buildNavigator(sp, 0));
    }

    public static void onLeave(ServerPlayer sp) {
        SatNetworkData data = SatNetworkData.get(sp.serverLevel());
        onLeaveInternal(data, sp.getUUID());
        sp.displayClientMessage(Component.translatable("msg.create_satnav.left"), true);
        ModNetwork.sendTo(sp, buildNavigator(sp, 0));
    }

    /** 断开（§3.4 定案：仅手动断开）。 */
    public static void onLeaveInternal(SatNetworkData data, UUID uuid) {
        if (data.playerGroup.remove(uuid) != null) {
            data.playerServer.remove(uuid);
            data.setDirty();
        }
    }

    // ------------------------------------------------------------------
    // 目的地（§4.6：以玩家当前坐标登记，上限可配）
    // ------------------------------------------------------------------
    public static void onAddDestination(ServerPlayer sp, String name) {
        ServerLevel level = sp.serverLevel();
        SatNetworkData data = SatNetworkData.get(level);
        String root = data.playerGroup.get(sp.getUUID());
        if (root == null) {
            sp.displayClientMessage(Component.translatable("msg.create_satnav.need_group"), true);
            return;
        }
        if (name == null || name.isBlank() || name.length() > 32) {
            sp.displayClientMessage(Component.translatable("msg.create_satnav.dest_badname"), true);
            return;
        }
        name = name.trim();
        for (SatNetworkData.Destination d : data.destinationsOf(root)) {
            if (d.name.equals(name)) {
                sp.displayClientMessage(Component.translatable("msg.create_satnav.dest_dup"), true);
                return;
            }
        }
        if (data.countByCreator(sp.getUUID()) >= com.hmod.create_satnav.registry.ModConfig.DEST_LIMIT.get()) {
            sp.displayClientMessage(Component.translatable("msg.create_satnav.dest_limit"), true);
            return;
        }
        data.destinations.add(new SatNetworkData.Destination(root, name, sp.getUUID(), sp.blockPosition()));
        data.setDirty();
        sp.displayClientMessage(Component.translatable("msg.create_satnav.dest_added", name), true);
        ModNetwork.sendTo(sp, buildNavigator(sp, 0));
    }

    public static void onRemoveDestination(ServerPlayer sp, String name) {
        SatNetworkData data = SatNetworkData.get(sp.serverLevel());
        String root = data.playerGroup.get(sp.getUUID());
        if (root == null) return;
        boolean removed = data.destinations.removeIf(d -> d.groupRoot.equals(root) && d.name.equals(name));
        if (removed) {
            data.setDirty();
            ModNetwork.sendTo(sp, buildNavigator(sp, 0));
        }
    }

    // ------------------------------------------------------------------
    // 服务器 GUI 保存 / 踢人
    // ------------------------------------------------------------------
    /** §3.8：保存群名/公开或私人/密码/颜色/申请开关 + 本机强加载模式。 */
    public static void onServerGuiUpdate(ServerPlayer sp, Payloads.ServerGuiUpdatePayload p) {
        ServerLevel level = sp.serverLevel();
        BlockPos pos = SatNetworkData.readPos(p.posKey());
        if (pos == null || !(level.getBlockEntity(pos) instanceof com.hmod.create_satnav.blockentity.ServerBlockEntity be)) return;
        SatNetworkData data = SatNetworkData.get(level);
        String root = data.serverGroup.get(p.posKey());
        SatNetworkData.Group g = root == null ? null : data.groups.get(root);
        if (g == null || !ServerInteractions.canManage(sp, g, be)) {
            sp.displayClientMessage(Component.translatable("msg.create_satnav.no_perm"), true);
            return;
        }
        String name = p.groupName() == null ? "" : p.groupName().trim();
        if (name.length() > 32) name = name.substring(0, 32);
        String pwd = p.password() == null ? "" : (p.isPublic() ? "" : p.password());
        if (pwd.length() > 16) pwd = pwd.substring(0, 16);
        g.name = name;
        g.isPublic = p.isPublic();
        g.password = pwd;
        g.color = p.color() & 0xFFFFFF;
        g.applyOpen = p.applyOpen();
        be.setForceMode(Math.max(0, Math.min(2, p.forceMode())));
        be.touch();
        data.setDirty();
        sp.displayClientMessage(Component.translatable("msg.create_satnav.saved"), true);
        ServerInteractions.openServerGui(level, pos, sp);
    }

    /** §3.4：管理侧踢出玩家。 */
    public static void onKick(ServerPlayer sp, String posKey, UUID target) {
        ServerLevel level = sp.serverLevel();
        BlockPos pos = SatNetworkData.readPos(posKey);
        if (pos == null || !(level.getBlockEntity(pos) instanceof com.hmod.create_satnav.blockentity.ServerBlockEntity be)) return;
        SatNetworkData data = SatNetworkData.get(level);
        String root = data.serverGroup.get(posKey);
        SatNetworkData.Group g = root == null ? null : data.groups.get(root);
        if (g == null || !ServerInteractions.canManage(sp, g, be)) {
            sp.displayClientMessage(Component.translatable("msg.create_satnav.no_perm"), true);
            return;
        }
        if (target.equals(sp.getUUID())) return;
        boolean removed = data.playerGroup.remove(target) != null;
        data.playerServer.remove(target);
        if (removed) {
            data.setDirty();
            ServerPlayer victim = level.getServer().getPlayerList().getPlayer(target);
            if (victim != null) {
                victim.displayClientMessage(Component.translatable("msg.create_satnav.kicked"), true);
                ModNetwork.sendTo(victim, buildNavigator(victim, 0));
            }
        }
        ServerInteractions.openServerGui(level, pos, sp);
    }

    private GroupActions() {}
}
