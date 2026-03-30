package com.thezeroer.nexalithic.core.messaging.handler;

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

    /**
     * 数组 trie 节点子存储</br>
     * 适用于 ID 范围固定且连续的情况（如 0-255）
     * @author tbrtz647@outlook.com
     * @since 2026/03/16
     * @version 1.0.0
     */
    class ArrayTrieNodeChildrenStorage<HC extends HandlerContext<?>> implements TrieNodeChildrenStorage<HC> {
        private final HandlerRegistry.TrieNode<HC>[] array;
        @SuppressWarnings("unchecked")
        public ArrayTrieNodeChildrenStorage(int size) {
            this.array = (HandlerRegistry.TrieNode<HC>[]) new HandlerRegistry.TrieNode[size];
        }
        @Override
        public HandlerRegistry.TrieNode<HC> get(short key) {
            return (key >= 0 && key < array.length) ? array[key] : null;
        }
        @Override
        public void put(short key, HandlerRegistry.TrieNode<HC> node) {
            if (key >= 0 && key < array.length) array[key] = node;
        }
        @Override
        public Collection<HandlerRegistry.TrieNode<HC>> all() {
            return Arrays.stream(array).filter(Objects::nonNull).toList();
        }
    }
    /**
     * 映射 Trie 节点子存储</br>
     * 适用于 ID 极其分散或范围无法预知的情况
     * @author tbrtz647@outlook.com
     * @since 2026/03/16
     * @version 1.0.0
     */
    class MapTrieNodeChildrenStorage<HC extends HandlerContext<?>> implements TrieNodeChildrenStorage<HC> {
        private final Map<Short, HandlerRegistry.TrieNode<HC>> map = new HashMap<>();
        @Override
        public HandlerRegistry.TrieNode<HC> get(short key) { return map.get(key); }
        @Override
        public void put(short key, HandlerRegistry.TrieNode<HC> node) { map.put(key, node); }
        @Override
        public Collection<HandlerRegistry.TrieNode<HC>> all() { return map.values(); }
    }
}
