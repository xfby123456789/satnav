package com.hmod.create_satnav.client;

import com.hmod.create_satnav.network.Payloads;
import net.minecraft.core.BlockPos;

import java.util.Map;

/** 客户端侧缓存：轮廓高亮、最近一次导航器数据、当前选中航点。 */
public final class ClientSatState {

    public record Outline(int color, boolean satellite) {}

    // ---- 导航状态（§12 修复：磁石式无限距离，仅「玩家离开服务区」才终止导航）----
    public static final int NAV_OK = 0;         // 可继续导航
    public static final int NAV_NO_GROUP = 1;   // 未加入服务群
    public static final int NAV_NO_SAT = 2;     // 已离开卫星服务区域（唯一中断原因）

    /** 导航是否有效：有群且玩家本人仍在服务区内（服务区 = 服务端 activeSatellites 真实覆盖）。 */
    public static boolean navActive() {
        return navStatus() == NAV_OK;
    }

    public static int navStatus() {
        Payloads.NavigatorInfoPayload n = NAV;
        if (n == null || n.joinedName().isEmpty()) return NAV_NO_GROUP;
        // signalSat 由服务端按玩家实时坐标对每颗激活卫星的服务区圆盘判定；为空 = 人不在服务区。
        // §12 修复：删除原先「目标点须落在卫星半径圆盘内」的硬性距离检查——磁石式导航
        // 目标 XY 不限距离，玩家在服务区内就一直指路。
        if (n.signalSat().isEmpty()) return NAV_NO_SAT;
        return waypoint() == null ? NAV_NO_SAT : NAV_OK;
    }

    /** key = "dim;x,y,z"（pos long 形式，与服务端一致）。 */
    public static volatile Map<String, Outline> OUTLINES = Map.of();

    public static Payloads.NavigatorInfoPayload NAV;
    public static long navSetAtMs;
    /** 选中目的地在 NAV.destinations() 中的下标，-1 = 无。 */
    public static int selectedDest = -1;
    /** 最近一次「导航被终止」的原因与时刻（§12：仅玩家离开服务区时终止，HUD 提示）。 */
    public static int navStopReason = 0;      // NAV_NO_SAT
    public static long navStopAtMs = 0L;

    public static void setNav(Payloads.NavigatorInfoPayload info) {
        NAV = info;
        navSetAtMs = System.currentTimeMillis();
        if (selectedDest >= info.destinations().size()) selectedDest = -1;
        // §12 修复：只有玩家真正离开服务区（服务端刷新 → signalSat 为空）才终止导航；
        // 目标点距离再远也继续指路（磁石式无限距离），不再因「目标超出半径」而中断。
        if (selectedDest >= 0 && navStatus() == NAV_NO_SAT) {
            navStopReason = NAV_NO_SAT;
            navStopAtMs = navSetAtMs;
            selectedDest = -1;
        }
    }

    /** 选中（或取消）一个航点：label 形如 "name|x,y,z"（他人私有目的地以 "!" 前缀）。 */
    public static void selectDest(int index) {
        if (NAV == null) return;
        selectedDest = (index == selectedDest || index < 0 || index >= NAV.destinations().size()) ? -1 : index;
    }

    public static BlockPos waypoint() {
        if (NAV == null || selectedDest < 0 || selectedDest >= NAV.destinations().size()) return null;
        return parseDestPos(NAV.destinations().get(selectedDest));
    }

    public static String waypointLabel() {
        if (NAV == null || selectedDest < 0 || selectedDest >= NAV.destinations().size()) return "";
        String raw = NAV.destinations().get(selectedDest);
        int bar = raw.indexOf('|');
        String name = bar < 0 ? raw : raw.substring(0, bar);
        return name.startsWith("!") ? name.substring(1) + "（他人）" : name;
    }

    public static BlockPos parseDestPos(String entry) {
        try {
            int bar = entry.indexOf('|');
            if (bar < 0) return null;
            String[] c = entry.substring(bar + 1).split(",");
            return new BlockPos(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2]));
        } catch (Exception e) {
            return null;
        }
    }

    /** 目的地展示文本："name  X,Y,Z"。 */
    public static String destLabel(String entry) {
        int bar = entry.indexOf('|');
        if (bar < 0) return entry;
        String name = entry.substring(0, bar);
        boolean mine = !name.startsWith("!");
        return (mine ? "" : "☆") + name + "  " + entry.substring(bar + 1);
    }

    private ClientSatState() {}
}
