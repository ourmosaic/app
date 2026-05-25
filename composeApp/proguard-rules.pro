# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.

# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# Kotlinx Serialization
-keepattributes *Annotation*, EnclosingMethod, InnerClasses, Signature
-keepclassmembers class ** {
    @kotlinx.serialization.SerialName <fields>;
}
-keepclassmembers class * extends kotlinx.serialization.internal.GeneratedSerializer {
    <init>(...);
}
-keepclassmembers class * {
    *** Companion;
    *** $serializer;
}

# Ktor
-keep class io.ktor.** { *; }
-dontwarn java.lang.management.**
-dontwarn kotlinx.coroutines.debug.DebugProbesKt

# Compose Multiplatform / Jetpack Compose
-keep class androidx.compose.runtime.snapshots.** { *; }
-keep class androidx.compose.runtime.saveable.** { *; }
-dontwarn androidx.compose.runtime.ParcelableSnapshotState**
-keep class androidx.compose.runtime.external.kotlinx.collections.immutable.implementations.immutableMap.PersistentHashMap { *; }
-keep class androidx.compose.runtime.external.kotlinx.collections.immutable.implementations.immutableList.PersistentVector { *; }

# OkHttp (used in androidMain)
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Your model classes
-keep @kotlinx.serialization.Serializable class space.ourmosaic.app.** { *; }
-keepclassmembers class space.ourmosaic.app.** {
    *** Companion;
    *** $serializer;
}
