package com.thezeroer.nexalithic.core.messaging.handler;

import com.thezeroer.nexalithic.core.messaging.handler.mapping.HandlerPathMatcher;

/**
 * 处理元数据
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/07/09
 */
public record HandlerMetadata(String name, String description, HandlerPathMatcher pathMatcher) {

    @Override
    public String toString() {
        return "HandlerMetadata [name = \"" + name + "\", description = \"" + description + "\", pathMatcher = " + pathMatcher + "\"]";
    }
}
