package com.blamejared.mcbot.commands.casino.util.chips;

import java.util.concurrent.TimeUnit;

public class Wallet {
    
    private long chips;
    private long lastPayday;
    
    public long chips() {
        return chips;
    }
    
    public boolean remove(int chips) {
        if (chips > this.chips) {
            return false;
        }
        this.chips -= chips;
        return true;
    }

    public void add(int chips) {
        this.chips += chips;
    }

    public boolean payday() {
        if (System.currentTimeMillis() - lastPayday > TimeUnit.DAYS.toMillis(1)) {
            add(500);
            this.lastPayday = System.currentTimeMillis();
            return true;
        }
        return false;
    }
}
