package com.hmod.create_satnav.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/** 卫星绑定关系：绑到哪个服务群（root 键）、由谁绑定（§3.5 归属 / §7 星空判定依据）。 */
public record SatelliteLink(String groupRoot, UUID owner) {

    public static final Codec<SatelliteLink> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("group_root").forGetter(SatelliteLink::groupRoot),
            Codec.STRING.fieldOf("owner").forGetter(l -> l.owner().toString())
    ).apply(i, (root, owner) -> new SatelliteLink(root, java.util.UUID.fromString(owner))));

    public static final StreamCodec<ByteBuf, SatelliteLink> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SatelliteLink::groupRoot,
            ByteBufCodecs.STRING_UTF8, l -> l.owner().toString(),
            (root, owner) -> new SatelliteLink(root, java.util.UUID.fromString(owner)));

    public boolean bound() {
        return groupRoot != null && !groupRoot.isEmpty();
    }
}
