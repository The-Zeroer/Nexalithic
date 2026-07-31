package com.thezeroer.nexalithic.core.messaging.handler.mapping;

import com.thezeroer.nexalithic.core.messaging.handler.HandlerContext;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 映射 Trie 节点子存储</br>
 * 适用于 ID 极其分散或范围无法预知的情况
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/03/16
 */
public class MapTrieNodeChildrenStorage<HC extends HandlerContext<?>> implements TrieNodeChildrenStorage<HC> {
    private final Map<Short, HandlerRegistry.TrieNode<HC>> map = new HashMap<>();

    @Override
    public HandlerRegistry.TrieNode<HC> get(short key) {
        return map.get(key);
    }

    @Override
    public void put(short key, HandlerRegistry.TrieNode<HC> node) {
        map.put(key, node);
    }

    @Override
    public Collection<HandlerRegistry.TrieNode<HC>> all() {
        return map.values();
    }
}
