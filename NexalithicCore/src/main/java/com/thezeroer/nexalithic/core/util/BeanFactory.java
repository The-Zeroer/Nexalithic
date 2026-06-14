package com.thezeroer.nexalithic.core.util;

/**
 * Bean 工厂
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/16
 * @version 1.0.0
 */
@FunctionalInterface
public interface BeanFactory {
    <T> T getBean(Class<T> clazz);
}