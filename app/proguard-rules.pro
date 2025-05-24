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

-keep class com.roxgps.ui.viewmodel.MainViewModel{*;}
-keepnames class com.roxgps.ui.viewmodel.MainViewModel.**

-keep class com.roxgps.xposed.Xshare{*;}
-keep class com.roxgps.xposed.HookEntry{*;}
-keep class de.robv.android.xposed.**{*;}
-keepnames class de.robv.android.xposed.**

#-repackageclasses
#-allowaccessmodification
#-overloadaggressively

# Keep Base Activity dan turunannya
-keep class com.roxgps.ui.BaseMapActivity { *; }
-keepnames class com.roxgps.ui.BaseMapActivity
-keep class * extends com.roxgps.ui.BaseMapActivity { *; }

# Keep semua MapActivity untuk setiap flavor
-keep class com.roxgps.**.MapActivity { *; }
-keepnames class com.roxgps.**.MapActivity
-keep class * extends com.roxgps.**.MapActivity { *; }

# Keep Application class
-keep class com.roxgps.App { *; }
-keepnames class com.roxgps.App
-keep public class * extends android.app.Application

# Keep semua class dalam package ui
-keep class com.roxgps.ui.** { *; }
-keepnames class com.roxgps.ui.**

# Keep constructors
-keepclasseswithmembers class * {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep lifecycle methods
-keepclassmembers class * extends android.app.Activity {
    public void *(android.view.View);
    public void onCreate(android.os.Bundle);
}

# Keep inheritance
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Specific untuk multiple flavors
-keep class **.BuildConfig { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Kurangi agresifitas
-dontoptimize
-dontpreverify

# Tambahkan untuk mempertahankan type casting
-keepattributes Signature
-keepattributes *Annotation*
