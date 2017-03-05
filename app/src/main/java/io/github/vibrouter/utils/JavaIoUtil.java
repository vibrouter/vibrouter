package io.github.vibrouter.utils;

import java.io.Closeable;
import java.io.IOException;

public class JavaIoUtil {
    public static boolean close(Closeable closeable) {
        if (closeable == null) {
            return true;
        }
        try {
            closeable.close();
            return true;
        } catch (IOException failedClosing) {
            return false;
        }
    }
}
