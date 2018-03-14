package com.tterrag.k9.util;

import lombok.SneakyThrows;

public class Threads {

    @SneakyThrows
    public static void sleep(long ms) {
        Thread.sleep(ms);
    }
}
