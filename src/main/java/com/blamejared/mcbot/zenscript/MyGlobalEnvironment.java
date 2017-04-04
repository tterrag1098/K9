package com.blamejared.mcbot.zenscript;

import stanhebben.zenscript.*;
import stanhebben.zenscript.compiler.*;
import stanhebben.zenscript.expression.partial.IPartialExpression;
import stanhebben.zenscript.symbols.IZenSymbol;
import stanhebben.zenscript.type.ZenType;
import stanhebben.zenscript.util.ZenPosition;

import java.lang.reflect.Type;
import java.util.*;

import static com.blamejared.mcbot.zenscript.ZenScript.*;


public class MyGlobalEnvironment implements IEnvironmentGlobal {
		private final Map<String, byte[]> classes;
		private final Map<String, IZenSymbol> symbols;
		private final ClassNameGenerator generator;

		public MyGlobalEnvironment(Map<String, byte[]> classes) {
			this.classes = classes;
			symbols = new HashMap<>();
			generator = new ClassNameGenerator();
		}

		@Override
		public IZenCompileEnvironment getEnvironment() {
			return environment;
		}

		@Override
		public TypeExpansion getExpansion(String name) {
			return expansions.get(name);
		}

		@Override
		public String makeClassName() {
			return generator.generate();
		}

		@Override
		public boolean containsClass(String name) {
			return classes.containsKey(name);
		}

		@Override
		public void putClass(String name, byte[] data) {
			classes.put(name, data);
		}

		@Override
		public IPartialExpression getValue(String name, ZenPosition position) {
			if (symbols.containsKey(name)) {
				return symbols.get(name).instance(position);
			} else if (globals.containsKey(name)) {
				return globals.get(name).instance(position);
			} else {
				IZenSymbol pkg = root.get(name);
				if (pkg == null) {
					return null;
				} else {
					return pkg.instance(position);
				}
			}
		}

		@Override
		public void putValue(String name, IZenSymbol value, ZenPosition position) {
			if (symbols.containsKey(name)) {
				error(position, "Value already defined in this scope: " + name);
			} else {
				symbols.put(name, value);
			}
		}

		@Override
		public ZenType getType(Type type) {
			return types.getType(type);
		}

		@Override
		public void error(ZenPosition position, String message) {
			System.out.println("ERROR: "+ position.toString() + "> " + message);
		}

		@Override
		public void warning(ZenPosition position, String message) {
			System.out.println("WARNING: "+ position.toString() + "> " + message);
		}

		@Override
		public Set<String> getClassNames() {
			return classes.keySet();
		}

		@Override
		public byte[] getClass(String name) {
			return classes.get(name);
		}
	}
