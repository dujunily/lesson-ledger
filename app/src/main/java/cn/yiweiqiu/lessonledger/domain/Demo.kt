package cn.yiweiqiu.lessonledger.domain

import java.util.UUID

/**
 * 演示数据（示例账本）。
 *
 * 它没有独立的库、也没有一个「demoMode」开关字段：整套示例记录的 id 都以 [PREFIX] 开头，
 * 「这条记录属于演示那一套」就等于「id 是不是这个前缀」。
 *
 * 这么做的好处是不用改表结构、不用迁移，也不会因为用户把「钢琴一对一」改名成
 * 自己孩子真实的课就认不出来 —— 名字会变，id 不会。
 */
object DemoIds {
    const val PREFIX = "seed-"

    const val CHILD = "${PREFIX}child-1"
    const val COURSE_PIANO = "${PREFIX}course-piano"
    const val COURSE_SWIM = "${PREFIX}course-swim"

    fun isDemo(id: String?): Boolean = id != null && id.startsWith(PREFIX)
}

/**
 * 数据源：账本里并存的两套数据，同一时刻只把其中一套呈现给用户。
 *
 * 以前的实现把两者做成互斥的：账本非空就不许载入演示，想记真账又必须把演示删掉。
 * 结果是「试用」和「真用」不能共存 —— 试用完要么删掉自己的试录，要么带着假账继续记。
 * 现在两套数据都留在库里，[DataSource] 只是一个「看哪一套」的开关：
 * 切过去不动任何数据，切回来自己的记录原样都在。
 */
enum class DataSource(val key: String, val label: String) {
    MINE("mine", "我的数据"),
    DEMO("demo", "演示数据");

    val isDemo: Boolean get() = this == DEMO

    /** 在这一套里新建记录时用的 id：演示套带 `seed-` 前缀，天然归到演示那一边 */
    fun newId(): String =
        if (isDemo) DemoIds.PREFIX + UUID.randomUUID().toString() else UUID.randomUUID().toString()

    companion object {
        fun of(key: String?): DataSource = entries.firstOrNull { it.key == key } ?: MINE
    }
}

/**
 * 取出属于 [source] 的那一套记录。
 *
 * 归属只看 id 前缀和孩子归属，不看名字：示例课被改名、示例孩子被改昵称，都还认得出。
 * 课程要跟着它挂在哪个孩子下面走 —— 用户在示例孩子名下自己加了一门课，
 * 那门课属于演示这套，切回「我的数据」时不该出现。
 */
fun LedgerData.forSource(source: DataSource): LedgerData {
    val demo = source.isDemo
    val scopedChildren = children.filter { DemoIds.isDemo(it.id) == demo }
    val childIds = scopedChildren.map { it.id }.toSet()
    val scopedCourses = courses.filter {
        it.childId in childIds && (demo || !DemoIds.isDemo(it.id))
    }
    val courseIds = scopedCourses.map { it.id }.toSet()
    return LedgerData(
        children = scopedChildren,
        courses = scopedCourses,
        payments = payments.filter { it.courseId in courseIds },
        lessons = lessons.filter { it.courseId in courseIds },
    )
}

/**
 * 一套数据的规模。
 *
 * 设置里拿它显示「我的数据 3 个孩子 · 4 门课 / 演示数据 1 个孩子 · 2 门课」，
 * 清理确认框里也拿它列清单 —— 用户点「清空」之前得先看清会删掉什么。
 */
data class DemoStats(
    val children: Int = 0,
    val courses: Int = 0,
    val payments: Int = 0,
    val lessons: Int = 0,
) {
    val present: Boolean get() = children > 0 || courses > 0

    val summary: String
        get() = "$children 个孩子 · $courses 门课 · $payments 笔缴费 · $lessons 条上课记录"

    /** 副标题用的一行字：「3 个孩子 · 4 门课 · 30 条上课记录」 */
    val brief: String
        get() = if (!present) {
            "还没有记录"
        } else {
            buildString {
                append("$children 个孩子 · $courses 门课")
                if (payments > 0) append(" · $payments 笔缴费")
                if (lessons > 0) append(" · $lessons 条上课记录")
            }
        }
}

/** 账本这一套的规模（不含软件商店意义上的「演示」，纯粹是计数） */
fun LedgerData.statsOf(): DemoStats = DemoStats(
    children = children.size,
    courses = courses.size,
    payments = payments.size,
    lessons = lessons.size,
)
