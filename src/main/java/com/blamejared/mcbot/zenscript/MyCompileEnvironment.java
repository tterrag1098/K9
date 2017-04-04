package com.blamejared.mcbot.zenscript;

import stanhebben.zenscript.*;
import stanhebben.zenscript.compiler.*;
import stanhebben.zenscript.parser.Token;
import stanhebben.zenscript.symbols.IZenSymbol;

import java.util.List;

import static com.blamejared.mcbot.zenscript.ZenScript.*;


public class MyCompileEnvironment implements IZenCompileEnvironment {
	
	@Override
	public IZenErrorLogger getErrorLogger() {
		
		return errors;
	}
	
	@Override
	public IZenSymbol getGlobal(String name) {
		if(globals.containsKey(name)) {
			return globals.get(name);
		} else {
			return root.get(name);
		}
	}
	
	@Override
	public IZenSymbol getDollar(String name) {
		return null;
	}
	
	@Override
	public IZenSymbol getBracketed(IEnvironmentGlobal environment, List<Token> tokens) {
		return null;
	}
	
	@Override
	public TypeRegistry getTypeRegistry() {
		return types;
	}
	
	@Override
	public TypeExpansion getExpansion(String type) {
		return expansions.get(type);
	}
}
