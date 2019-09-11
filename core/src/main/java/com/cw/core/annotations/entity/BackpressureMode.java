package com.cw.core.annotations.entity;

/**
 * @author cw
 * @date 2018/3/23
 */

public enum BackpressureMode {
    /**
     * 事件做缓存
     */
    BUFFER,

    /**
     * 事件抛弃掉
     */
    DROP,

    /**
     * 发出的元素的速度比订阅者消化得要快,接收最新发出的事件处理
     */
    LATEST,

    /**
     * 默认
     */
    NORMAL
}
