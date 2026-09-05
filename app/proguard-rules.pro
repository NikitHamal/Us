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
# Enums are serialized by name (Emotion, Horseman, RiskLevel...). R8 can otherwise strip
# values()/valueOf(), which breaks decoding of cloud provider responses at runtime.
-keepclassmembers enum com.us.copilot.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Room ---
-keep class androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# --- SQLCipher ---
-keep class net.sqlcipher.** { *; }
-keep interface net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# --- Tink (pulled in by androidx.security:security-crypto) ---
# Tink is compiled against Error Prone annotations that are compile-time only and are
# deliberately not shipped at runtime. R8 sees the dangling references and fails the build,
# so tell it these are expected to be absent.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
# Tink's KeysDownloader fetches remote keysets over google-api-client + joda-time. We never
# call it (we only use local AES keysets via EncryptedSharedPreferences), and neither library
# is a dependency, so these references are unreachable dead code.
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**
-keep class com.google.crypto.tink.** { *; }
# Explicitly drop the remote-keyset downloader so R8 never has to resolve its deps.
-dontwarn com.google.crypto.tink.util.KeysDownloader**
-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite {
    <fields>;
}

# --- Ktor / OkHttp ---
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-keep class io.ktor.client.engine.okhttp.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# --- Hilt ---
-dontwarn dagger.hilt.**

# --- WorkManager ---
# Workers are constructed reflectively by name, so they must survive shrinking untouched.
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
-keep class com.us.copilot.work.** { *; }

# --- App models used with reflection-free serialization still need names in stacktraces ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Compose ---
-dontwarn androidx.compose.**
