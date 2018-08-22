package com.goodcoinwallet.android;

public interface Continuation<T> {
    void cont(T t);
}
