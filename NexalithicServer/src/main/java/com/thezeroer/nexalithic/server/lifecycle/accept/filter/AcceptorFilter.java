package com.thezeroer.nexalithic.server.lifecycle.accept.filter;

import com.thezeroer.nexalithic.server.lifecycle.accept.FiltrationStrategy;

import java.nio.channels.SocketChannel;

/**
 * 接受器连接过滤器，用于在 TCP 连接建立初期执行快速校验。
 *
 * <p><b>核心功能：</b><br>
 * 本接口运行在连接接收（Accept）阶段的极早期。它允许开发者在不申请昂贵的业务资源前，
 * 针对 {@link SocketChannel} 的元数据（如远程 IP 地址、端口、并发连接数等）进行合法性判定。</p>
 *
 * <p><b>执行规约：</b>
 * <ul>
 * <li><b>无状态性：</b>建议实现类保持无状态或线程安全，以便在多个 {@link FiltrationStrategy} 中复用。</li>
 * <li><b>判别逻辑：</b>返回 {@code true} 表示允许连接继续传递；返回 {@code false} 表示拦截。</li>
 * </ul>
 * </p>
 *
 * <p><b>典型应用场景：</b>
 * <ul>
 * <li>黑白名单过滤 (IP Blacklist/Whitelist)</li>
 * <li>连接限流 (Rate Limiting)</li>
 * <li>地域限制 (Geo-fencing)</li>
 * </ul>
 * </p>
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/07
 * @version 1.1.0
 */
public interface AcceptorFilter {

    /**
     * 执行过滤逻辑。
     *
     * @param socketChannel 待校验的原始套接字通道。
     * @return {@code true} 如果连接通过校验；{@code false} 如果连接被拦截，
     * 随后框架将调用 {@code context.reject()} 物理关闭该通道。
     */
    boolean doFilter(SocketChannel socketChannel);
}
