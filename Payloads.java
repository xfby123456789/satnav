package com.hmod.create_satnav.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义网络消息集合：导航器/服务器 GUI 的 C2S 请求与 S2C 数据。
 * 全部使用手写字节编解码，简单可靠。
 */
public final class Payloads {

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath("create_satnav", name);
    }

    private static void writeString(ByteBuf buf, String s) {
        ByteBufCodecs.STRING_UTF8.encode(buf, s == null ? "" : s);
    }

    private static String readString(ByteBuf buf) {
        return ByteBufCodecs.STRING_UTF8.decode(buf);
    }

    private static void writeStrings(ByteBuf buf, List<String> list) {
        ByteBufCodecs.VAR_INT.encode(buf, list.size());
        for (String s : list) writeString(buf, s);
    }

    private static List<String> readStrings(ByteBuf buf) {
        int n = ByteBufCodecs.VAR_INT.decode(buf);
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(readString(buf));
        return out;
    }

    // ==================================================================
    // C2S：请求导航器数据（openFlags: 0=仅刷新 1=打开主界面 2=打开网络配置）
    // ==================================================================
    public record RequestNavigatorPayload(int openFlags) implements CustomPacketPayload {
        public static final Type<RequestNavigatorPayload> TYPE = new Type<>(id("request_navigator"));
        public static final StreamCodec<ByteBuf, RequestNavigatorPayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public RequestNavigatorPayload decode(ByteBuf buf) {
                        return new RequestNavigatorPayload(ByteBufCodecs.VAR_INT.decode(buf));
                    }

                    @Override
                    public void encode(ByteBuf buf, RequestNavigatorPayload p) {
                        ByteBufCodecs.VAR_INT.encode(buf, p.openFlags());
                    }
                };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ==================================================================
    // S2C：导航器数据包（公开群列表/已入群信息/目的地/信号来源）
    // ==================================================================
    public record NavigatorInfoPayload(
            int openFlags,
            String joinedName,
            boolean joinedPublic,
            List<String> publicGroups,
            List<String> destinations,      // "名字|X,Y,Z"
            List<String> members,           // 群内成员名（含服务器管理侧）
            List<String> boundSats,         // "X,Y,Z"
            String signalSat,               // "X,Y,Z" 空=无信号（§12：唯一服务区判定依据，服务端按真实覆盖下发）
            double signalDist) implements CustomPacketPayload {
        public static final Type<NavigatorInfoPayload> TYPE = new Type<>(id("navigator_info"));
        public static final StreamCodec<ByteBuf, NavigatorInfoPayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public NavigatorInfoPayload decode(ByteBuf buf) {
                        int flags = ByteBufCodecs.VAR_INT.decode(buf);
                        String joined = readString(buf);
                        boolean pub = buf.readBoolean();
                        List<String> pubs = readStrings(buf);
                        List<String> dests = readStrings(buf);
                        List<String> members = readStrings(buf);
                        List<String> sats = readStrings(buf);
                        String signal = readString(buf);
                        double dist = buf.readDouble();
                        return new NavigatorInfoPayload(flags, joined, pub, pubs, dests, members, sats, signal, dist);
                    }

                    @Override
                    public void encode(ByteBuf buf, NavigatorInfoPayload p) {
                        ByteBufCodecs.VAR_INT.encode(buf, p.openFlags());
                        writeString(buf, p.joinedName());
                        buf.writeBoolean(p.joinedPublic());
                        writeStrings(buf, p.publicGroups());
                        writeStrings(buf, p.destinations());
                        writeStrings(buf, p.members());
                        writeStrings(buf, p.boundSats());
                        writeString(buf, p.signalSat());
                        buf.writeDouble(p.signalDist());
                    }
                };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ==================================================================
    // C2S：加入服务群（公开=名字；私人=名字+密码）§4.2
    // ==================================================================
    public record JoinGroupPayload(String name, String password) implements CustomPacketPayload {
        public static final Type<JoinGroupPayload> TYPE = new Type<>(id("join_group"));
        public static final StreamCodec<ByteBuf, JoinGroupPayload> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public JoinGroupPayload decode(ByteBuf buf) {
                return new JoinGroupPayload(readString(buf), readString(buf));
            }

            @Override
            public void encode(ByteBuf buf, JoinGroupPayload p) {
                writeString(buf, p.name());
                writeString(buf, p.password());
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** C2S：在导航器界面主动断开（§3.4 定案：仅手动断开）。 */
    public record LeaveGroupPayload() implements CustomPacketPayload {
        public static final Type<LeaveGroupPayload> TYPE = new Type<>(id("leave_group"));
        public static final StreamCodec<ByteBuf, LeaveGroupPayload> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public LeaveGroupPayload decode(ByteBuf buf) {
                return new LeaveGroupPayload();
            }

            @Override
            public void encode(ByteBuf buf, LeaveGroupPayload p) {
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** C2S：新增目的地（以玩家当前坐标为准，防作弊）。§4.6 */
    public record AddDestinationPayload(String name) implements CustomPacketPayload {
        public static final Type<AddDestinationPayload> TYPE = new Type<>(id("add_destination"));
        public static final StreamCodec<ByteBuf, AddDestinationPayload> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public AddDestinationPayload decode(ByteBuf buf) {
                return new AddDestinationPayload(readString(buf));
            }

            @Override
            public void encode(ByteBuf buf, AddDestinationPayload p) {
                writeString(buf, p.name());
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** C2S：删除目的地。§4.6 GUI 按钮删除 */
    public record RemoveDestinationPayload(String name) implements CustomPacketPayload {
        public static final Type<RemoveDestinationPayload> TYPE = new Type<>(id("remove_destination"));
        public static final StreamCodec<ByteBuf, RemoveDestinationPayload> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public RemoveDestinationPayload decode(ByteBuf buf) {
                return new RemoveDestinationPayload(readString(buf));
            }

            @Override
            public void encode(ByteBuf buf, RemoveDestinationPayload p) {
                writeString(buf, p.name());
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** C2S：服务器 GUI 保存（群名/公开或私人/密码/颜色/申请开关/强加载模式）。§3.8/§5.1 */
    public record ServerGuiUpdatePayload(String posKey, String groupName, boolean isPublic, String password,
                                         int color, boolean applyOpen, int forceMode) implements CustomPacketPayload {
        public static final Type<ServerGuiUpdatePayload> TYPE = new Type<>(id("server_gui_update"));
        public static final StreamCodec<ByteBuf, ServerGuiUpdatePayload> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public ServerGuiUpdatePayload decode(ByteBuf buf) {
                return new ServerGuiUpdatePayload(readString(buf), readString(buf), buf.readBoolean(),
                        readString(buf), buf.readInt(), buf.readBoolean(), ByteBufCodecs.VAR_INT.decode(buf));
            }

            @Override
            public void encode(ByteBuf buf, ServerGuiUpdatePayload p) {
                writeString(buf, p.posKey());
                writeString(buf, p.groupName());
                buf.writeBoolean(p.isPublic());
                writeString(buf, p.password());
                buf.writeInt(p.color());
                buf.writeBoolean(p.applyOpen());
                ByteBufCodecs.VAR_INT.encode(buf, p.forceMode());
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** C2S：服务器管理侧踢出玩家（§3.4）。 */
    public record KickPlayerPayload(String posKey, UUIDWrapper target) implements CustomPacketPayload {
        public static final Type<KickPlayerPayload> TYPE = new Type<>(id("kick_player"));
        public static final StreamCodec<ByteBuf, KickPlayerPayload> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public KickPlayerPayload decode(ByteBuf buf) {
                return new KickPlayerPayload(readString(buf), new UUIDWrapper(buf.readLong(), buf.readLong()));
            }

            @Override
            public void encode(ByteBuf buf, KickPlayerPayload p) {
                writeString(buf, p.posKey());
                buf.writeLong(p.target().most());
                buf.writeLong(p.target().least());
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** UUID 的线上传输包装。 */
    public record UUIDWrapper(long most, long least) {
        public java.util.UUID toUuid() {
            return new java.util.UUID(most, least);
        }

        public static UUIDWrapper of(java.util.UUID u) {
            return new UUIDWrapper(u.getMostSignificantBits(), u.getLeastSignificantBits());
        }
    }

    /** S2C：服务器配置 GUI 数据包（kind 可为 POTATO_BAKED：已烤熟的土豆服务器）。 */
    public record ServerGuiPayload(String posKey, String groupName, boolean isPublic, String password,
                                   int color, boolean applyOpen, int forceMode,
                                   int capacity, int used, int boundSatCount,
                                   List<String> memberNames, List<String> memberUuids,
                                   boolean canEdit, String kind) implements CustomPacketPayload {
        public static final Type<ServerGuiPayload> TYPE = new Type<>(id("server_gui"));
        public static final StreamCodec<ByteBuf, ServerGuiPayload> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public ServerGuiPayload decode(ByteBuf buf) {
                return new ServerGuiPayload(readString(buf), readString(buf), buf.readBoolean(), readString(buf),
                        buf.readInt(), buf.readBoolean(), ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                        readStrings(buf), readStrings(buf), buf.readBoolean(), readString(buf));
            }

            @Override
            public void encode(ByteBuf buf, ServerGuiPayload p) {
                writeString(buf, p.posKey());
                writeString(buf, p.groupName());
                buf.writeBoolean(p.isPublic());
                writeString(buf, p.password());
                buf.writeInt(p.color());
                buf.writeBoolean(p.applyOpen());
                ByteBufCodecs.VAR_INT.encode(buf, p.forceMode());
                ByteBufCodecs.VAR_INT.encode(buf, p.capacity());
                ByteBufCodecs.VAR_INT.encode(buf, p.used());
                ByteBufCodecs.VAR_INT.encode(buf, p.boundSatCount());
                writeStrings(buf, p.memberNames());
                writeStrings(buf, p.memberUuids());
                buf.writeBoolean(p.canEdit());
                writeString(buf, p.kind());
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** S2C：群内服务器方块轮廓颜色（§3.8 定案）。entries: "posKey|RRGGBB" */
    public record OutlinesPayload(List<String> entries) implements CustomPacketPayload {
        public static final Type<OutlinesPayload> TYPE = new Type<>(id("outlines"));
        public static final StreamCodec<ByteBuf, OutlinesPayload> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public OutlinesPayload decode(ByteBuf buf) {
                return new OutlinesPayload(readStrings(buf));
            }

            @Override
            public void encode(ByteBuf buf, OutlinesPayload p) {
                writeStrings(buf, p.entries());
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** C2S：请求服务器配置 GUI 数据（GUI 保存/踢人后的刷新）。 */
    public record RequestServerGuiPayload(String posKey) implements CustomPacketPayload {
        public static final Type<RequestServerGuiPayload> TYPE = new Type<>(id("request_server_gui"));
        public static final StreamCodec<ByteBuf, RequestServerGuiPayload> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public RequestServerGuiPayload decode(ByteBuf buf) {
                return new RequestServerGuiPayload(readString(buf));
            }

            @Override
            public void encode(ByteBuf buf, RequestServerGuiPayload p) {
                writeString(buf, p.posKey());
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** C2S：取消连接器配对暂存（右键空气）。 */
    public record PairClearPayload() implements CustomPacketPayload {
        public static final Type<PairClearPayload> TYPE = new Type<>(id("pair_clear"));
        public static final StreamCodec<ByteBuf, PairClearPayload> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public PairClearPayload decode(ByteBuf buf) {
                return new PairClearPayload();
            }

            @Override
            public void encode(ByteBuf buf, PairClearPayload p) {
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private Payloads() {}
}
