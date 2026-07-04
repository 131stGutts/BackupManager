# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\firem\AppData\Local\Google\AndroidStudio2026.1.1\sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.kts.

# SMBJ Rules
-keep class com.hierynomus.** { *; }
-keep interface com.hierynomus.** { *; }
-dontwarn com.hierynomus.**
-keep class net.schmizz.** { *; }
-keep interface net.schmizz.** { *; }
-dontwarn net.schmizz.**
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Fix for MBassy (used by SMBJ or others) missing javax.el
-dontwarn net.engio.mbassy.**
-dontwarn javax.el.**

# Apache Commons Net Rules
-keep class org.apache.commons.net.** { *; }
-dontwarn org.apache.commons.net.**

# JSch Rules
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

# GSON Rules
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.ayer.backupmanager.data.** { *; }

# Room Rules
-keep class androidx.room.paging.** { *; }
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
