package com.tterrag.k9.mappings;

import java.lang.reflect.Type;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.tterrag.k9.util.Nullable;

import gnu.trove.list.array.TIntArrayList;

public class DeserializeTIntArrayList implements JsonDeserializer<TIntArrayList> {

    @Override
    public TIntArrayList deserialize(@Nullable JsonElement json, @Nullable Type typeOfT, @Nullable JsonDeserializationContext context) throws JsonParseException {
        if (json != null && json.isJsonArray()) {
            TIntArrayList ret = new TIntArrayList();
            JsonArray versions = json.getAsJsonArray();
            versions.forEach(e -> ret.add(e.getAsInt()));
            return ret;
        }
        throw new JsonParseException("Could not parse TIntArrayList, was not array.");
    }

    public static void register(GsonBuilder builder) {
        builder.registerTypeAdapter(TIntArrayList.class, new DeserializeTIntArrayList());
    }

}
