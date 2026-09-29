# Room and Media3 publish their consumer rules.

# LibVLC looks up its Java API from JNI, so these classes must retain their
# original names and members in minified release builds.
-keep class org.videolan.libvlc.** { *; }
-keep interface org.videolan.libvlc.** { *; }
