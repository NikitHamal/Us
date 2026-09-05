# --- Kotlin / Coroutines ---
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# --- kotlinx.serialization ---
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations
-dontnote kotlinx.serialization.**
-keepclasseswithmembers class **$$serializer { *** INSTANCE; }
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers,allowshrinking class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.us.copilot.**$$serializer { *; }
-keepclassmembers class com.us.copilot.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Room ---
-keep class androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# --- SQLCipher ---
-keep class net.sqlcipher.** { *; }
-keep interface net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# --- Ktor / OkHttp ---
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-keep class io.ktor.client.engine.okhttp.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# --- Hilt ---
-dontwarn dagger.hilt.**

# --- App models used with reflection-free serialization still need names in stacktraces ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Compose ---
-dontwarn androidx.compose.**
