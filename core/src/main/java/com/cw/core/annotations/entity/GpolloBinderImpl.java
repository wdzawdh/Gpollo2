package com.cw.core.annotations.entity;

import com.cw.core.annotations.contrace.GpolloBinder;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;


/**
 * @author cw
 * @date 2017/12/20
 */
public class GpolloBinderImpl implements GpolloBinder {

    private CompositeDisposable mCompositeDisposable = new CompositeDisposable();

    @Override
    public void add(Disposable disposable) {
        mCompositeDisposable.add(disposable);
    }

    @Override
    public void unbind() {
        if (!mCompositeDisposable.isDisposed()) {
            mCompositeDisposable.clear();
        }
    }

    @Override
    public boolean isUnbind() {
        return mCompositeDisposable.isDisposed();
    }
}
