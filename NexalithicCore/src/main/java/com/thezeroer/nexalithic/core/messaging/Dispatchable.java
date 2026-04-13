package com.thezeroer.nexalithic.core.messaging;

/**
 * 可调度
 *
 * @author tbrtz647@outlook.com
 * @since 2026/04/13
 * @version 1.0.0
 */
public interface Dispatchable {
    enum Type {
        Handler,
        Task,
    }

    Type type();
}
