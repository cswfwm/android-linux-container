package com.prootshell.container;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 一个用 PRoot 启动的交互式 shell 会话。向 stdin 写命令、从 stdout/stderr 读输出。
 */
public class ContainerShell {
    private Process process;
    private OutputStream stdin;
    private InputStream stdout;
    private InputStream stderr;
    private final ExecutorService ioPool = Executors.newFixedThreadPool(2);
    private volatile ShellListener listener;

    public interface ShellListener {
        void onOutput(String text);
        void onExit(int code);
    }

    /** 启动 proot -R <rootfs> /bin/sh */
    public void start(Context ctx, ShellListener l) throws IOException {
        this.listener = l;
        File proot = ContainerInstaller.prootFile(ctx);
        File root = ContainerInstaller.rootfsDir(ctx);

        ProcessBuilder pb = new ProcessBuilder(
            proot.getAbsolutePath(),
            "-R", root.getAbsolutePath(),
            "/bin/sh"
        );
        pb.redirectErrorStream(false);
        process = pb.start();
        stdin = process.getOutputStream();
        stdout = process.getInputStream();
        stderr = process.getErrorStream();

        ioPool.execute(() -> pump(stdout));
        ioPool.execute(() -> pump(stderr));
        ioPool.execute(() -> {
            try {
                int code = process.waitFor();
                if (listener != null) listener.onExit(code);
            } catch (InterruptedException ignored) { }
        });
    }

    private void pump(InputStream in) {
        BufferedReader r = new BufferedReader(new InputStreamReader(in));
        StringBuilder sb = new StringBuilder();
        try {
            int c;
            while ((c = r.read()) != -1) {
                sb.append((char) c);
                if (c == '\n') {
                    if (listener != null) listener.onOutput(sb.toString());
                    sb.setLength(0);
                }
            }
            if (sb.length() > 0 && listener != null) listener.onOutput(sb.toString());
        } catch (IOException ignored) { }
    }

    /** 向 shell 写入一行命令。 */
    public void sendCommand(String cmd) throws IOException {
        if (stdin == null) return;
        stdin.write((cmd + "\n").getBytes());
        stdin.flush();
    }

    public void stop() {
        if (process != null) {
            process.destroy();
            process = null;
        }
    }

    public boolean isRunning() {
        return process != null && process.isAlive();
    }
}