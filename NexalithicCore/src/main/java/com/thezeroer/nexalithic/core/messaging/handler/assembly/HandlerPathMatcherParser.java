package com.thezeroer.nexalithic.core.messaging.handler.assembly;

import com.thezeroer.nexalithic.core.messaging.handler.assembly.annotation.HandlerPathLevel;
import com.thezeroer.nexalithic.core.messaging.handler.assembly.annotation.NexalithicHandlerController;
import com.thezeroer.nexalithic.core.messaging.handler.assembly.annotation.NexalithicHandlerMethod;
import com.thezeroer.nexalithic.core.messaging.handler.mapping.HandlerPathMatcher;

/**
 * 处理路径匹配解析器。
 *
 * <p>负责将Controller公共路径和Handler方法局部路径
 * 合并为最终的{@link HandlerPathMatcher}。</p>
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/07/24
 */
public final class HandlerPathMatcherParser {
    private HandlerPathMatcherParser() {}

    /**
     * 解析Controller与Handler方法的完整路径。
     *
     * <p>Controller路径在前，Handler方法路径在后。</p>
     *
     * @param controller Controller注解
     * @param method Handler方法注解
     * @return 完整路径匹配器
     */
    public static HandlerPathMatcher parse(NexalithicHandlerController controller, NexalithicHandlerMethod method) {
        HandlerPathMatcher matcher = new HandlerPathMatcher();
        append(matcher, controller.value(), controller.levels());
        append(matcher, method.value(), method.levels());
        return matcher;
    }

    /**
     * 将注解中的路径配置追加到匹配器。
     *
     * <p>当levels不为空时使用levels，否则使用value。</p>
     */
    private static void append(HandlerPathMatcher matcher, short[] value, HandlerPathLevel[] levels) {
        if (value.length > 0 && levels.length > 0) {
            throw new IllegalArgumentException("value and levels cannot both be specified");
        }
        if (levels.length > 0) {
            for (HandlerPathLevel level : levels) {
                short[] options = level.value();
                if (options.length == 0) {
                    matcher.addWildcard();
                } else {
                    matcher.addBreadth(options);
                }
            }
            return;
        }
        if (value.length > 0) {
            matcher.addDepth(value);
        }
    }
}