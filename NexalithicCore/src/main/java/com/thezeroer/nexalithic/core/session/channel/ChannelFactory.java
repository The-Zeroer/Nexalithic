package com.thezeroer.nexalithic.core.session.channel;

import com.thezeroer.nexalithic.core.security.SecretKeyContext;

/**
 * 通道工厂
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/09
 * @version 1.0.0
 */
public interface ChannelFactory<S, SC, BC> {
    SC createSignaling(S session, SecretKeyContext key);
    BC createBusiness(S session, SecretKeyContext key);
}