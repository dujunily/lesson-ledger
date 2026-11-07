package cn.yiweiqiu.lessonledger.ui.forms

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import cn.yiweiqiu.lessonledger.core.Dates
import java.time.LocalDate

/**
 * 表单草稿：只在内存里留一份，进程被杀就丢，不写库也不写 DataStore。
 *
 * 为什么需要：底部表单里的字段都是 Composable 内部的 remember 状态，
 * 表单一关（或者界面重建）状态就跟着没了，用户刚填的金额、课时全得重来一遍。
 * 这里按「表单 key + 字段名」把值暂存下来，下次打开同一个表单再带出来。
 */
object FormDrafts {

    private val drafts = mutableMapOf<String, MutableMap<String, String>>()

    /** 没存过返回 null，好让调用方自己决定用默认值 */
    fun read(draftKey: String, field: String): String? = drafts[draftKey]?.get(field)

    fun write(draftKey: String, field: String, value: String) {
        drafts.getOrPut(draftKey) { mutableMapOf() }[field] = value
    }

    /**
     * 保存成功之后调一下，把这张表的草稿清掉。
     * 注意只是关掉（dismiss）时千万别调，用户要的就是「关掉再打开还在」。
     */
    fun clear(draftKey: String) {
        drafts.remove(draftKey)
    }
}

/**
 * 文本字段的草稿 state。
 *
 * - draftKey 传 null 表示「这张表不存草稿」（编辑已有记录时用），退化成普通 remember；
 * - 有 key 时：第一次打开用 default，之后每次打开都用草稿里的值，边填边回写。
 */
@Composable
fun rememberDraftText(draftKey: String?, field: String, default: String): MutableState<String> {
    if (draftKey == null) return remember { mutableStateOf(default) }
    val state = remember(draftKey, field) {
        mutableStateOf(FormDrafts.read(draftKey, field) ?: default)
    }
    // 每次重组都回写一次：值一变就落到草稿里，中途关掉也不丢
    SideEffect { FormDrafts.write(draftKey, field, state.value) }
    return state
}

/** 开关字段：存成 "true" / "false" */
@Composable
fun rememberDraftBool(draftKey: String?, field: String, default: Boolean): MutableState<Boolean> {
    if (draftKey == null) return remember { mutableStateOf(default) }
    val state = remember(draftKey, field) {
        mutableStateOf(FormDrafts.read(draftKey, field)?.toBoolean() ?: default)
    }
    SideEffect { FormDrafts.write(draftKey, field, state.value.toString()) }
    return state
}

/** 数字字段：星期掩码、提醒时长这类 */
@Composable
fun rememberDraftInt(draftKey: String?, field: String, default: Int): MutableState<Int> {
    if (draftKey == null) return remember { mutableStateOf(default) }
    val state = remember(draftKey, field) {
        mutableStateOf(FormDrafts.read(draftKey, field)?.toIntOrNull() ?: default)
    }
    SideEffect { FormDrafts.write(draftKey, field, state.value.toString()) }
    return state
}

/**
 * 日期字段：存 "yyyy-MM-dd"，读不出来就用 default。
 * 「清空」特意存成空串而不是把这条删掉 —— 删掉的话下次打开又变回默认日期了，用户会以为没清掉。
 */
@Composable
fun rememberDraftDate(
    draftKey: String?,
    field: String,
    default: LocalDate?,
): MutableState<LocalDate?> {
    if (draftKey == null) return remember { mutableStateOf(default) }
    val state = remember(draftKey, field) {
        mutableStateOf(FormDrafts.read(draftKey, field)?.let { Dates.parseDate(it) } ?: default)
    }
    SideEffect { FormDrafts.write(draftKey, field, state.value?.let { Dates.formatDate(it) } ?: "") }
    return state
}
