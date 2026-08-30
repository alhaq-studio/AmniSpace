# ProGuard / R8 Rules for AmniQuest

# Keep line numbers and source files for readable crash reports
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes InnerClasses,EnclosingMethod

# Kotlinx Serialization
-keepattributes *Annotation*,Signature
-dontwarn kotlinx.serialization.**
-keepclassmembers class * {
    companion object *;
}
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,allowobfuscation,allowshrinking class * {
    <init>(...);
}
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep @kotlinx.serialization.Serializable class * { *; }

# AmniQuest Domain & Data Models
-keep class com.alhaq.amniquest.data.** { *; }
-keep class com.alhaq.amniquest.backed.repositories.** { *; }
-keep class com.alhaq.amniquest.core.utils.** { *; }

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Hilt & Dagger
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper
-keep class * extends androidx.hilt.work.HiltWorker
-keepclasseswithmembernames class * {
    @javax.inject.Inject <init>(...);
}

# WorkManager
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# Coil
-dontwarn coil.**
-keep class coil.** { *; }

# Biometrics & Jetpack Compose
-dontwarn androidx.biometric.**