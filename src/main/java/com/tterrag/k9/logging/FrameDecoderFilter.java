package com.tterrag.k9.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

public class FrameDecoderFilter extends Filter<ILoggingEvent> {

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (event.getMessage().contains("Decoding WebSocket Frame")) {
            return FilterReply.DENY;
        }
        return FilterReply.NEUTRAL;
    }
}
