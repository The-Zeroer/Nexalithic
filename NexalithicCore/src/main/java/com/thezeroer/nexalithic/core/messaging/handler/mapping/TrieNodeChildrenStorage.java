package com.thezeroer.nexalithic.core.messaging.handler.mapping;

import com.thezeroer.nexalithic.core.messaging.handler.HandlerContext;

import java.util.*;

/**
 * TrieNode 子节点储藏
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/16
 * @version 1.0.0
 */
public interface TrieNodeChildrenStorage<HC extends HandlerContext<?>> {
    /** 获取子节点 */
    HandlerRegistry.TrieNode<HC> get(short key);

    /** 存入子节点 */
    void put(short key, HandlerRegistry.TrieNode<HC> node);

    /** 获取所有子节点 */
    Collection<HandlerRegistry.TrieNode<HC>> all();
}
