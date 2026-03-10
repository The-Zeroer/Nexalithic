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
    private volatile T currentValue;

    private NexalithicOption(String name, T defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
    }

    public static <T> NexalithicOption<T> create(String name, T defaultValue) {
        return new NexalithicOption<T>(name, defaultValue);
    }

    public final String name() {
        return name;
    }
    public final T defaultValue() {
        return defaultValue;
    }

    public final void set(T value) {
        this.currentValue = value;
    }
    public final T get() {
        return currentValue;
    }

    public final T value() {
        return currentValue == null ? defaultValue : currentValue;
    }

    @Override
    public String toString() {
        return String.format("%s[current=%s](default=%s)", name, currentValue, defaultValue);
    }
}
