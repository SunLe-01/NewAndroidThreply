# Threply ProGuard Rules

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.arche.threply.data.** { *; }

# Google Play Billing
-keep class com.android.vending.billing.**
