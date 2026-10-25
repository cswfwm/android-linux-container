package com.prootshell.container;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 极简 POSIX tar 解包器，支持普通文件、目录与符号链接。
 * Android 5.1 无内置 tar 库，故手写解析。不处理块设备/硬链接/稀疏等 rootfs 里没有的条目。
 */
public class TarExtractor {

    private static final int BLOCK = 512;
    private static final int MODE = 100;
    private static final int SIZE = 124;
    private static final int MTIME = 136;
    private static final int TYPE = 156;
    private static final int LINKNAME = 157;
    private static final int MAGIC = 257;
    private static final int NAME = 345;

    private final byte[] header = new byte[BLOCK];

    public void extract(InputStream in, File destRoot) throws IOException {
        while (true) {
            readFully(in, header);
            if (isAllZero(header)) {
                // 两个空块结束；读到文件尾即止
                if (in.read() == -1) break;
                continue;
            }
            String name = cString(header, NAME);
            if (name.isEmpty()) name = cString(header, 0);
            long size = octal(header, SIZE);
            char type = (char) header[TYPE];
            String linkname = cString(header, LINKNAME);
            // 反序列化路径防穿越
            File target = safeJoin(destRoot, name);
            switch (type) {
                case '5':
                    target.mkdirs();
                    break;
                case '2':
                    target.getParentFile().mkdirs();
                    // 链接目标保留 rootfs 内路径原样（如 /bin/busybox），proot 会自动重定向
                    if (!symlink(linkname, target.getAbsolutePath())) {
                        try {
                            new ProcessBuilder("ln", "-s", linkname, target.getAbsolutePath())
                                .redirectErrorStream(true).start().waitFor();
                        } catch (Exception ignored) { }
                    }
                    break;
                default:
                    target.getParentFile().mkdirs();
                    FileOutputStream fos = new FileOutputStream(target);
                    long remaining = size;
                    byte[] buf = new byte[64 * 1024];
                    while (remaining > 0) {
                        int chunk = (int) Math.min(buf.length, remaining);
                        int n = in.read(buf, 0, chunk);
                        if (n < 0) break;
                        fos.write(buf, 0, n);
                        remaining -= n;
                    }
                    fos.close();
                    break;
            }
            // 跳过头部的 padding
            long padded = (size + BLOCK - 1) / BLOCK * BLOCK;
            skip(in, padded);
        }
    }

    private void readFully(InputStream in, byte[] b) throws IOException {
        int off = 0;
        while (off < b.length) {
            int n = in.read(b, off, b.length - off);
            if (n < 0) throw new IOException("unexpected EOF");
            off += n;
        }
    }

    private void skip(InputStream in, long n) throws IOException {
        long left = n;
        byte[] buf = new byte[8192];
        while (left > 0) {
            int chunk = (int) Math.min(buf.length, left);
            int r = in.read(buf, 0, chunk);
            if (r < 0) break;
            left -= r;
        }
    }

    private boolean isAllZero(byte[] b) {
        for (byte x : b) if (x != 0) return false;
        return true;
    }

    private static boolean symlink(String oldpath, String newpath) {
        try {
            Class<?> libcore = Class.forName("libcore.io.Libcore");
            java.lang.reflect.Field osField = libcore.getField("os");
            Object os = osField.get(null);
            os.getClass().getMethod("symlink", String.class, String.class)
                .invoke(os, oldpath, newpath);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String cString(byte[] b, int off) {
        int end = off;
        while (end < b.length && b[end] != 0) end++;
        return new String(b, off, end - off);
    }

    private static long octal(byte[] b, int off) {
        long v = 0;
        for (int i = off; i < b.length && b[i] != 0 && b[i] != ' '; i++) {
            v = v * 8 + (b[i] - '0');
        }
        return v;
    }

    private static File safeJoin(File root, String name) {
        File f = new File(root, name);
        String rp = root.getAbsolutePath();
        String p = f.getAbsolutePath();
        if (!p.startsWith(rp)) throw new IllegalArgumentException("unsafe path: " + name);
        return f;
    }
}