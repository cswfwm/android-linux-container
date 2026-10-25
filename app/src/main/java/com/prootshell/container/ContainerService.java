package com.prootshell.container;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import com.prootshell.R;
import com.prootshell.ui.TerminalActivity;

/**
 * 前台服务：持有全局容器 shell，保证进程存活，供终端与 AI 代理读写。
 */
public class ContainerService extends Service implements ContainerShell.ShellListener {

    private static final String CHANNEL_ID = "container";
    private static ContainerShell shell;
    private static Service instance;

    public static ContainerShell getShell() { return shell; }
    public static boolean isRunning() { return shell != null && shell.isRunning(); }

    /** 若容器未启动则启动前台服务。 */
    public static void startShellIfNeeded(Context ctx) {
        if (!isRunning()) {
            ctx.startService(new Intent(ctx, ContainerService.class));
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        startForegroundCompat();
        if (shell == null) {
            shell = new ContainerShell();
            try {
                if (!ContainerInstaller.isInstalled(this)) {
                    ContainerInstaller.installProot(this);
                    ContainerInstaller.installRootfs(this);
                }
                shell.start(this, this);
            } catch (Exception e) {
                stopSelf();
            }
        }
    }

    private void startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Container", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(ch);
        }
        Intent i = new Intent(this, TerminalActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, 0);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        b.setContentTitle("ProotShell")
         .setContentText("Linux container running")
         .setSmallIcon(R.drawable.ic_notif)
         .setContentIntent(pi);
        startForeground(1, b.build());
    }

    @Override
    public void onOutput(String text) {
        broadcast("output", text);
    }

    @Override
    public void onExit(int code) {
        broadcast("exit", String.valueOf(code));
        shell = null;
    }

    private void broadcast(String action, String data) {
        Intent i = new Intent("com.prootshell." + action);
        i.putExtra("data", data);
        sendBroadcast(i);
    }

    public static void sendCommand(String cmd) {
        if (shell != null) {
            try { shell.sendCommand(cmd); } catch (Exception ignored) { }
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        if (shell != null) { shell.stop(); shell = null; }
        instance = null;
        super.onDestroy();
    }
}