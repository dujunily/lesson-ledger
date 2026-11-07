package cn.yiweiqiu.lessonledger.di

import android.content.Context
import androidx.room.Room
import cn.yiweiqiu.lessonledger.data.local.AppDatabase
import cn.yiweiqiu.lessonledger.data.local.dao.ChildDao
import cn.yiweiqiu.lessonledger.data.local.dao.CourseDao
import cn.yiweiqiu.lessonledger.data.local.dao.LessonDao
import cn.yiweiqiu.lessonledger.data.local.dao.PaymentDao
import cn.yiweiqiu.lessonledger.data.local.dao.SyncLogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "lesson_ledger.db")
            // 不用 destructiveMigration：账本是钱，升级时宁可报错也不能悄悄清库。
            // 缺 migration 会直接抛异常，比静默删库安全（数据在坚果云还有一份）。
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
            )
            .build()

    @Provides
    fun provideChildDao(db: AppDatabase): ChildDao = db.childDao()

    @Provides
    fun provideCourseDao(db: AppDatabase): CourseDao = db.courseDao()

    @Provides
    fun providePaymentDao(db: AppDatabase): PaymentDao = db.paymentDao()

    @Provides
    fun provideLessonDao(db: AppDatabase): LessonDao = db.lessonDao()

    @Provides
    fun provideSyncLogDao(db: AppDatabase): SyncLogDao = db.syncLogDao()
}
