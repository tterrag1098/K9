package com.blamejared.mcbot.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

public class StatusChangeFilter extends Filter<ILoggingEvent> {

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (event.getMessage().contains("changed presence") && event.getLoggerName().equals("sx.blah.discord.Discord4J")) {
            return FilterReply.DENY;
        }
        return FilterReply.NEUTRAL;
    }
}
