package cn.yiweiqiu.lessonledger.core

/**
 * 同步冲突副本的统一标记。
 *
 * 同步引擎把打架的两条记录都保留时，输的那条会加上「[冲突副本]」前缀（名字和备注）。
 * 之前这个前缀只写在 SyncEngine 的 private companion 里，统计、课程列表想排除副本
 * 只能各自手写字符串字面量，容易散落不一致 —— 收拢到这里一处定义。
 */
object ConflictMark {
    const val PREFIX = "[冲突副本] "

    /** 这条记录是不是同步时生成的冲突副本（副本在用户处理前不参与任何统计） */
    fun isCopy(note: String): Boolean = note.startsWith(PREFIX.trim())
}

/**
 * 「一次性结清课时」的标记。
 *
 * 换手机、补录历史账时最常见的情况是：一门两三年前的课，课时早就上完了，
 * 但具体哪天上了哪一节根本查不到 —— 逐节铺日期纯属编造，日历里还会冒出一堆假记录。
 * 这种就用一条「核销」记录把剩余课时一次性扣掉：它是课时消耗，但不是一次真实上课，
 * 所以统计里算节数、不算次数，也不进打卡日历和"今天"。
 */
object LedgerMark {
    const val SETTLE = "[核销] "

    fun isSettle(note: String?): Boolean = note != null && note.startsWith(SETTLE.trim())

    /** 核销记录不挂在具体某天：时间戳归零，日历和"今天"都当它不存在 */
    const val SETTLE_AT = 0L
}
