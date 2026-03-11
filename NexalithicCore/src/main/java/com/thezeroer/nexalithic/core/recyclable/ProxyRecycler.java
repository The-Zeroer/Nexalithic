package com.thezeroer.nexalithic.core.recyclable;

/**
 * 代理回收器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/11
 * @version 1.0.0
 */
public interface ProxyRecycler<W> {
    boolean release(W w);
}