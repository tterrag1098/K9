package com.tterrag.k9.trick;

import java.util.HashMap;
import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@FunctionalInterface
public interface TrickType {
    
    String getId();
    
    default String getHighlighter() {
        return "";
    }
    
    Map<String, TrickType> byId = new HashMap<>();
    
    @Getter
    @EqualsAndHashCode
    class SimpleType implements TrickType {
        
        private final String id, name;
        
        public SimpleType(String id, String name) {
            this.id = id;
            this.name = name;
            byId.put(this.id, this);
        }
        
        @Override
        public String toString() {
            return name;
        }
    }
    
    @Getter
    @EqualsAndHashCode(callSuper = true)
    class HighlightedType extends SimpleType {
        
        private final String highlighter;
        
        public HighlightedType(String id, String name, String highlighter) {
            super(id, name);
            this.highlighter = highlighter;
        }
        
        public HighlightedType(String id, String name) {
            this(id, name, name);
        }
    }
    
    TrickType STRING = new SimpleType("str", "String");
    TrickType CLOJURE = new HighlightedType("clj", "Clojure", "clojure");
}
