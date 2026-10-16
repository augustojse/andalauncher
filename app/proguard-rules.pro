# kotlinx.serialization: keep generated serializers for settings/backup models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class com.anda.launcher.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.anda.launcher.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Reflection target for the notification-shade fallback (SystemActions).
-keep class android.app.StatusBarManager { *; }
