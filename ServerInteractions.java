package com.hmod.create_satnav.logic;

import com.hmod.create_satnav.blockentity.ServerBlockEntity;
import com.hmod.create_satnav.network.ModNetwork;
import com.hmod.create_satnav.network.Payloads;
import com.hmod.create_satnav.network.SatNetworkData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 服务器方块交互（§3.8）：空手右键打开配置 GUI。
 * 耐久/修复交互已随耐久机制整体移除（§本轮8）。
 */
public final class ServerInteractions {

    /** 空手右键：服务器把配置数据发给玩家，客户端据此打开 GUI。 */
    public static void openServerGui(ServerLevel level, BlockPos pos, Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        if (!(level.getBlockEntity(pos) instanceof ServerBlockEntity be)) return;
        be.touch();
        SatNetworkData data = SatNetworkData.get(level);
        String k = SatNetworkData.key(level, pos);
        String root = data.serverGroup.get(k);
        SatNetworkData.Group g = root == null ? null : data.groups.get(root);

        boolean canEdit = canManage(sp, g, be);
        List<String> names = new ArrayList<>();
        List<String> uuids = new ArrayList<>();
        if (root != null) {
            for (UUID m : data.playersOfGroup(root)) {
                String nm = data.playerNames.get(m);
                names.add(nm != null ? nm : m.toString().substring(0, 8));
                uuids.add(m.toString());
                if (names.size() >= 16) break;
            }
        }
        // §本轮9：烤土豆服务器在界面上以独立标题显示（仅展示用字符串，组网数据不变）
        String kindName = be.isBaked() ? "POTATO_BAKED" : be.kind().name();
        ModNetwork.sendTo(sp, new Payloads.ServerGuiPayload(
                k,
                g == null ? "" : g.name,
                g == null || g.isPublic,
                g == null ? "" : g.password,
                g == null ? 0x44BBFF : g.color,
                g != null && g.applyOpen,
                be.getForceMode(),
                data.slotCapacity(k),
                data.slotUsed(k),
                root == null ? 0 : data.satellitesOfGroup(root).size(),
                names,
                uuids,
                canEdit,
                kindName));
    }

    /** 管理权限：群主 / 本机放置者 / 创造模式（§3.5 默认）。 */
    public static boolean canManage(ServerPlayer sp, SatNetworkData.Group g, ServerBlockEntity be) {
        if (sp.isCreative()) return true;
        UUID u = sp.getUUID();
        if (g != null && u.equals(g.owner)) return true;
        return be != null && u.equals(be.getPlacedBy());
    }

    private ServerInteractions() {}
}
