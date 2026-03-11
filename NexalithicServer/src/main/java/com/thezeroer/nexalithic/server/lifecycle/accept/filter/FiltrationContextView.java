package com.thezeroer.nexalithic.server.lifecycle.accept.filter;

/**
 * 过滤上下文视图
 * @see FiltrationContext
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/03/11
 */
public interface FiltrationContextView {
    public void approve();
    public void reject();
}
