package com.prootshell.container;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 把随 APK 内置的 proot 二进制与 rootfs.tar 解压到应用私有目录。
 */
public class ContainerInstaller {

    public static File prootFile(Context ctx) {
        return new File(ctx.getFilesDir(), "proot");
    }

    public static File rootfsDir(Context ctx) {
        return new File(ctx.getFilesDir(), "rootfs");
    }

    public static boolean isInstalled(Context ctx) {
        return prootFile(ctx).exists() && new File(rootfsDir(ctx), "bin/sh").exists();
    }

    /** 从 assets 解压 proot 二进制并赋予可执行权限。 */
    public static void installProot(Context ctx) throws IOException {
        File out = prootFile(ctx);
        if (out.exists()) return;
        File tmp = new File(ctx.getFilesDir(), "proot.tmp");
        copyAsset(ctx, "proot", tmp);
        if (!tmp.renameTo(out)) {
            tmp.delete();
            copyAsset(ctx, "proot", out);
        }
        out.setExecutable(true, true);
    }

    /** 从 assets 解压 rootfs.tar（POSIX tar，顶层为 / 内容）到 files/rootfs。 */
    public static void installRootfs(Context ctx) throws IOException {
        File root = rootfsDir(ctx);
        if (new File(root, "bin/sh").exists()) return;
        root.mkdirs();
        InputStream in = ctx.getAssets().open("rootfs.tar");
        new TarExtractor().extract(in, root);
        in.close();
    }

    private static void copyAsset(Context ctx, String name, File dest) throws IOException {
        InputStream in = ctx.getAssets().open(name);
        FileOutputStream fos = new FileOutputStream(dest);
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
        fos.close();
        in.close();
    }
}