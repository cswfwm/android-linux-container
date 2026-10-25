package com.prootshell.ai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.prootshell.api.ApiClient;
import com.prootshell.api.model.ChatModels;
import com.prootshell.container.ContainerInstaller;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * AI 代理：向 OpenAI 兼容 API 发送请求，把模型给出的 shell 指令在容器内捕获式执行，
 * 并将命令输出回喂给模型，形成「AI 下指令 -> 容器执行 -> 看结果 -> 再决策」的闭环。
 */
public class AiAgent {

    public interface Callback {
        void onAiMessage(String text);
        void onError(String err);
    }

    private final Context ctx;
    private final List<ChatModels.Message> history = new ArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean awaiting;
    private Callback cb;
    private int round;
    private static final int MAX_ROUND = 5;

    private static final String SYSTEM_PROMPT =
        "你是一个运行在 Android 上的轻量级 Linux 容器（Alpine Linux）的远程操作员。\n" +
        "用户会给你自然语言指令，你要转化为要在容器 shell 中执行的命令。\n" +
        "规则：\n" +
        "  1) 需要执行命令时，输出且只输出一行：CMD:<shell 命令>\n" +
        "  2) 否则输出给用户的纯文本说明。\n" +
        "  3) 当你需要查看容器状态来决定下一步时，先输出 CMD: 命令。\n" +
        "  4) 系统会把该命令在容器里的 stdout/stderr 输出作为下一条 user 消息回喂给你。\n" +
        "  5) 当前系统是 Alpine Linux，包管理器是 apk，可 apk add 安装软件。\n" +
        "  6) 完成用户请求后，用中文给出结论。";

    public AiAgent(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        history.add(new ChatModels.Message("system", SYSTEM_PROMPT));
    }

    public void setCallback(Callback cb) { this.cb = cb; }

    /** 用户发言入口（主线程调用）。 */
    public void ask(String userText) {
        if (awaiting) return;
        awaiting = true;
        round = 0;
        history.add(new ChatModels.Message("user", userText));
        requestChat();
    }

    private void requestChat() {
        ApiClient client = ApiClient.get(ctx);
        ChatModels.ChatRequest req = new ChatModels.ChatRequest();
        req.messages = history;

        client.getApi().chatCompletions(
            client.buildChatUrl(),
            "Bearer " + client.getApiKey(),
            req
        ).enqueue(new Callback<ChatModels.ChatResponse>() {
            @Override
            public void onResponse(Call<ChatModels.ChatResponse> call, Response<ChatModels.ChatResponse> r) {
                if (!r.isSuccessful() || r.body() == null || r.body().choices.isEmpty()) {
                    main.post(() -> { onError("API error: " + r.code()); awaiting = false; });
                    return;
                }
                String content = r.body().choices.get(0).message.content;
                history.add(new ChatModels.Message("assistant", content));
                handleReply(content);
            }

            @Override
            public void onFailure(Call<ChatModels.ChatResponse> call, Throwable t) {
                main.post(() -> { onError("网络错误: " + t.getMessage()); awaiting = false; });
            }
        });
    }

    private void handleReply(String content) {
        if (content.startsWith("CMD:")) {
            round++;
            if (round > MAX_ROUND) {
                main.post(() -> { onAiMessage("（已达到最大执行轮数，停止自动执行）\n"); awaiting = false; });
                return;
            }
            String cmd = content.substring(4).trim();
            main.post(() -> onAiMessage("$ " + cmd + "\n"));
            // 后台捕获式执行容器命令
            worker.execute(() -> {
                String out = execInContainer(cmd);
                // 把输出作为 user 消息回喂，驱动模型继续
                history.add(new ChatModels.Message("user",
                    "[命令已执行，容器输出如下，请据此回复用户或继续下达指令]\n" +
                    "命令: " + cmd + "\n输出:\n" + out));
                awaiting = false;
                main.post(this::requestChat);
            });
        } else {
            main.post(() -> { onAiMessage(content + "\n"); awaiting = false; });
        }
    }

    /**
     * 在容器内执行单条命令并捕获完整 stdout/stderr。
     * 用 proot -R rootfs /bin/sh -c "<cmd>" 一次性执行，干净拿到输出。
     */
    private String execInContainer(String cmd) {
        StringBuilder sb = new StringBuilder();
        try {
            File proot = ContainerInstaller.prootFile(ctx);
            File root = ContainerInstaller.rootfsDir(ctx);
            ProcessBuilder pb = new ProcessBuilder(
                proot.getAbsolutePath(),
                "-R", root.getAbsolutePath(),
                "/bin/sh", "-c", cmd
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) != -1) sb.append(buf, 0, n);
            p.waitFor();
        } catch (Exception e) {
            sb.append("[执行错误] ").append(e.getMessage());
        }
        if (sb.length() > 4000) sb.setLength(4000); // 截断过长输出
        return sb.toString();
    }

    private void onError(String err) { if (cb != null) cb.onError(err); }
    private void onAiMessage(String t) { if (cb != null) cb.onAiMessage(t); }
}