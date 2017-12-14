package com.fsck.k9.mail.e3;

import android.content.Context;
import android.util.Log;

import com.fsck.k9.mail.util.FileFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created on 12/8/2016.
 *
 * @author koh
 */

public class E3Utils {
    private static final String E3_TEMP_DIR = "e3-temp";
    private final Context context;

    public E3Utils(final Context context) {
        this.context = context;
    }

    public FileFactory getFileFactory() {
        return new E3TempFileFactory(this);
    }

    public File getTempFile(final String prefix, final String suffix) throws IOException {
        return File.createTempFile(prefix, suffix, getFilesDir());
    }

    public File getFileStreamPath(final String path) {
        return context.getFileStreamPath(path);
    }

    public FileOutputStream openFileOutput(final String filename, final int mode) throws
            FileNotFoundException {
        return context.openFileOutput(filename, mode);
    }

    public File getFilesDir() {
        final File directory = new File(context.getFilesDir(), E3_TEMP_DIR);

        if (!directory.exists() && !directory.mkdir()) {
            Log.e(E3Constants.LOG_TAG, "Error creating directory: " + directory.getAbsolutePath());
        }

        return directory;
    }

    private static class E3TempFileFactory implements FileFactory {
        private final E3Utils e3Utils;

        E3TempFileFactory(final E3Utils e3Utils) {
            this.e3Utils = e3Utils;
        }

        @Override
        public File createFile() throws IOException {
            final File e3TempDirectory = e3Utils.getFilesDir();
            return File.createTempFile("e3-file-factory-", null, e3TempDirectory);
        }
    }

    public static long logDuration(final String tag, final String message, final long start) {
        final long end = System.currentTimeMillis();
        final long duration = end - start;
        Log.d(tag, message + ": " + duration);
        return duration;
    }
}
