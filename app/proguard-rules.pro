# ProGuard/R8
-keepattributes *Annotation*

# PDFBox Android
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**
-dontwarn org.apache.**
