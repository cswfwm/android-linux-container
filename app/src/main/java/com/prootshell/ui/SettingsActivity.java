package com.prootshell.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.prootshell.R;
import com.prootshell.api.ApiClient;
import com.prootshell.container.ContainerService;

/**
 * 设置页：OpenAI 兼容 API 的 key 与 baseUrl，以及 Alpine 软件源切换。
 */
public class SettingsActivity extends Activity {

    private static final String[][] MIRRORS = {
        {"官方 dl-cdn.alpinelinux.org", "https://dl-cdn.alpinelinux.org/alpine"},
        {"华为云 mirrors.huaweicloud.com", "https://mirrors.huaweicloud.com/alpine"},
        {"阿里云 mirrors.aliyun.com", "https://mirrors.aliyun.com/alpine"},
        {"清华 TUNA mirrors.tuna.tsinghua.edu.cn", "https://mirrors.tuna.tsinghua.edu.cn/alpine"},
        {"浙大 mirrors.zju.edu.cn", "https://mirrors.zju.edu.cn/alpine"},
    };

    private EditText etKey, etUrl;
    private Spinner mirrorSpinner;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 48, 32, 32);

        ApiClient client = ApiClient.get(this);

        root.addView(label("OpenAI 兼容 API 密钥"));
        etKey = new EditText(this);
        etKey.setHint("sk-...");
        etKey.setText(client.getApiKey());
        root.addView(etKey, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, -2));

        root.addView(label("Base URL（中转站/自定义）"));
        etUrl = new EditText(this);
        etUrl.setHint("https://api.openai.com");
        etUrl.setText(client.getBaseUrl().replaceFirst("/$", ""));
        root.addView(etUrl, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, -2));

        root.addView(label("Alpine 软件源（老系统镜像）"));
        mirrorSpinner = new Spinner(this);
        String[] names = new String[MIRRORS.length];
        for (int i = 0; i < MIRRORS.length; i++) names[i] = MIRRORS[i][0];
        mirrorSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names));
        root.addView(mirrorSpinner, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, -2));

        Button btnSave = new Button(this);
        btnSave.setText("保存并写入容器源");
        btnSave.setOnClickListener(v -> save());
        root.addView(btnSave, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, -2));

        setContentView(root);
    }

    private void save() {
        ApiClient client = ApiClient.get(this);
        client.setApiKey(etKey.getText().toString().trim());
        client.setBaseUrl(etUrl.getText().toString().trim());

        int idx = mirrorSpinner.getSelectedItemPosition();
        if (idx >= 0) {
            String base = MIRRORS[idx][1];
            String version = "v3.12";
            String cmd = "echo \"" + base + "/" + version + "/main\" > /etc/apk/repositories && " +
                         "echo \"" + base + "/" + version + "/community\" >> /etc/apk/repositories && " +
                         "apk update";
            ContainerService.sendCommand(cmd);
        }
        Toast.makeText(this, "已保存，软件源已写入容器", Toast.LENGTH_SHORT).show();
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        return t;
    }
}