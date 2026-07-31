package com.thezeroer.nexalithic.core.messaging.handler.mapping;

import com.thezeroer.nexalithic.core.exception.NexalithicDuplicateKeyException;
import com.thezeroer.nexalithic.core.messaging.handler.HandlerContext;
import com.thezeroer.nexalithic.core.messaging.handler.NexalithicHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;

/**
 * 处理器注册表
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/16
 * @version 1.0.0
 */
public class HandlerRegistry<HC extends HandlerContext<?>> {
    private static final Logger logger = LoggerFactory.getLogger(HandlerRegistry.class);
    private final TrieNode<HC> root;
    private HandlerRegistry(TrieNode<HC> root) {
        this.root = root;
    }

    public static <HC extends HandlerContext<?>> Builder<HC> builder() {
        return new Builder<>();
    }

    /**
     * 根据路径匹配对应的处理器。
     *
     * <p>匹配规则：</p>
     * <ol>
     *     <li>路径长度必须完全一致；</li>
     *     <li>优先匹配精确节点；</li>
     *     <li>精确分支无法完整匹配时，再尝试通配符分支；</li>
     *     <li>不允许回退到较短路径上的Handler。</li>
     * </ol>
     *
     * @param path 协议路径数组
     * @return 匹配到的处理器，未匹配则返回null
     */
    public NexalithicHandler<HC> match(short[] path) {
        if (root == null) {
            return null;
        }
        if (path == null) {
            return root.handler();
        }
        return doMatch(root, path, 0);
    }
    /**
     * 递归匹配。
     *
     * <p>通配符节点只匹配当前的一个路径层级。</p>
     */
    private NexalithicHandler<HC> doMatch(TrieNode<HC> current, short[] path, int depth) {
        if (depth == path.length) {
            return current.handler();
        }
        TrieNode<HC> exactNode = current.children().get(path[depth]);
        if (exactNode != null) {
            NexalithicHandler<HC> exactHandler = doMatch(exactNode, path, depth + 1);
            if (exactHandler != null) {
                return exactHandler;
            }
        }
        TrieNode<HC> wildcardNode = current.wildcard();
        if (wildcardNode != null) {
            return doMatch(wildcardNode, path, depth + 1);
        }
        return null;
    }
    public static class Builder<HC extends HandlerContext<?>> {
        private Function<Integer, TrieNodeChildrenStorage<HC>> factory = (count) -> new MapTrieNodeChildrenStorage<>();
        private final MutableNode<HC> root = new MutableNode<>();

        /**
         * 注册路径匹配器关联的处理器
         * @param matcher 路径匹配器（包含多级深度、广度及通配符逻辑）
         * @param handler 业务逻辑处理器
         */
        public void register(HandlerPathMatcher matcher, NexalithicHandler<HC> handler) {
            if (matcher == null || handler == null) {
                return;
            }
            List<short[]> levels = matcher.getLevels();
            if (levels.isEmpty()) {
                bindHandler(root, handler);
                return;
            }
            doRegister(root, levels, 0, handler);
        }
        private void doRegister(MutableNode<HC> parent, List<short[]> levels, int depth, NexalithicHandler<HC> handler) {
            short[] level = levels.get(depth);
            if (level.length == 0) {
                registerNext(parent.getOrCreateWildcard(), levels, depth, handler);
            } else {
                for (short key : level) {
                    registerNext(parent.getOrCreateChild(key), levels, depth, handler);
                }
            }
        }
        private void registerNext(MutableNode<HC> child, List<short[]> levels, int depth, NexalithicHandler<HC> handler) {
            if (depth == levels.size() - 1) {
                bindHandler(child, handler);
                return;
            }
            doRegister(child, levels, depth + 1, handler);
        }
        private void bindHandler(MutableNode<HC> node, NexalithicHandler<HC> handler) {
            NexalithicHandler<HC> existing = node.handler;
            if (existing != null) {
                throw new NexalithicDuplicateKeyException("HandlerRegistry", existing.getMetadata());
            }
            node.handler = handler;
            logger.debug("Registered handler mappings, {}", handler.getMetadata());
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

    /**
     * Trie 树节点定义
     * 优化方案：显式区分精确匹配分支与通配符分支
     *
     * @param children 精确匹配子节点：Key 为具体的协议 ID
     * @param wildcard 通配符匹配子节点：如果当前层级匹配 *，则流向此节点
     * @param handler  绑定的处理器实例
     */
    public record TrieNode<HC extends HandlerContext<?>>(TrieNodeChildrenStorage<HC> children,
                                                         TrieNode<HC> wildcard,
                                                         NexalithicHandler<HC> handler) {
    }
}
