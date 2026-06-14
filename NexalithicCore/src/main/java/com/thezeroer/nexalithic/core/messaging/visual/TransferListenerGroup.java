package com.thezeroer.nexalithic.core.messaging.visual;

/**
 * 传输监听器组
 *
 * @author tbrtz647@outlook.com
 * @since 2026/04/06
 * @version 1.0.0
 */
public record TransferListenerGroup(long taskId, TransferListener requestTransferListener, TransferListener responseTransferListener) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private TransferListener requestTransferListener;
        private TransferListener responseTransferListener;

        public Builder onRequest(TransferListener.Builder requestTransferListenerBuilder) {
            this.requestTransferListener = requestTransferListenerBuilder.build();
            return this;
        }

        public Builder onResponse(TransferListener.Builder responseTransferListenerBuilder) {
            this.responseTransferListener = responseTransferListenerBuilder.build();
            return this;
        }

        public TransferListenerGroup build(long taskId) {
            return new TransferListenerGroup(taskId, requestTransferListener, responseTransferListener);
        }
    }
}
