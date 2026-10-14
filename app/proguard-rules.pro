# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.talksy.app.data.remote.dto.** { *; }
-keep,includedescriptorclasses class com.talksy.app.**$$serializer { *; }
-keepclassmembers class com.talksy.app.** { *** Companion; }
# Socket.IO
-dontwarn io.socket.**
-keep class io.socket.** { *; }
