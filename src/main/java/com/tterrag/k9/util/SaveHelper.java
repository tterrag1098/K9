package com.tterrag.k9.util;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;

import org.apache.commons.io.FileUtils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tterrag.k9.util.annotation.Nullable;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
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
		return exists;
	}
	
	private File getFile(String file) {
	    return new File(parentFolder, file);
	}

	@SneakyThrows
	public Reader getReader(String file) {
		File f = getFile(file);
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
		File f = getFile(file);
		checkExists(f, true);
		return new FileWriter(f);
	}
	
	public T fromJson(String file, Class<T> type) {
		return fromJson(file, TypeToken.get(type));
    }

    public T fromJson(String file, TypeToken<T> type) {
        try {
            T ret = gson.fromJson(getReader(file), type.getType());
            if (ret == null) {
                ret = defaultValue;
            }
            return ret;
        } catch (Exception e) {
            log.error("Failed to load data from file {}, copying to backup: {}", file, file + ".bak");
            log.error("Error trace: ", e);
            try {
                FileUtils.copyFile(getFile(file), getFile(file + ".bak"));
                return defaultValue;
            } catch (IOException e1) {
                log.error("Exception copying file, data loss could occur!", e1);
                throw new RuntimeException(e1);
            }
        }
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
