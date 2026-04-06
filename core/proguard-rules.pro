# LitePal requires generated metadata and loads registry implementations via ServiceLoader.

-keep class org.litepal.** { *; }

-keep interface org.litepal.generated.LitePalGeneratedRegistry
-keep class * implements org.litepal.generated.LitePalGeneratedRegistry { *; }
-keepnames class * implements org.litepal.generated.LitePalGeneratedRegistry

-keep class * extends org.litepal.crud.LitePalSupport {
    <fields>;
    <methods>;
}

-keepattributes Signature,*Annotation*

-keep @org.litepal.annotation.Column class * { *; }
-keep @org.litepal.annotation.Encrypt class * { *; }

-keepclassmembers class * {
    @org.litepal.annotation.Column <fields>;
    @org.litepal.annotation.Encrypt <fields>;
}
