package com.blamejared.mcbot.util;

import sx.blah.discord.util.RequestBuffer.IVoidRequest;

import com.blamejared.mcbot.MCBot;

import sx.blah.discord.util.RequestBuilder;

public class RequestHelper {

    public static void requestOrdered(IVoidRequest...requests) {
        if (requests.length == 0) {
            return;
        }
        RequestBuilder builder = new RequestBuilder(MCBot.instance).shouldBufferRequests(true);
        builder.doAction(() -> {
            requests[0].doRequest();
            return true;
        });
        if (requests.length > 1) {
            for (int i = 1; i < requests.length; i++) {
                final int idx = i;
                builder.andThen(() -> {
                    requests[idx].doRequest();
                    return true;
                });
            }
        }
        builder.build();
    }
}
