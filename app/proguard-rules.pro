# libVLC
-keep class org.videolan.libvlc.** { *; }
# jcifs-ng / bouncycastle
-keep class jcifs.** { *; }
-dontwarn jcifs.**
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**
-dontwarn javax.**
# Gson models
-keep class com.orhan.iptv.data.xtream.** { *; }
-keepattributes Signature,*Annotation*
