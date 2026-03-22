package com.thezeroer.nexalithic.core.messaging.handler;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 处理器注册表
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/16
 * @version 1.0.0
 */
public class HandlerRegistry<HC extends HandlerContext<?>> {
    /**
     * 路径匹配器
     *
     * @author tbrtz647@outlook.com
     * @since 2026/03/16
     * @version 1.0.0
     */
    public static class PathMatcher {
        // 每一层 List 代表一个层级，内层 List 存储该层级的所有可选 Short 值
        private final List<List<Short>> levels = new ArrayList<>();

        /**
         * 添加深度节点 (Depth): 依次向下追加多个新的层级，每个层级包含一个固定值。
         * 语义：/level1/level2/level3...
         * @param paths 路径序列，例如 0x01, 0x02 会产生 [ [1], [2] ] 的结构
         */
        public PathMatcher addDepth(short... paths) {
            if (paths == null) {
                return this;
            }
            for (short p : paths) {
                this.levels.add(new ArrayList<>(Collections.singletonList(p)));
            }
            return this;
        }
        /**
         * 添加广度节点 (Breadth): 向下追加单个层级并添加多个可选值（横向扩展）。
         * 语义：/level1/(opt1|opt2|opt3)
         */
        public PathMatcher addBreadth(short... options) {
            if (options == null || options.length == 0) {
                return this;
            }
            List<Short> lastLevel = new ArrayList<>();
            for (short opt : options) {
                if (!lastLevel.contains(opt)) {
                    lastLevel.add(opt);
                }
            }
            this.levels.add(lastLevel);
            return this;
        }
        /**
         * 添加通配节点 (Wildcard): 在路径末尾追加一级“全匹配”层级。
         * 语义：/level1/*
         */
        public PathMatcher addWildcard() {
            this.levels.add(new ArrayList<>(Collections.singletonList(null)));
            return this;
        }

        public List<List<Short>> getLevels() {
            return levels;
        }

        /**
         * 辅助方法：合并另一个 PathMatcher 的所有层级
         */
        public PathMatcher combine(PathMatcher other) {
            if (other != null) {
                for (List<Short> level : other.getLevels()) {
                    this.levels.add(new ArrayList<>(level));
                }
            }
            return this;
        }
        public String formatPath() {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            if (!this.levels.isEmpty()) {
                for (List<Short> paths : this.levels) {
                    if (paths != null) {
                        sb.append("(").append(paths.stream().map(String::valueOf).collect(Collectors.joining("|"))).append(")");
                    } else {
                        sb.append("*");
                    }
                    sb.append('-');
                }
                sb.deleteCharAt(sb.length() - 1);
            } else {
                sb.append("*");
            }
            return sb.append("]").toString();
        }
    }
    /**
     * Trie 树节点定义
     * 优化方案：显式区分精确匹配分支与通配符分支
     */
    public static class TrieNode<HC extends HandlerContext<?>> {
        /** 精确匹配子节点：Key 为具体的协议 ID */
        private final TrieNodeChildrenStorage<HC> children;
        /** 通配符匹配子节点：如果当前层级匹配 *，则流向此节点 */
        private TrieNode<HC> wildcardChild;
        /** 绑定的处理器实例 */
        private NexalithicHandler<HC> handler;

        private TrieNode(TrieNodeChildrenStorage<HC> children) {
            this.children = children;
        }

        private TrieNodeChildrenStorage<HC> getChildren() {
            return children;
        }
        private void  setWildcardChild(TrieNode<HC> wildcardChild) {
            this.wildcardChild = wildcardChild;
        }
        private TrieNode<HC> getWildcardChild() {
            return wildcardChild;
        }
        private void setHandler(NexalithicHandler<HC> handler) {
            if (this.handler != null) {
                throw new IllegalStateException("TrieNode has already been set");
            }
            this.handler = handler;
        }
        private NexalithicHandler<HC> getHandler() {
            return handler;
        }
    }
    /**
     * TrieNode 子节点储藏
     *
     * @author tbrtz647@outlook.com
     * @since 2026/03/16
     * @version 1.0.0
     */
    public interface TrieNodeChildrenStorage<HC extends HandlerContext<?>> {
        /** 获取子节点 */
        TrieNode<HC> get(short key);

        /** 存入子节点 */
        void put(short key, TrieNode<HC> node);

        /** 获取所有子节点 */
        Collection<TrieNode<HC>> all();
    }

    private final Supplier<TrieNodeChildrenStorage<HC>> factory;
    private final TrieNode<HC> root;

    public HandlerRegistry(Supplier<TrieNodeChildrenStorage<HC>> factory) {
        this.factory = factory;
        this.root = new TrieNode<>(factory.get());
    }

    public static PathMatcher parse(HandlerMapping mapping) {
        if (mapping == null) {
            return null;
        }
        PathMatcher matcher = new PathMatcher();
        for (HandlerMapping.Level level : mapping.value()) {
            short[] levelValue = level.value();
            if (levelValue == null || levelValue.length == 0) {
                matcher.addWildcard();
            } else if (levelValue.length == 1) {
                matcher.addDepth(levelValue[0]);
            } else {
                matcher.addBreadth(levelValue);
            }
        }
        return matcher;
    }

    /**
     * 注册路径匹配器关联的处理器
     * @param matcher 路径匹配器（包含多级深度、广度及通配符逻辑）
     * @param handler 业务逻辑处理器
     */
    public void register(PathMatcher matcher, NexalithicHandler<HC> handler) {
        if (matcher == null || handler == null) {
            return;
        }
        List<List<Short>> levels = matcher.getLevels();
        if (levels.isEmpty()) {
            root.setHandler(handler);
            return;
        }
        doRegister(this.root, levels, 0, handler);
        if (handler.getName() == null) {
            handler.setName(matcher.formatPath());
        }
    }
    /**
     * 递归执行路径展开与节点创建
     */
    private void doRegister(TrieNode<HC> parent, List<List<Short>> levels, int depth, NexalithicHandler<HC> handler) {
        for (Short key : levels.get(depth)) {
            TrieNode<HC> childNode = getOrCreateChild(parent, key);
            if (depth == levels.size() - 1) {
                childNode.setHandler(handler);
            } else {
                doRegister(childNode, levels, depth + 1, handler);
            }
        }
    }
    private TrieNode<HC> getOrCreateChild(TrieNode<HC> parent, Short key) {
        if (key == null) {
            TrieNode<HC> wildcardChild = parent.getWildcardChild();
            if (wildcardChild == null) {
                wildcardChild = new TrieNode<>(factory.get());
                parent.setWildcardChild(wildcardChild);
            }
            return wildcardChild;
        }
        TrieNode<HC> child = parent.getChildren().get(key);
        if (child == null) {
            child = new TrieNode<>(factory.get());
            parent.getChildren().put(key, child);
        }
        return child;
    }

    /**
     * 根据路径匹配对应的处理器
     * 匹配策略：深度优先，精确匹配优先于通配符匹配
     *
     * @param path 协议路径数组
     * @return 匹配到的处理器，若未找到则返回 null
     */
    public NexalithicHandler<HC> match(short[] path) {
        if (root == null) {
            return null;
        }
        if (path == null) {
            return root.getHandler();
        }
        return doMatch(root, path, 0);
    }
    /**
     * 递归匹配逻辑
     */
    private NexalithicHandler<HC> doMatch(TrieNode<HC> current, short[] path, int depth) {
        if (depth == path.length) {
            return current.getHandler();
        }
        NexalithicHandler<HC> handler = null;
        TrieNode<HC> nextNode = current.getChildren().get(path[depth]);
        if (nextNode != null) {
            handler = doMatch(nextNode, path, depth + 1);
        }
        if (handler == null) {
            TrieNode<HC> wildcardNode = current.getWildcardChild();
            if (wildcardNode != null) {
                handler = doMatch(wildcardNode, path, depth + 1);
            }
        }
        return handler;
    }

    /**
     * 数组 trie 节点子存储</br>
     * 适用于 ID 范围固定且连续的情况（如 0-255）
     * @author tbrtz647@outlook.com
     * @since 2026/03/16
     * @version 1.0.0
     */
    public static class ArrayTrieNodeChildrenStorage<HC extends HandlerContext<?>> implements TrieNodeChildrenStorage<HC> {
        private final TrieNode<HC>[] array;
        @SuppressWarnings("unchecked")
        public ArrayTrieNodeChildrenStorage(int size) {
            this.array = (TrieNode<HC>[]) new TrieNode[size];
        }
        @Override
        public TrieNode<HC> get(short key) {
            return (key >= 0 && key < array.length) ? array[key] : null;
        }
        @Override
        public void put(short key, TrieNode<HC> node) {
            if (key >= 0 && key < array.length) array[key] = node;
        }
        @Override
        public Collection<TrieNode<HC>> all() {
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
    public static class MapTrieNodeChildrenStorage<HC extends HandlerContext<?>> implements TrieNodeChildrenStorage<HC> {
        private final Map<Short, TrieNode<HC>> map = new HashMap<>();
        @Override
        public TrieNode<HC> get(short key) { return map.get(key); }
        @Override
        public void put(short key, TrieNode<HC> node) { map.put(key, node); }
        @Override
        public Collection<TrieNode<HC>> all() { return map.values(); }
    }
}
