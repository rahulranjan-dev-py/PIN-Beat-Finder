# Shrink and optimise but keep class/method names: crash reports are read by people, not a
# mapping server, and the app carries no secrets that renaming would protect.
-dontobfuscate

# --- kotlinx.serialization -------------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.pinbeatfinder.**$$serializer { *; }
-keepclassmembers class com.pinbeatfinder.** { *** Companion; }
-keepclasseswithmembers class com.pinbeatfinder.** { kotlinx.serialization.KSerializer serializer(...); }

# --- Retrofit / OkHttp -----------------------------------------------------
-keepattributes Signature, Exceptions
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-dontwarn okhttp3.internal.platform.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# --- FastExcel / Aalto / commons-compress ----------------------------------
-keep class com.fasterxml.aalto.** { *; }
-keep class org.codehaus.stax2.** { *; }
-keep class javax.xml.stream.** { *; }
-keep class org.dhatim.fastexcel.** { *; }
-dontwarn org.apache.commons.compress.**
-dontwarn org.brotli.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.tukaani.xz.**
-dontwarn org.osgi.**
-dontwarn javax.xml.stream.**
