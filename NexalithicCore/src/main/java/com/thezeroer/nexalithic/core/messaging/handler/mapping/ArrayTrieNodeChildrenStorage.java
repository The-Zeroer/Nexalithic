package com.thezeroer.nexalithic.core.messaging.handler.mapping;

import com.thezeroer.nexalithic.core.messaging.handler.HandlerContext;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

/**
 * 数组 trie 节点子存储</br>
 * 适用于 ID 范围固定且连续的情况（如 0-255）
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/03/16
 */
public class ArrayTrieNodeChildrenStorage<HC extends HandlerContext<?>> implements TrieNodeChildrenStorage<HC> {
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
