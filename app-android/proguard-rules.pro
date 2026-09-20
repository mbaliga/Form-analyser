# Crocodyl keeps the release shrinker enabled. Room, Compose, CameraX, MediaPipe and Tink publish
# their own consumer rules; only app-owned reflective/data-bound surfaces need explicit retention.
# Keep shrinking and obfuscation enabled, but avoid the optimizer's pathological compile time on the
# MediaPipe/Compose graph. This remains a release build (unused code/resources are removed and names
# are obfuscated); re-enable optimization as a separately measured maintenance change when the R8
#/AGP stack is upgraded.
-dontoptimize

# Kotlin runtime types are resolved by AndroidX test's component-factory bridge when the minified
# release APK is exercised under instrumentation. Keep the runtime contract explicit; debug builds
# masked this dependency because the unminified APK retained the full stdlib transitively.
-keep class kotlin.** { *; }

# Room instantiates the database implementation generated from AppDatabase.
-keep class xyz.mdhv.formanalyser.app.data.AppDatabase_Impl { *; }

# Room entities are serialized into the local export and inspected by generated adapters.
-keep class xyz.mdhv.formanalyser.app.data.** { *; }

# MediaPipe discovers native inference implementations through its runtime library.
-keep class com.google.mediapipe.** { *; }

# MediaPipe graph/configuration protobufs use reflection at runtime. Preserve generated field names
# and descriptors through release obfuscation (the minified capture path depends on them).
-keep class com.google.protobuf.** { *; }

# MediaPipe's optional graph-profiler and generated-protobuf surfaces reference classes that are
# intentionally absent from the runtime artifact. The app does not use the profiler or annotation
# processor APIs; suppress only the exact optional references reported by R8.
-dontwarn com.google.mediapipe.proto.CalculatorProfileProto$CalculatorProfile
-dontwarn com.google.mediapipe.proto.GraphTemplateProto$CalculatorGraphTemplate
-dontwarn com.google.protobuf.Internal$ProtoMethodMayReturnNull
-dontwarn com.google.protobuf.Internal$ProtoNonnullApi
-dontwarn com.google.protobuf.ProtoField
-dontwarn com.google.protobuf.ProtoPresenceBits
-dontwarn com.google.protobuf.ProtoPresenceCheckedField
-dontwarn javax.annotation.processing.AbstractProcessor
-dontwarn javax.annotation.processing.SupportedAnnotationTypes
-dontwarn javax.lang.model.SourceVersion
-dontwarn javax.lang.model.element.Element
-dontwarn javax.lang.model.element.ElementKind
-dontwarn javax.lang.model.element.Modifier
-dontwarn javax.lang.model.type.TypeMirror
-dontwarn javax.lang.model.type.TypeVisitor
-dontwarn javax.lang.model.util.SimpleTypeVisitor8
