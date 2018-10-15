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

# This seems to be depended on by some Android internal stuff, not any Rover stuff.
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
# And various libraries that apparently aren't shipping a Proguard rules file to merge in:
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn com.uber.autodispose.**
-dontwarn okio.**
