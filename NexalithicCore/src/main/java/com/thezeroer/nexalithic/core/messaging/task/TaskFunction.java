package com.thezeroer.nexalithic.core.messaging.task;

import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;

/**
 * 业务任务函数接口，定义了一个异步请求-响应周期的完整生命周期。
 * <p>
 * 该接口作为 Nexalithic 框架中“请求意图”的抽象，封装了从数据包构造到结果异步回执的所有行为。
 * 框架底层通过 SequenceID 自动关联请求与响应，用户无需手动维护会话状态。
 * </p>
 * <b>生命周期流向：</b>
 * <pre>
 * [提交任务] -> request() -> (网络传输) -> [等待响应/超时/异常]
 * |--> 正常响应: response(BusinessPacket) -> finish()
 * |--> 触发超时: timeout() -> finish()
 * |--> 运行异常: exception(Exception) -> finish()
 * |--> 主动取消: cancel() -> finish()
 * </pre>
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/21
 * @version 1.0.0
 */
public interface TaskFunction {

    /**
     * 构造并返回请求数据包。
     *
     * @return 待发送的业务数据包，为空则取消后续流程，即取消发送直接进入{@link #finish()}
     */
    BusinessPacket request();

    /**
     * 处理收到的业务响应。
     *
     * @param response 远程端返回的业务响应包
     */
    void response(BusinessPacket response);

    /**
     * 任务执行超时回调。
     * <p>
     * 在预设的时间内未收到响应，或网络层判定请求丢失时触发。
     * 触发后将不再接收该任务的响应包，随后会调用 {@link #finish()}。
     */
    default void timeout() {}

    /**
     * 任务被主动取消回调。
     */
    default void cancel() {}

    /**
     * 任务终态清理回调。
     * <p>
     * 无论任务成功（response）、超时（timeout）、取消（cancel）还是异常（exception），
     * 最终都会调用此方法进行资源释放。
     */
    default void finish() {}

    /**
     * 任务执行期间捕获的异常处理。
     *
     * @param e 捕获的异常实例
     */
    default void exception(Exception e) {}
}