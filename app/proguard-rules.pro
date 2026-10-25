# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# Gson 模型
-keep class com.prootshell.api.model.** { *; }

# 容器与 AI 类
-keep class com.prootshell.container.** { *; }
-keep class com.prootshell.ai.** { *; }