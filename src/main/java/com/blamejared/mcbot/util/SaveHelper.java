package com.blamejared.mcbot.util;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
public class SaveHelper<T> {
	
	private final File parentFolder;
	private final Gson gson;
	private final @Nullable T defaultValue;
	
	@SneakyThrows
	public boolean checkExists(File f, boolean create) {
	    boolean exists = f.exists();
		if (!exists && create) {
		    f.getParentFile().mkdirs();
			f.createNewFile();
			return true;
		}
		return false;
	}

	@SneakyThrows
	public Reader getReader(String file) {
		File f = new File(parentFolder, file);
		if (checkExists(f, false)) {
		    return new FileReader(f);
		} else {
		    return new Reader() {
                
                @Override
                public int read(char[] cbuf, int off, int len) throws IOException {
                    return -1;
                }
                
                @Override
                public void close() throws IOException {}
            };
		}
	}
	
	@SneakyThrows
	public FileWriter getWriter(String file) {
		File f = new File(parentFolder, file);
		checkExists(f, true);
		return new FileWriter(f);
	}
	
	public T fromJson(String file, Class<T> type) {
		return fromJson(file, TypeToken.get(type));
	}

	public T fromJson(String file, TypeToken<T> type) {
		T ret = gson.fromJson(getReader(file), type.getType());
		if (ret == null) {
		    ret = defaultValue;
		}
		return ret;
	}
	
	public void writeJson(String file, T toWrite) {
		writeJson(file, toWrite, TypeToken.get(toWrite.getClass()).getType());
	}
	
    public void writeJson(String file, T toWrite, TypeToken<? extends T> type) {
        writeJson(file, toWrite, type.getType());
    }

    @SneakyThrows
    private void writeJson(String file, T toWrite, Type type) {
        FileWriter fw = getWriter(file);
        try {
            fw.write(gson.toJson(toWrite, type));
        } finally {
            fw.flush();
            fw.close();
        }
    }
}
