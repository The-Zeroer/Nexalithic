package com.thezeroer.nexalithic.core.option;

import com.thezeroer.nexalithic.core.io.loop.AbstractLoop;
import com.thezeroer.nexalithic.core.io.loop.ChannelLoop;

/**
 * 核心选项集
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/10
 * @version 1.0.0
 */
public class CoreOptionSet {
    public static final class AbstractLoop_ {
        public static final NexalithicOption<Integer> Max_Shutdown_Wait = AbstractLoop.Max_Shutdown_Wait;
    }
    public static final class ChannelLoop_ {
        public static final NexalithicOption<Integer> InterestQueue_Capacity = ChannelLoop.InterestQueue_Capacity;
    }
}
