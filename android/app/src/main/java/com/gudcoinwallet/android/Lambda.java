package com.gudcoinwallet.android;

public interface Lambda<A, B> {
    B apply(A a);
}
