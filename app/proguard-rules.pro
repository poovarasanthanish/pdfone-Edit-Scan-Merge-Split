# Project specific ProGuard rules

# Optimization
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification

# Keep line numbers for debugging
-keepattributes SourceFile,LineNumberTable

# PDFBox-Android
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.bouncycastle.**

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Firebase
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep class androidx.room.RoomDatabase {
    static <fields>;
}
-keep class * extends androidx.room.Entity
-keep class * implements androidx.room.Dao

# AndroidX Lifecycle & ViewModel
-keep class * extends androidx.lifecycle.ViewModel
-keep class * extends androidx.lifecycle.AndroidViewModel

# Android Image Cropper
-keep class com.canhub.cropper.** { *; }

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}
-dontwarn kotlinx.coroutines.**

# Play Services (Ads, Billing, App Update)
-keep class com.google.android.gms.ads.** { *; }
-keep class com.android.billingclient.** { *; }
-keep class com.google.android.play.core.** { *; }

# AndroidX PDF
-keep class androidx.pdf.** { *; }
-dontwarn androidx.pdf.**

# CameraX
-keep class androidx.camera.core.** { *; }
-keep class androidx.camera.camera2.** { *; }
-keep class androidx.camera.lifecycle.** { *; }
-keep class androidx.camera.view.** { *; }

# Backdrop
-keep class io.github.kyant0.backdrop.** { *; }

# Google Generative AI (Gemini)
-keep class com.google.ai.client.generativeai.** { *; }
-dontwarn com.google.ai.client.generativeai.**

# ZXing (QR Codes)
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# OkHttp
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod,InnerClasses
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep data classes/models if they are used for serialization (JSON, Firebase, etc.)
# You should annotate your models with @Keep or add them here.
-keepclassmembers class com.villagefast.pdfeditor.data.db.** { *; }
-keep class com.villagefast.pdfeditor.data.models.** { *; }
-keep @androidx.annotation.Keep class *
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# JNI / Native code
-keepclasseswithmembernames class * {
    native <methods>;
}
