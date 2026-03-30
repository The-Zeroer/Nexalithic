package com.thezeroer.nexalithic.core.timer;

/**
 * 有限期的
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/25
 * @version 1.0.0
 */
public interface Expirable {
    long getExpiryTime();
    boolean onExpiryTriggered();
    boolean isCancelled();
}
