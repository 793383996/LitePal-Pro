# LitePal relies on runtime reflection for model classes and annotations.

-keep class org.litepal.** { *; }

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
