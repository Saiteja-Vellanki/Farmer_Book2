# ---- JXL (Excel library) ----
-keep class jxl.** { *; }
-dontwarn jxl.**
-dontwarn java.awt.**
-dontwarn javax.swing.**

# ---- WorkManager workers are instantiated reflectively ----
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# ---- Keep data model classes used with JSON serialization ----
-keep class com.farmerbook.app.Labour { *; }
-keep class com.farmerbook.app.FertItem { *; }
-keep class com.farmerbook.app.FertRecord { *; }
-keep class com.farmerbook.app.FertSection { *; }
-keep class com.farmerbook.app.FertPlace { *; }
-keep class com.farmerbook.app.WorkerPlace { *; }

# ---- Security hardening: strip logs from release builds ----
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}

# ---- Obfuscation strength (protects activation logic) ----
-repackageclasses ''
-allowaccessmodification
-optimizationpasses 5

# ---- Remove source file names / line numbers from stack traces ----
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
