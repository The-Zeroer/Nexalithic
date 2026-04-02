package com.thezeroer.nexalithic.core.option;

import com.thezeroer.nexalithic.core.exception.NexalithicOptionException;

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
    private final OptionValidator<T> validator;
    private volatile T currentValue;

    private NexalithicOption(String name, T defaultValue, OptionValidator<T> validator) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.validator = validator;
        if (validator != null) {
            validator.validate(defaultValue);
        }
    }
    public static <T> NexalithicOption<T> create(String name, T defaultValue) {
        return new NexalithicOption<>(name, defaultValue, null);
    }
    public static <T> NexalithicOption<T> create(String name, T defaultValue, OptionValidator<T> validator) {
        return new NexalithicOption<>(name, defaultValue, validator);
    }

    public final String name() {
        return name;
    }
    public final T defaultValue() {
        return defaultValue;
    }

    public final void set(T value) {
        if (validator != null) {
            try {
                validator.validate(value);
            } catch (IllegalArgumentException e) {
                throw new NexalithicOptionException(name, e.getMessage());
            }
        }
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
