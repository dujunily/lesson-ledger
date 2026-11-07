# ============ 课时簿 Release 混淆规则 ============
# 依赖库里大多自带 consumer rules（Room / Hilt / WorkManager / OkHttp / kotlinx-serialization），
# 这里补的是「本项目自己的代码」在 R8 下会被误伤的部分。

# ---- kotlinx.serialization ----
# @Serializable 类的 Companion.serializer() 是反射拿到的，必须留住
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class cn.yiweiqiu.lessonledger.data.**$$serializer { *; }
-keep class cn.yiweiqiu.lessonledger.data.sync.** { *; }
-keep class cn.yiweiqiu.lessonledger.data.local.entity.** { *; }

# ---- Room ----
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# ---- Hilt / Dagger ----
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper
-keepclasseswithmembers class * {
    @dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper <init>(...);
}
-keep class javax.inject.** { *; }
# HiltWorker：WorkManager 反射实例化
-keep class * extends androidx.work.ListenableWorker {
    <init>(...);
}
-keep class * extends android.content.ContextWrapper { *; }

# ---- WorkManager ----
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker { *; }
-keepnames class androidx.work.impl.model.WorkSpec

# ---- OkHttp ----
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okio.**

# ---- Tink（备份加密）----
# Tink 是 compileOnly 引用了 errorprone 注解，运行时不需要，别让 R8 报缺类
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# ---- 枚举：R8 会把 enum 常量内联，WebDAV 冲突副本比对依赖枚举名 ----
-keepclassmembers enum cn.yiweiqiu.lessonledger.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---- Kotlin 协程 ----
-dontwarn kotlinx.coroutines.**

# ---- 保留崩溃栈可读性：不要混淆行号，Release 也带完整调试信息 ----
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
