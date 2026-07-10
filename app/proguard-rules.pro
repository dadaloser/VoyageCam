# Keep the initial release simple. Add shrinking rules when recording modules are introduced.
-dontwarn com.tencent.bugly.**
-keep public class com.tencent.bugly.**{*;}