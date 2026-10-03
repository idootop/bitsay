# bitsay — keep rules
# Nothing reflective in this app: models are plain data classes, backup uses org.json.
# R8 full mode handles everything. The rules below only silence known-noise warnings.

-dontwarn org.jetbrains.annotations.**

# Keep AppWidgetProvider / RemoteViewsService subclasses: they are instantiated by the OS
# from the manifest by name.
-keep class com.del.bitsay.widget.** { public <init>(...); }
-keep class * extends android.appwidget.AppWidgetProvider { public <init>(...); }
-keep class * extends android.widget.RemoteViewsService { public <init>(...); }
-keep class * extends android.content.BroadcastReceiver { public <init>(...); }
