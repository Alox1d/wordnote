# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in D:\Android\SDK/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

#noinspection ShrinkerUnresolvedReference
-keep class com.github.** { *; }
-keep class com.google.gson.** { *; }
-dontwarn com.squareup.picasso.**
-dontwarn com.squareup.okhttp.**
-keep public class org.jsoup.** { public *; }
-keep class com.huawei.openalliance.ad.** { *; }
-keep class com.huawei.hms.ads.** { *; }
-ignorewarnings
-keepattributes *Annotation*
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable
-keep class com.hianalytics.android.**{*;}
-keep class com.huawei.updatesdk.**{*;}
-keep class com.huawei.hms.**{*;}
-keep class com.huawei.cloud.services.drive.**{*;}
-keep class com.huawei.cloud.base.json.JsonError{*;}
-keep class com.huawei.cloud.base.json.JsonErrorContainer{*;}

-keep class com.huawei.cloud.base.** {*;}
-keep class com.huawei.cloud.client.** {*;}

    -dontwarn com.huawei.**
    -keep class com.huawei.** {*;}
    -dontwarn org.slf4j.**
    -keep class org.slf4j.** {*;}
    -dontwarn org.springframework.**
    -keep class org.springframework.** {*;}
    -dontwarn com.fasterxml.jackson.**
    -keep class com.fasterxml.jackson.** {*;}

# Do not obfuscate classes with Injected Constructors
-keepclasseswithmembernames class * { @javax.inject.Inject <init>(...); }
-keepnames @toothpick.InjectConstructor class *
# Do not obfuscate classes with Injected Fields
-keepclasseswithmembernames class * { @javax.inject.Inject <fields>; }
# Do not obfuscate classes with Injected Methods
-keepclasseswithmembernames class * { @javax.inject.Inject <methods>; }
# Do not obfuscate classes with Inject delegates
-keepclasseswithmembernames class * { toothpick.ktp.delegate.* *; }
-keep class javax.inject.**
-keep class javax.annotation.**
-keep class **__Factory { *; }
-keep class **__MemberInjector { *; }
-keepclassmembers class * {
	@javax.inject.Inject <init>(...);
	@javax.inject.Inject <init>();
	@javax.inject.Inject <fields>;
	public <init>(...);
    toothpick.ktp.delegate.* *;
}
-keep class toothpick.** { *; }

-keep @javax.inject.Singleton class *
# You need to keep your custom scopes too, e.g.
# -keepnames @foo.bar.ActivityScope class *

