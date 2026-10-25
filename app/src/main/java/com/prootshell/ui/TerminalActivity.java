package com.prootshell.ui;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.prootshell.R;
import com.prootshell.ai.AiAgent;
import com.prootshell.container.ContainerService;

/**
 * 仿 Termux 的终端界面：黑底绿色等宽字体滚动输出 + 底部输入栏。
 */
public class TerminalActivity extends Activity {

    private TextView term;
    private ScrollView scroll;
    private EditText input;
    private AiAgent agent;
    private final StringBuilder buffer = new StringBuilder();

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            String action = intent.getAction();
            String data = intent.getStringExtra("data");
            if ("com.prootshell.output".equals(action)) {
                append(data, false);
            } else if ("com.prootshell.exit".equals(action)) {
                append("\n[container exited: " + data + "]\n", true);
            }
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(buildUi());
        ContainerService.startShellIfNeeded(this);

        agent = new AiAgent(this);
        agent.setCallback(new AiAgent.Callback() {
            @Override public void onAiMessage(String t) { runOnUiThread(() -> append(t + "\n", false)); }
            @Override public void onError(String e) { runOnUiThread(() -> append("[AI] " + e + "\n", true)); }
        });

        input.setOnEditorActionListener((v, id, ev) -> {
            if (id == EditorInfo.IME_ACTION_SEND || id == EditorInfo.IME_ACTION_DONE || id == EditorInfo.IME_NULL) {
                String line = input.getText().toString().trim();
                if (!line.isEmpty()) {
                    if (line.startsWith("/ai ")) {
                        agent.ask(line.substring(4));
                    } else {
                        ContainerService.sendCommand(line);
                    }
                    append("$ " + line + "\n", false);
                }
                input.setText("");
                return true;
            }
            return false;
        });
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        scroll = new ScrollView(this);
        term = new TextView(this);
        term.setTextColor(Color.rgb(0x9e, 0xff, 0x9e));
        term.setTypeface(Typeface.MONOSPACE);
        term.setTextSize(13);
        term.setPadding(16, 16, 16, 80);
        scroll.addView(term);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        input = new EditText(this);
        input.setBackgroundColor(Color.rgb(0x11, 0x11, 0x11));
        input.setTextColor(Color.rgb(0xe0, 0xe0, 0xe0));
        input.setSingleLine(true);
        input.setHint("输入命令，或用 /ai 提问让 AI 操控容器");
        input.setHintTextColor(Color.rgb(0x66, 0x66, 0x66));
        input.setTypeface(Typeface.MONOSPACE);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        root.addView(input, new FrameLayout.LayoutParams(-1, 64, android.view.Gravity.BOTTOM));

        return root;
    }

    private void append(String text, boolean isErr) {
        buffer.append(text);
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        if (isErr) {
            sb.setSpan(new ForegroundColorSpan(Color.rgb(0xff, 0x66, 0x66)), 0, text.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        }
        term.append(sb);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter();
        f.addAction("com.prootshell.output");
        f.addAction("com.prootshell.exit");
        registerReceiver(receiver, f);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(receiver); } catch (Exception ignored) { }
    }
}