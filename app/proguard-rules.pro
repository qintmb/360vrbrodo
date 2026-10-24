# proguard-rules.pro for 360VR Browser
# Keep app entry points
- keep public class com.elbruz.vrvideo.VrBrowserApp { *; }
- keep public class * extends android.app.Activity
- keep public class * extends android.app.Service

# yt-dlp / ffmpeg-kit native
- keep class io.github.junkfood02.youtubedl_android.** { *; }
- keep class com.arthenica.** { *; }

# Room
- keep class com.elbruz.vrvideo.data.local.room.** { *; }
- keep class * extends androidx.room.RoomDatabase

# Retrofit / Gson
- keepattributes Signature, InnerClasses, EnclosingMethod
- keep class retrofit2.** { *; }
- keep class okhttp3.** { *; }
- dontwarn okhttp3.**
- dontwarn okio.**
