package com.thezeroer.nexalithic.core.option;

/**
 * Nexalithic选项
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/02/19
 */
public class NexalithicOption<T> {
    private final String name;
    private final T defaultValue;

    private NexalithicOption(String name, T defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
    }

    public static <T> NexalithicOption<T> create(String name, T defaultValue) {
        return new NexalithicOption<T>(name, defaultValue);
    }

    public String name() {
        return name;
    }
    public T defaultValue() {
        return defaultValue;
    }

    @Override
    public String toString() {
        return String.format("%s(default=%s)", name, defaultValue);
    }
}
