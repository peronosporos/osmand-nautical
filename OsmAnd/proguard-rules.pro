# Signal K Serialization Models
-keepclassmembers class net.osmand.plus.plugins.nautical.network.** { *; }
-keep class net.osmand.plus.plugins.nautical.network.** { *; }

# Retrofit Interfaces
-keep interface net.osmand.plus.plugins.nautical.network.SignalKRestService { *; }
-keepclassmembers interface net.osmand.plus.plugins.nautical.network.SignalKRestService { *; }

# Custom Views & Map Layers (Reflection/Inflation)
-keep class net.osmand.plus.plugins.nautical.ui.editor.PolarCurveCanvasView { *; }
-keep class net.osmand.plus.plugins.nautical.map.layers.SailingLaylinesMapLayer { *; }
-keep class net.osmand.plus.plugins.nautical.map.layers.WeatherRoutingMapLayer { *; }

# OkHttp & Retrofit generic keep rules
-keepattributes Signature, InnerClasses, AnnotationDefault
-keep @retrofit2.http.* interface * { *; }
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# GSON
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class sun.misc.Unsafe { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.android.HandlerContext$ScheduledPatch {
    public void run();
}
