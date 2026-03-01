package com.thezeroer.nexalithic.core.io.buffer;

import java.io.IOException;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

/**
 * 环形缓冲区业务视图。
 * <p>本接口为 {@code TLoopBuffer} 的受限视图，仅暴露数据读写操作。
 * 框架层负责管理缓冲区的生命周期（申请、重置、回收），业务层开发者**无需也不应**关心内存的释放。</p>
 * <h3>安全性约束</h3>
 * <ul>
 * <li><b>生命周期限制：</b>本对象仅在当前回调（Handler）方法内有效。请勿将此引用跨线程持久化或异步持有，
 * 否则可能读取到已被框架重置或分配给其他连接的脏数据。</li>
 * <li><b>线程安全性：</b>本视图非线程安全。通常由框架在 Reactor 线程中单线程调用。</li>
 * <li><b>回绕处理：</b>视图自动处理环形缓冲区的物理断层（Scattering/Gathering IO），用户可视为连续内存操作。</li>
 * </ul>
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/10
 */
public interface LoopBufferView {

    /**
     * 从通道读取数据并填充至缓冲区。
     * @param channel 数据源通道
     * @return 实际读取的字节数
     * @throws IOException 如果发生网络或文件 IO 错误
     */
    int readFromChannel(ScatteringByteChannel channel) throws IOException;

    /**
     * 将缓冲区数据写入通道。
     * @param channel 目标通道
     * @return 实际写入的字节数
     * @throws IOException 如果发生网络或文件 IO 错误
     */
    int writeToChannel(GatheringByteChannel channel) throws IOException;

    LoopBufferView put(byte value);
    LoopBufferView put(short value);
    LoopBufferView put(int value);
    LoopBufferView put(long value);
    LoopBufferView put(float value);
    LoopBufferView put(double value);
    LoopBufferView put(byte[] value);

    byte getByte();
    short getShort();
    int getInt();
    long getLong();
    float getFloat();
    double getDouble();
    /**
     * 读取指定长度的字节并返回新数组。
     * @param length 长度
     * @return 包含数据的字节数组
     */
    byte[] getBytes(int length);
    /**
     * 将数据读入目标数组，直到填满目标数组。
     * @param dst 目标接收数组
     */
    void getBytes(byte[] dst);

    /**
     * 缓冲区是否为空。
     */
    boolean isEmpty();
    /**
     * 缓冲区是否已满。
     */
    boolean isFull();
    /**
     * 获取当前可读取的字节数。
     */
    int readableBytes();
    /**
     * 获取当前可写入的空闲字节数。
     */
    int writableBytes();
}
