package com.thezeroer.nexalithic.core.io.codec.wrapper;

import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.recyclable.TargetDynamicWrapperPool;

/**
 * 业务包包装器
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/03/11
 */
public class BusinessPacketWrapper extends TargetDynamicWrapperPool.InteriorRecyclableWrapper<BusinessPacket<?>, BusinessPacketWrapper> {
    private BusinessPacketWrapper prev;
    private BusinessPacketWrapper next;

    @Override
    public void onWrap(BusinessPacket<?> packet) {
        
    }
}
