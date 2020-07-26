package com.tterrag.k9.util;

import com.beust.jcommander.IStringConverter;

import discord4j.common.util.Snowflake;

public class ConvertAdmins implements IStringConverter<Snowflake> {

    @Override
    public Snowflake convert(String value) {
        return Snowflake.of(value);
    }
}
