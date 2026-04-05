package com.thezeroer.nexalithic.core.builder.module;

/**
 * Nexalithic 模块
 *
 * @author tbrtz647@outlook.com
 * @since 2026/04/04
 * @version 1.0.0
 */
public class NexalithicModule<T> {
    private final String name;
    private final Class<T> type;

    private NexalithicModule(String name, Class<T> type) {
        this.name = name;
        this.type = type;
    }

    @SuppressWarnings("unchecked")
    public static <T> NexalithicModule<T> create(String name, Class<?> type) {
        return new NexalithicModule<>(name, (Class<T>) type);
    }

    public final String name() {
        return name;
    }
    public final Class<T> type() {
        return type;
    }
}
