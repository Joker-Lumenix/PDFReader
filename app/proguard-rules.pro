# ---------------------------------------------------------------------------
# 日志剥离
# Logger 在 release 下本身就是空实现，这里再兜底一次，确保任何直接调用
# android.util.Log 的第三方或遗漏代码在打包后不残留任何日志字符串。
# ---------------------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# ---------------------------------------------------------------------------
# 自定义 View 需要保留构造函数，供 XML 布局 inflate 与 XML 预览使用
# ---------------------------------------------------------------------------
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ---------------------------------------------------------------------------
# 反射：JSON 解析与跨模块模型
# ---------------------------------------------------------------------------
-keepattributes Signature
-keepattributes *Annotation*
