# kotlinx.serialization: keep the generated serializers of the API models (the library's own rules cover the rest).
-keepclassmembers @kotlinx.serialization.Serializable class com.boimin.beatradar.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.boimin.beatradar.**$$serializer { *; }
-keepclasseswithmembers class com.boimin.beatradar.** {
    kotlinx.serialization.KSerializer serializer(...);
}
