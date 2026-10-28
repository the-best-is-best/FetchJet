# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# ---- FetchJet keep rules (R8 is enabled for release) ----
# Gson reflection on the yt-dlp models (YTDLPUtil uses TypeToken + @SerializedName)
-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses
-keep class com.deniscerri.ytdl.database.models.** { *; }
-keep class com.deniscerri.ytdl.core.packages.** { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.google.gson.reflect.TypeToken { *; }
-dontwarn com.google.gson.**

# youtubedl-android / ffmpeg / aria2c wrappers (JNI + reflection)
-keep class com.yausername.** { *; }
-keep class io.github.junkfood02.** { *; }
-dontwarn com.yausername.**

# Ktor / OkHttp / Okio / coroutines
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.slf4j.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# Our own serialized models (settings/queue are parsed by hand, keep them anyway)
-keep class com.michelle.raouf.fetch_jet.domain.model.** { *; }

# libtorrent4j (JNI)
-keep class org.libtorrent4j.swig.libtorrent_jni { *; }
-keep class org.libtorrent4j.** { *; }
-dontwarn org.libtorrent4j.**
