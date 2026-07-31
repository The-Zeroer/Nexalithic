package com.thezeroer.nexalithic.core.messaging.handler.assembly.interceptor;

import java.util.Map;

/**
 * 拦截器配置
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/07/12
 */
public class InterceptorConfiguration {
    private final String name;
    private final Map<String, String> properties;

    public InterceptorConfiguration(String name, Map<String, String> properties) {
        this.name = name;
        this.properties = Map.copyOf(properties);
    }

    public String getName() {
        return name;
    }

    public String getStringValue(String propertyName) {
        String value = properties.get(propertyName);
        if (value == null) {
            throw new IllegalArgumentException("Missing interceptor property: " + propertyName);
        }
        return value;
    }
    public String getStringValue(String propertyName, String defaultValue) {
        String value = properties.get(propertyName);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    public int getIntValue(String propertyName) {
        String value = getStringValue(propertyName);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid int interceptor property: " + propertyName + "=" + value, e);
        }
    }
    public int getIntValue(String propertyName, int defaultValue) {
        String value = properties.get(propertyName);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid int interceptor property: " + propertyName + "=" + value, e);
        }
    }

    public long getLongValue(String propertyName) {
        String value = getStringValue(propertyName);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid long interceptor property: " + propertyName + "=" + value, e);
        }
    }
    public long getLongValue(String propertyName, long defaultValue) {
        String value = properties.get(propertyName);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid long interceptor property: " + propertyName + "=" + value, e);
        }
    }

    public float getFloatValue(String propertyName) {
        String value = getStringValue(propertyName);
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid float interceptor property: " + propertyName + "=" + value, e);
        }
    }
    public float getFloatValue(String propertyName, float defaultValue) {
        String value = properties.get(propertyName);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid float interceptor property: " + propertyName + "=" + value, e);
        }
    }

    public double getDoubleValue(String propertyName) {
        String value = getStringValue(propertyName);
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid double interceptor property: " + propertyName + "=" + value, e);
        }
    }
    public double getDoubleValue(String propertyName, double defaultValue) {
        String value = properties.get(propertyName);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid double interceptor property: " + propertyName + "=" + value, e);
        }
    }

    public boolean getBooleanValue(String propertyName) {
        return Boolean.parseBoolean(getStringValue(propertyName));
    }
    public boolean getBooleanValue(String propertyName, boolean defaultValue) {
        String value = properties.get(propertyName);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }
}
