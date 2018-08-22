package com.goodcoinwallet.android;

public interface Lambda<A, B> {
    B apply(A a);
}
