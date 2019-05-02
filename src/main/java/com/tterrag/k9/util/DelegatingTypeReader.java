package com.tterrag.k9.util;

import java.io.IOException;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DelegatingTypeReader<R> implements TypeAdapterFactory {
    
    private final Class<? extends R> rawClass;

    @Override
    public final <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (!rawClass.isAssignableFrom(type.getRawType())) {
            return null;
        }
        final TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
        return new TypeAdapter<T>() {

            public void write(JsonWriter out, T value) throws IOException {
                delegate.write(out, value);
            }

            @SuppressWarnings("unchecked")
            public T read(JsonReader in) throws IOException {
                return (T) handleDelegate(readDelegate((TypeAdapter<R>) delegate, in));
            }
        };
    }
    
    protected R readDelegate(TypeAdapter<R> delegate, JsonReader in) throws IOException {
        return delegate.read(in);
    }

    protected R handleDelegate(R delegate) {
        return delegate;
    }
}
