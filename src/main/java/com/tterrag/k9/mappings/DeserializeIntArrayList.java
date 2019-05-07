package com.tterrag.k9.mappings;

import java.lang.reflect.Type;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.tterrag.k9.util.annotation.Nullable;

import it.unimi.dsi.fastutil.ints.IntArrayList;

public class DeserializeIntArrayList implements JsonDeserializer<IntArrayList> {

    @Override
    public IntArrayList deserialize(@Nullable JsonElement json, @Nullable Type typeOfT, @Nullable JsonDeserializationContext context) throws JsonParseException {
        if (json != null && json.isJsonArray()) {
            IntArrayList ret = new IntArrayList();
            JsonArray versions = json.getAsJsonArray();
            versions.forEach(e -> ret.add(e.getAsInt()));
            return ret;
        }
        throw new JsonParseException("Could not parse TIntArrayList, was not array.");
    }

    public static void register(GsonBuilder builder) {
        builder.registerTypeAdapter(IntArrayList.class, new DeserializeIntArrayList());
    }

}
