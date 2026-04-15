package com.thezeroer.nexalithic.client.event;

import java.util.EventListener;

/**
 * 链接状态监听器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/04/15
 * @version 1.0.0
 */
public interface LinkStatusListener extends EventListener {
    /**
     * 链路状态。
     * <p>
     * 该枚举描述了客户端与服务器之间链路的逻辑生存周期，屏蔽了底层多通道（信令/业务）的构建细节。
     * </p>
     *
     * @author tbrtz647@outlook.com
     * @since 2026/04/15
     * @version 1.0.0
     */
    enum Status {
        /**
         * 未连接。
         * <p>初始状态或连接已彻底断开（包括主动关闭或重连失败）。</p>
         */
        UNLINKED,

        /**
         * 连接中。
         * <p>正在执行建立物理连接、安全协议握手以及内部多通道协商等过程。</p>
         */
        LINKING,

        /**
         * 已连接。
         * <p>全链路已就绪，业务通道已对齐，开发者可以正常进行业务交互。</p>
         */
        LINKED,

        /**
         * 正在重连。
         * <p>链路发生非预期中断，框架正在尝试自动恢复连接。
         * 此时请求可能会被挂起或根据配置直接失败。</p>
         */
        RECONNECTING
    }

    /**
     * 断开原因
     *
     * @author tbrtz647@outlook.com
     * @since 2026/04/15
     * @version 1.0.0
     */
    enum DisconnectReason {
        /** 无原因（非断开状态时的默认值） */
        NONE,
        /** 客户端主动关闭 */
        LOCAL_ACTIVE,
        /** 服务器主动断开 */
        REMOTE_ACTIVE,
        /** 网络异常（如超时、物理断网、心跳丢失） */
        NETWORK_ERROR,
        /** 协议错误（如密钥协商失败、非法数据包） */
        PROTOCOL_ERROR
    }

    /**
     * 事件
     *
     * @author tbrtz647@outlook.com
     * @since 2026/04/15
     * @version 1.0.0
     */
    record Event(Status from, Status to, DisconnectReason reason) {
        public static Event of(Status from, Status to, DisconnectReason reason) {
            return new Event(from, to, reason);
        }
    }
    record EventKey(Status from, Status to) {
        public static EventKey of(Status from, Status to) {
            return new EventKey(from, to);
        }
    }

    void onTrigger(Event event);
}