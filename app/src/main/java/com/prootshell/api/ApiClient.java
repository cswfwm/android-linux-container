package com.prootshell.api;

import android.content.Context;
import android.content.SharedPreferences;
import okhttp3.OkHttpClient;
import java.util.concurrent.TimeUnit;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private static final String PREFS = "config";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_BASE_URL = "base_url";
    private static final String DEFAULT_BASE_URL = "https://api.openai.com";

    private static ApiClient instance;
    private final OpenAiApi api;
    private final SharedPreferences prefs;

    private ApiClient(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(300, TimeUnit.SECONDS)
            .build();

        Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(getBaseUrl())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build();
        api = retrofit.create(OpenAiApi.class);
    }

    public static synchronized ApiClient get(Context ctx) {
        if (instance == null) instance = new ApiClient(ctx.getApplicationContext());
        return instance;
    }

    public OpenAiApi getApi() { return api; }
    public String getApiKey() { return prefs.getString(KEY_API_KEY, ""); }
    public void setApiKey(String key) { prefs.edit().putString(KEY_API_KEY, key).apply(); }
    public String getBaseUrl() {
        String url = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL);
        return url.endsWith("/") ? url : url + "/";
    }
    public void setBaseUrl(String url) { prefs.edit().putString(KEY_BASE_URL, url).apply(); }
    public String buildChatUrl() { return getBaseUrl() + "v1/chat/completions"; }
}