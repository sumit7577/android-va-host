package com.lody.virtual.helper.utils;

import android.content.Context;

import com.lody.virtual.client.core.VirtualCore;

import java.io.File;
import java.io.FileWriter;

/**
 * PORT AID. MIUI silently drops this app's logcat, so engine-side diagnostics
 * go to a file under the host's filesDir, one per process, readable with
 * `adb exec-out run-as <pkg> cat files/port-<proc>.log`.
 */
public class PortLog {

    private static volatile File sFile;
    private static volatile boolean sTried;

    public static void w(String tag, String msg) {
        VLog.i(tag, msg);
        try {
            File f = file();
            if (f == null) return;
            try (FileWriter fw = new FileWriter(f, true)) {
                fw.write(tag + ": " + msg + "\n");
            }
        } catch (Throwable ignored) {
        }
    }

    private static File file() {
        if (sTried) return sFile;
        synchronized (PortLog.class) {
            if (sTried) return sFile;
            sTried = true;
            try {
                Context c = VirtualCore.get().getContext();
                String proc = VirtualCore.get().getProcessName();
                String suffix = proc == null || !proc.contains(":")
                        ? "main" : proc.substring(proc.lastIndexOf(':') + 1);
                sFile = new File(c.getFilesDir(), "port-" + suffix + ".log");
            } catch (Throwable t) {
                sFile = null;
            }
            return sFile;
        }
    }
}
