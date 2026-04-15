package com.thezeroer.nexalithic.client.manager;

import com.thezeroer.nexalithic.client.event.LinkStatusListener;
import com.thezeroer.nexalithic.client.event.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 链接状态管理器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/04/15
 * @version 1.0.0
 */
public class LinkStatusManager {
    private static final Logger logger = LoggerFactory.getLogger(LinkStatusManager.class);
    private final Map<LinkStatusListener.EventKey, Queue<LinkStatusListener>> events = new ConcurrentHashMap<>();
    private final AtomicReference<LinkStatusListener.Status> linkStatus = new AtomicReference<>(LinkStatusListener.Status.UNLINKED);

    /**
     * 注册一个状态转移监听器。
     *
     * @param event  状态转移定义（包含起始状态和目标状态，支持 null 作为通配符）
     * @param listener 状态转移触发时的回调逻辑
     * @return 用于注销该监听器的注册句柄
     */
    public Registration onStatusTransition(LinkStatusListener.EventKey event, LinkStatusListener listener) {
        Queue<LinkStatusListener> listeners = events.computeIfAbsent(event, k -> new ConcurrentLinkedQueue<>());
        listeners.add(listener);
        return () -> {
            listeners.remove(listener);
            if (listeners.isEmpty()) {
                events.remove(event, listeners);
            }
        };
    }

    /**
     * 注册一个一次性状态转移监听器。
     * <p>回调函数在第一次触发后会自动注销。</p>
     *
     * @param event  状态转移定义
     * @param listener 触发后的回调逻辑
     * @return 用于提前手动注销的句柄
     */
    public Registration onStatusTransitionOnce(LinkStatusListener.EventKey event, LinkStatusListener listener) {
        final Registration[] registration = new Registration[1];
        LinkStatusListener wrappedListener = (realEvent) -> {
            try {
                listener.onTrigger(realEvent);
            } finally {
                if (registration[0] != null) {
                    registration[0].unregister();
                }
            }
        };
        registration[0] = onStatusTransition(event, wrappedListener);
        return registration[0];
    }

    /**
     * 精确匹配监听：只有当状态从特定的 {@code from} 转移到 {@code to} 时才触发。
     *
     * @param from 起始状态
     * @param to   目标状态
     * @param listener 回调逻辑
     * @return 注册句柄
     */
    public Registration onStatusTransition(LinkStatusListener.Status from, LinkStatusListener.Status to, LinkStatusListener listener) {
        return onStatusTransition(LinkStatusListener.EventKey.of(from, to), listener);
    }

    /**
     * 进入状态监听：只要链路进入了目标状态 {@code to} 就会触发，不关注前驱状态。
     *
     * @param to     目标状态
     * @param listener 回调逻辑
     * @return 注册句柄
     */
    public Registration onEnterStatus(LinkStatusListener.Status to, LinkStatusListener listener) {
        return onStatusTransition(LinkStatusListener.EventKey.of(null, to), listener);
    }

    /**
     * 离开状态监听：只要链路离开了当前状态 {@code from} 就会触发，不关注后继状态。
     *
     * @param from   起始状态
     * @param listener 回调逻辑
     * @return 注册句柄
     */
    public Registration onLeaveStatus(LinkStatusListener.Status from, LinkStatusListener listener) {
        return onStatusTransition(LinkStatusListener.EventKey.of(from, null), listener);
    }

    /**
     * 触发状态转移事件。
     * <p>按照“精确匹配 -> 进入匹配 -> 离开匹配”的顺序依次调用相关监听器。</p>
     *
     * @param to   实际发生的结束状态
     * @param reason 原因
     */
    public void trigger(LinkStatusListener.Status to, LinkStatusListener.DisconnectReason reason) {
        LinkStatusListener.Status from = linkStatus.get();
        if (from == to) {
            return;
        }
        if (!linkStatus.compareAndSet(from, to)) {
            trigger(to, reason);
            return;
        }
        LinkStatusListener.Event event = LinkStatusListener.Event.of(from, to, reason);
        // 按照 匹配粒度 从细到粗进行分发
        triggerSpecific(LinkStatusListener.EventKey.of(from, to), event);   // 1. 精确匹配
        triggerSpecific(LinkStatusListener.EventKey.of(null, to), event);   // 2. 进入状态
        triggerSpecific(LinkStatusListener.EventKey.of(from, null), event); // 3. 离开状态
        triggerSpecific(LinkStatusListener.EventKey.of(null, null), event); // 4. 全局监听
    }
    public void trigger(LinkStatusListener.Status to) {
        trigger(to, LinkStatusListener.DisconnectReason.NONE);
    }

    /**
     * 查看当前状态
     *
     * @return {@code LinkStatusListener.Status} 当前与服务端的连接状态
     */
    public LinkStatusListener.Status getCurrentStatus() {
        return linkStatus.get();
    }

    /**
     * 内部私有方法：执行指定转移定义下的所有监听器
     */
    private void triggerSpecific(LinkStatusListener.EventKey eventKey, LinkStatusListener.Event event) {
        Queue<LinkStatusListener> listeners = events.get(eventKey);
        if (listeners != null) {
            listeners.forEach(listener -> {
                long start = System.currentTimeMillis();
                try {
                    listener.onTrigger(event);
                } catch (Exception e) {
                    logger.warn("LinkStatusManager: Exception in listener {} during {}", listener.getClass().getSimpleName(), eventKey, e);
                } finally {
                    long duration = System.currentTimeMillis() - start;
                    if (duration > 100) {
                        logger.warn("LinkStatusManager: Heavy listener detected! {} took {}ms. Please move blocking logic to business threads.",
                                eventKey, duration);
                    }
                }
            });
        }
    }
}
