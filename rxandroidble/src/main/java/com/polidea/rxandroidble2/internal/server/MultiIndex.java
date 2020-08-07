package com.polidea.rxandroidble2.internal.server;

import androidx.annotation.NonNull;

import java.util.Map;

public interface MultiIndex<T, V, U> extends Map<T, U> {
    U getMulti(V requestid);
    U putMulti(@NonNull V integer, @NonNull U payload);
}
