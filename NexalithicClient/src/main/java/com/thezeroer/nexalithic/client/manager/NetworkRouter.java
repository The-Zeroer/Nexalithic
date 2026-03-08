package com.thezeroer.nexalithic.client.manager;

/**
 * 网络路由器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/07
 * @version 1.0.0
 */
public class NetworkRouter {
    public volatile String serverHost;

    public void setServerHost(String serverHost) {
        this.serverHost = serverHost;
    }
    public String getServerHost() {
        return serverHost;
    }
}
