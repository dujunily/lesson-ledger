package cn.yiweiqiu.lessonledger.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import cn.yiweiqiu.lessonledger.data.local.dao.ChildDao
import cn.yiweiqiu.lessonledger.data.local.dao.CourseDao
import cn.yiweiqiu.lessonledger.data.local.dao.LessonDao
import cn.yiweiqiu.lessonledger.data.local.dao.PaymentDao
import cn.yiweiqiu.lessonledger.data.local.dao.SyncLogDao
import cn.yiweiqiu.lessonledger.data.local.entity.ChildEntity
import cn.yiweiqiu.lessonledger.data.local.entity.CourseEntity
import cn.yiweiqiu.lessonledger.data.local.entity.LessonEntity
import cn.yiweiqiu.lessonledger.data.local.entity.PaymentEntity
import cn.yiweiqiu.lessonledger.data.local.entity.SyncLogEntity

@Database(
    entities = [
        ChildEntity::class,
        CourseEntity::class,
        PaymentEntity::class,
        LessonEntity::class,
        SyncLogEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun childDao(): ChildDao
    abstract fun courseDao(): CourseDao
    abstract fun paymentDao(): PaymentDao
    abstract fun lessonDao(): LessonDao
    abstract fun syncLogDao(): SyncLogDao

    companion object {
        /**
         * v1 → v2：课程新增「上课前提醒分钟数」。
         * 必须写显式 Migration，不能用 destructiveMigration —— 老婆那台手机上已有账本数据，
         * 升级时把库删掉等于把人家的账清了。
         */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE courses ADD COLUMN remindBeforeMinutes INTEGER NOT NULL DEFAULT 30"
                )
            }
        }

        /**
         * v2 → v3：课次新增「已手动删除」标记。
         * 用户删掉某一节自动课次后，自动排课要记得别再把这一天补回来。
         */
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE lessons ADD COLUMN suppressed INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * v3 → v4：课程新增「上一期课程 id」。
         * 续费开新一期时指向被续的那条，形成期次链；老数据没有链，一律为 null。
         */
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE courses ADD COLUMN previousCourseId TEXT"
                )
            }
        }
        /**
         * v4 → v5：缴费支持「定金 + 尾款」。
         * 老数据全部视为一次付清（FULL），没有组、没有抵扣，金额与课时一笔都不动。
         */
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE payments ADD COLUMN kind TEXT NOT NULL DEFAULT 'FULL'")
                db.execSQL("ALTER TABLE payments ADD COLUMN groupKey TEXT")
                db.execSQL("ALTER TABLE payments ADD COLUMN creditCents INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v5 → v6：课程支持「每个星期几各自的上课时间」。
         * 新字段为 null 时一切照旧，统一用 startTime，老数据一行都不用改。
         */
        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE courses ADD COLUMN startTimesJson TEXT")
            }
        }

        /**
         * v6 → v7：课程支持「每个星期几各自的课时长」。
         * 同样只加列，为 null 时沿用统一的 durationMinutes，老数据不受影响。
         */
        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE courses ADD COLUMN durationMinutesJson TEXT")
            }
        }
    }
}
