# Ktor
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }
-dontwarn io.ktor.**

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.powerschedule.app.**$$serializer { *; }
-keepclassmembers class com.powerschedule.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.powerschedule.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep data classes
-keep class com.powerschedule.app.data.models.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}