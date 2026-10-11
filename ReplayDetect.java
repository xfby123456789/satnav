package com.hmod.create_satnav.client;

/**
 * 回放播放状态探测（§本轮5）：Flashback（Moulberry）与 Replay Mod 两条路径，
 * 全部用反射探测、不硬依赖任何回放模组——没装时恒为 false，正常游玩零影响。
 * 只在客户端调用。
 */
public final class ReplayDetect {

    public static boolean playing() {
        return flashbackPlaying() || replayModPlaying();
    }

    /** Flashback：com.moulberry.flashback.Flashback.isInReplay()（回放 ReplayServer 运行中为 true）。 */
    private static boolean flashbackPlaying() {
        try {
            Object v = Class.forName("com.moulberry.flashback.Flashback")
                    .getMethod("isInReplay").invoke(null);
            return Boolean.TRUE.equals(v);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Replay Mod：经典入口 ReplayClientUtils.instance 非空，或新版 ReplayModContext.isPlayingReplay()。 */
    private static boolean replayModPlaying() {
        try {
            Class<?> cls = Class.forName("com.replaymod.replay.utils.ReplayClientUtils");
            if (cls.getMethod("getInstance").invoke(null) != null) return true;
        } catch (Throwable ignored) {
        }
        try {
            Object inst = Class.forName("com.replaymod.replay.ReplayModContext")
                    .getMethod("getInstance").invoke(null);
            if (inst != null && Boolean.TRUE.equals(inst.getClass().getMethod("isPlayingReplay").invoke(inst)))
                return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private ReplayDetect() {}
}
