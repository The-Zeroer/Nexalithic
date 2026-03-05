package com.thezeroer.nexalithic.server.manager;

import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;

import java.util.EnumMap;

/**
 * 网络路由器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/03
 * @version 1.0.0
 */
public class NetworkRouter {
    private final EnumMap<AbstractPacket.PacketType, String> address = new EnumMap<>(AbstractPacket.PacketType.class);
}
