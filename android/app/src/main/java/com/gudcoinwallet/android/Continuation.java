package com.gudcoinwallet.android;

public interface Continuation<T> {
    void cont(T t);
}
