package com.thezeroer.nexalithic.core.infra.timer;

/**
 * 定时执行器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/25
 * @version 1.0.0
 */
public interface TimerExecutor<E extends Expirable> {
    void trigger(E e);
}
