package com.prootshell.api;

import com.prootshell.api.model.ChatModels;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Url;

public interface OpenAiApi {
    @POST
    Call<ChatModels.ChatResponse> chatCompletions(
        @Url String url,
        @Header("Authorization") String auth,
        @Body ChatModels.ChatRequest body
    );
}