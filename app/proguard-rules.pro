# Keep Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class studio.freestyle.labs.danjiangsunseeker.**$$serializer { *; }
-keepclassmembers class studio.freestyle.labs.danjiangsunseeker.** {
    *** Companion;
}
-keepclasseswithmembers class studio.freestyle.labs.danjiangsunseeker.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Retrofit / OkHttp
-keepattributes Signature
-keepattributes Exceptions
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**

# commons-suncalc has no required keep rules; keep public APIs for safety
-keep class org.shredzone.commons.suncalc.** { *; }

# MapLibre
-keep class org.maplibre.android.** { *; }

# ARCore
-keep class com.google.ar.** { *; }
