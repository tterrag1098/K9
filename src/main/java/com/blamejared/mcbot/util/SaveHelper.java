package com.blamejared.mcbot.util;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

@RequiredArgsConstructor
public class SaveHelper {
	
	private final File parentFolder;
	private final Gson gson;
	
	@SneakyThrows
	public void checkExists(File f) {
		if (!f.exists()) {
			f.createNewFile();
		}
	}

	@SneakyThrows
	public FileReader getReader(String file) {
		File f = new File(parentFolder, file);
		checkExists(f);
		return new FileReader(f);
	}
	
	@SneakyThrows
	public FileWriter getWriter(String file) {
		File f = new File(parentFolder, file);
		checkExists(f);
		return new FileWriter(f);
	}
	
	public <T> T fromJson(String file, Class<T> type) {
		return fromJson(file, TypeToken.get(type));
	}

	public <T> T fromJson(String file, TypeToken<T> type) {
		return gson.fromJson(getReader(file), type.getType());
	}
	
	public <T> void writeJson(String file, T toWrite) {
		writeJson(file, toWrite, TypeToken.get(toWrite.getClass()));
	}
	
	@SneakyThrows
	public <T> void writeJson(String file, T toWrite, TypeToken<? extends T> type) {
		FileWriter fw = getWriter(file);
		try {
			fw.write(gson.toJson(toWrite, type.getType()));
		} finally {
			fw.flush();
			fw.close();
		}
	}
}
