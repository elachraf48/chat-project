# ─────────────────────────────────────────────────────────────
# JavaChat Mobile – ProGuard / R8 rules
# ─────────────────────────────────────────────────────────────

# Keep Jackson model classes (protocol/Message, protocol/MessageType)
# so R8 does not strip the getters/setters Jackson needs at runtime.
-keep class com.javachat.mobile.protocol.** { *; }

# Keep enum values (MessageType)
-keepclassmembers enum com.javachat.mobile.protocol.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Jackson annotations
-keep @com.fasterxml.jackson.annotation.** class * { *; }
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.* *;
}

# Keep Jackson core (ObjectMapper)
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**

# Keep AppCompat and Material
-keep class androidx.appcompat.** { *; }
-keep class com.google.android.material.** { *; }

# Keep Activities and Fragments
-keep public class * extends android.app.Activity
-keep public class * extends androidx.fragment.app.Fragment

# Suppress warnings for optional dependencies
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
