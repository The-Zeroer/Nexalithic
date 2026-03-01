package com.thezeroer.nexalithic.core.model;

/**
 * 允许用户干预优先级的标记接口
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/05
 * @version 1.0.0
 */
public interface Prioritizable {
    /** 优先级紧急 */
    int PRIORITY_URGENT = -128;
    /** 优先级高 */
    int PRIORITY_HIGH = -64;
    /** 优先级普通 */
    int PRIORITY_NORMAL = 0;
    /** 优先级低 */
    int PRIORITY_LOW = 127;
    /**
     * 获取用户建议的优先级偏移量 (-128 ~ 127)
     */
    byte getSuggestedPriority();
}