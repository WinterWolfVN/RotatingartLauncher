# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK.

# Keep all public classes in the library
-keep public class com.app.ralib.** {
    public *;
}

# Keep icon extraction related classes
-keep class com.app.ralib.icon.** { *; }

# Keep UI components
-keep class com.app.ralib.ui.** { *; }
-keep class com.app.ralib.dialog.** { *; }
-keep class com.app.ralib.animation.** { *; }

