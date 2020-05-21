package com.tterrag.k9.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.FileWriterWithEncoding;

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
		    return new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8);
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
	public Writer getWriter(String file) {
		File f = getFile(file);
		checkExists(f, true);
		return new FileWriterWithEncoding(f, StandardCharsets.UTF_8);
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
            File realFile = getFile(file);
            File backupFile = getFile(file + ".bak");
            log.error("Failed to load data from file {}, copying to backup: {}", realFile, backupFile);
            log.error("Error trace: ", e);
            try {
                FileUtils.copyFile(realFile, backupFile);
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
        String tmpFile = file + ".tmp";
        Writer fw = getWriter(tmpFile);
        try {
            fw.write(gson.toJson(toWrite, type));
        } catch (Exception e) {
            log.error("Failed to save data to file {}, partial data can be found in {}", getFile(file), getFile(tmpFile));
            log.error("Error trace: ", e);
            return;
        } finally {
            fw.flush();
            fw.close();
        }
        
        File realFile = getFile(file);
        File realTmpFile = getFile(tmpFile);
        try {
            FileUtils.copyFile(realTmpFile, realFile);
        } catch (Exception e) {
            log.error("Failed to copy tmp file from {} to {}", realTmpFile, realFile);
            log.error("Error trace: ", e);
            return;
        }
        try {
            realTmpFile.delete();
        } catch (Exception e) {
            log.error("Failed to delete temp file {}", realTmpFile);
            log.error("Error trace: ", e);
            return;
        }
    }
}
