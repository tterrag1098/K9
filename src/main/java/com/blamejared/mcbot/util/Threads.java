package com.blamejared.mcbot.util;

import lombok.SneakyThrows;

public class Threads {

    @SneakyThrows
    public static void sleep(long ms) {
        Thread.sleep(ms);
    }
}
