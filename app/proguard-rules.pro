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

# ---------------------------------------------------------------------------
# 第三方库里用不到的分支
#
# Apache POI 在 android 上没有 java.awt 和 javax.imageio，PDFBox 的 JPX
# 过滤器需要 jcenter 上的 jp2-android，log4j 的注解和 osgi 也是用不到的。
# 这些类只会让 R8 报错，打包时不会用到它们。
# ---------------------------------------------------------------------------
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn com.gemalto.jp2.**
-dontwarn aQute.bnd.annotation.**
-dontwarn edu.umd.cs.findbugs.annotations.**
-dontwarn org.osgi.framework.**

# ---------------------------------------------------------------------------
# PDFBox
#
# AAR 自带的 consumer rules 已经保留了 SecurityHandler，这里再保留文档结构
# 的入口，PDFBox 用反射读它们。
# ---------------------------------------------------------------------------
-keep class com.tom_roush.pdfbox.pdmodel.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-dontwarn com.tom_roush.**
