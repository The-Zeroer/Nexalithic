package com.thezeroer.nexalithic.core.messaging.handler;

import java.util.*;
import java.util.function.Function;
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
    @SuppressWarnings("UnusedReturnValue")
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
        private final TrieNode<HC> wildcard;
        /** 绑定的处理器实例 */
        private final NexalithicHandler<HC> handler;
        public TrieNode(TrieNodeChildrenStorage<HC> children, TrieNode<HC> wildcard, NexalithicHandler<HC> handler) {
            this.children = children;
            this.wildcard = wildcard;
            this.handler = handler;
        }
        private TrieNodeChildrenStorage<HC> getChildren() {
            return children;
        }
        private TrieNode<HC> getWildcard() {
            return wildcard;
        }
        private NexalithicHandler<HC> getHandler() {
            return handler;
        }
    }

    private final TrieNode<HC> root;

    public static <HC extends HandlerContext<?>> Builder<HC> builder() {
        return new Builder<>();
    }

    private HandlerRegistry(TrieNode<HC> root) {
        this.root = root;
    }

    public static PathMatcher parse(HandlerMapping mapping) {
        if (mapping == null) {
            return null;
        }
        PathMatcher matcher = new PathMatcher();
        short[] simplePath = mapping.value();
        HandlerMapping.Level[] levels = mapping.levels();
        if (levels.length > 0) {
            for (HandlerMapping.Level level : levels) {
                processLevel(matcher, level.value());
            }
        } else if (simplePath.length > 0) {
            processLevel(matcher, simplePath);
        }
        return matcher;
    }
    private static void processLevel(PathMatcher matcher, short[] values) {
        if (values == null || values.length == 0) {
            matcher.addWildcard();
        } else if (values.length == 1) {
            matcher.addDepth(values[0]);
        } else {
            matcher.addBreadth(values);
        }
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
            TrieNode<HC> wildcardNode = current.getWildcard();
            if (wildcardNode != null) {
                doMatch(wildcardNode, path, depth + 1);
            }
            handler = current.getHandler();
        }
        return handler;
    }

    public static class Builder<HC extends HandlerContext<?>> {
        private Function<Integer, TrieNodeChildrenStorage<HC>> factory = (count) -> new TrieNodeChildrenStorage.MapTrieNodeChildrenStorage<>();
        private final MutableNode<HC> root = new MutableNode<>();

        /**
         * 注册路径匹配器关联的处理器
         * @param matcher 路径匹配器（包含多级深度、广度及通配符逻辑）
         * @param handler 业务逻辑处理器
         */
        public void register(PathMatcher matcher, NexalithicHandler<HC> handler) {
            if (matcher == null || handler == null) {
                return;
            }
            if (handler.getName() == null) {
                handler.setName(matcher.formatPath());
            }
            List<List<Short>> levels = matcher.getLevels();
            if (levels.isEmpty()) {
                root.handler = handler;
                return;
            }
            doRegister(root, levels, 0, handler);
        }
        private void doRegister(MutableNode<HC> parent, List<List<Short>> levels, int depth, NexalithicHandler<HC> handler) {
            for (Short key : levels.get(depth)) {
                MutableNode<HC> child = (key == null) ? parent.getOrCreateWildcard() : parent.getOrCreateChild(key);
                if (depth == levels.size() - 1) {
                    child.handler = handler;
                } else {
                    doRegister(child, levels, depth + 1, handler);
                }
            }
        }

        public void factory(Function<Integer, TrieNodeChildrenStorage<HC>> factory) {
            this.factory = factory;
        }

        public HandlerRegistry<HC> build() {
            return new HandlerRegistry<>(freeze(root));
        }
        private TrieNode<HC> freeze(MutableNode<HC> mutable) {
            TrieNodeChildrenStorage<HC> storage = factory.apply(mutable.children.size());
            for (Map.Entry<Short, MutableNode<HC>> entry : mutable.children.entrySet()) {
                storage.put(entry.getKey(), freeze(entry.getValue()));
            }
            TrieNode<HC> wildcard = (mutable.wildcard != null) ? freeze(mutable.wildcard) : null;
            return new TrieNode<>(storage, wildcard, mutable.handler);
        }
    }
    private static class MutableNode<HC extends HandlerContext<?>> {
        final Map<Short, MutableNode<HC>> children = new HashMap<>();
        MutableNode<HC> wildcard;
        NexalithicHandler<HC> handler;

        MutableNode<HC> getOrCreateChild(short key) {
            return children.computeIfAbsent(key, k -> new MutableNode<>());
        }
        MutableNode<HC> getOrCreateWildcard() {
            if (wildcard == null) {
                wildcard = new MutableNode<>();
            }
            return wildcard;
        }
    }
}
