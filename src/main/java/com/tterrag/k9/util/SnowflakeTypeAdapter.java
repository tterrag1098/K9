package com.tterrag.k9.util;

import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import discord4j.common.util.Snowflake;

public class SnowflakeTypeAdapter implements JsonSerializer<Snowflake>, JsonDeserializer<Snowflake> {

    @Override
    public JsonElement serialize(Snowflake src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(src.asLong());
    }

    @Override
    public Snowflake deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        return Snowflake.of(json.getAsLong());
    }
}
