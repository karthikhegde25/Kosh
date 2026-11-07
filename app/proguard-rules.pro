# ---------------------------------------------------------------------------------------------
# Kosh release rules (R8). The default optimize rules from AGP are applied first; these add to them.
# Libraries (Compose, Room, DataStore, coroutines, Navigation) ship their own consumer rules.
# ---------------------------------------------------------------------------------------------

# Smaller stack traces without losing line numbers. The mapping file in
# app/build/outputs/mapping/release/mapping.txt de-obfuscates crash reports; keep it for each release.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# More aggressive packaging of the remaining classes.
-repackageclasses ''
-allowaccessmodification

# Room creates NotesDatabase_Impl by class name. (Room's own rules cover this too; kept explicit on purpose.)
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Remove verbose logging calls from release builds. (Warnings and errors are kept.)
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Kotlin metadata is only needed by reflection; Kosh uses none.
-dontwarn kotlin.reflect.jvm.internal.**
-keep class com.kosh.android.tools.EditBridge { *; }

# PdfBox-Android: optional pieces it can use if present (JPEG 2000 decoding, extra crypto providers) are not bundled.
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-dontwarn javax.xml.stream.**
# PdfBox creates its encryption handlers by reflection, so they must not be renamed or removed.
-keep class com.tom_roush.pdfbox.pdmodel.encryption.** { *; }
-keepattributes Signature,*Annotation*

# The PDF viewer page calls these methods by name through addJavascriptInterface("JWI").
-keepattributes JavascriptInterface
-keep class com.kosh.android.viewer.WebInterface { *; }
