package com.cw.core.annotations.contrace;


import io.reactivex.disposables.Disposable;

/**
 * @author cw
 * @date 2017/12/20
 */
public interface GpolloBinder {

    void add(Disposable disposable);

    void unbind();

    boolean isUnbind();
}
