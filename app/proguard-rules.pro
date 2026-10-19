# Garmin Coach ProGuard Rules

# Keep Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep WebView JS interface methods
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
