package cn.yiweiqiu.lessonledger.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.IceSkating
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Skateboarding
import androidx.compose.material.icons.rounded.SportsBaseball
import androidx.compose.material.icons.rounded.SportsBasketball
import androidx.compose.material.icons.rounded.SportsGymnastics
import androidx.compose.material.icons.rounded.SportsMartialArts
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.SportsTennis
import androidx.compose.material.icons.rounded.SportsVolleyball
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import cn.yiweiqiu.lessonledger.domain.Category

/**
 * 课程图标。
 *
 * 之前只有「按科目」一档：体育一律是泳池、舞蹈一律是跑步小人 ——
 * 羽毛球课顶着一个跑步的人，跳舞课也是，一眼看不出这门课是什么。
 *
 * 现在先按**课程名**找最像的那颗图标（羽毛球 / 游泳 / 舞蹈 / 钢琴 / 美术 …），
 * 找不到再回落到科目图标。羽毛球和舞蹈 Material 里没有，自己画。
 */

// ---------------------------------------------------------------- 自绘图标

/** PathBuilder 没有画圆的接口，用两段半圆弧拼一个 */
private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, true, true, cx + r, cy)
    arcTo(r, r, 0f, true, true, cx - r, cy)
    close()
}

/** 羽毛球：上宽下窄的羽毛裙 + 下面那颗球托 */
private val Badminton: ImageVector = ImageVector.Builder(
    name = "Badminton",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black)) {
        // 羽毛裙（侧面看是个上宽下窄的梯形，边缘略带一点收口）
        moveTo(5.0f, 2.6f)
        lineTo(19.0f, 2.6f)
        lineTo(14.0f, 12.9f)
        lineTo(10.0f, 12.9f)
        close()
    }
    path(fill = SolidColor(Color.Black)) {
        // 球托
        circle(12f, 15.7f, 2.7f)
    }
}.build()

/** 舞蹈：双手上举、一条腿踢起的舞者，和「跑步」的横向前倾姿态区分开 */
private val Dance: ImageVector = ImageVector.Builder(
    name = "Dance",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black)) { circle(12f, 4.1f, 2.1f) }
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.9f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(12f, 6.6f)
        lineTo(12f, 12.4f)      // 躯干
        moveTo(12f, 7.5f)
        lineTo(7.3f, 3.5f)      // 左臂上举
        moveTo(12f, 7.5f)
        lineTo(16.7f, 3.5f)     // 右臂上举
        moveTo(12f, 12.4f)
        lineTo(8.9f, 20.0f)     // 支撑腿
        moveTo(12f, 12.4f)
        lineTo(16.6f, 15.9f)    // 踢起的腿
    }
    path(fill = SolidColor(Color.Black)) {
        // 裙摆
        moveTo(9.0f, 10.9f)
        lineTo(15.0f, 10.9f)
        lineTo(13.7f, 14.2f)
        lineTo(10.3f, 14.2f)
        close()
    }
}.build()

// ---------------------------------------------------------------- 名称匹配

private data class GlyphRule(val icon: ImageVector, val keywords: List<String>)

/**
 * 顺序有意义：越具体的球类越靠前，通用的「球拍」「训练」放后面，
 * 否则「乒乓球」会被「球拍」先吃掉（结果一样，但规则要能读）。
 */
private val glyphRules: List<GlyphRule> = listOf(
    GlyphRule(Badminton, listOf("羽毛球", "羽球", "badminton")),
    GlyphRule(Icons.Rounded.Pool, listOf("游泳", "泳", "潜水", "水上")),
    GlyphRule(Icons.Rounded.SportsBasketball, listOf("篮球")),
    GlyphRule(Icons.Rounded.SportsSoccer, listOf("足球")),
    GlyphRule(Icons.Rounded.SportsVolleyball, listOf("排球")),
    GlyphRule(Icons.Rounded.SportsBaseball, listOf("棒球", "垒球")),
    GlyphRule(Icons.Rounded.SportsTennis, listOf("网球", "乒乓球", "乒乓", "壁球", "球拍")),
    GlyphRule(Icons.Rounded.Skateboarding, listOf("轮滑", "滑板", "滑步车")),
    GlyphRule(Icons.Rounded.IceSkating, listOf("滑冰", "冰球", "花样滑冰")),
    GlyphRule(
        Icons.Rounded.SportsMartialArts,
        listOf("武术", "跆拳道", "空手道", "柔道", "散打", "拳击", "搏击", "击剑"),
    ),
    GlyphRule(Icons.Rounded.SportsGymnastics, listOf("体操", "体能", "体适能", "感统", "攀岩", "跳绳")),
    GlyphRule(Dance, listOf("舞蹈", "舞", "芭蕾", "拉丁", "街舞", "爵士", "形体")),
    GlyphRule(Icons.Rounded.Piano, listOf("钢琴", "电子琴", "双排键")),
    GlyphRule(
        Icons.Rounded.MusicNote,
        listOf(
            "音乐", "声乐", "合唱", "小提琴", "吉他", "古筝", "二胡", "尤克里里",
            "架子鼓", "乐理", "奥尔夫", "长笛", "萨克斯",
        ),
    ),
    GlyphRule(Icons.Rounded.Mic, listOf("主持", "口才", "演讲", "播音", "小记者")),
    GlyphRule(
        Icons.Rounded.Brush,
        listOf("美术", "绘画", "画画", "素描", "水彩", "油画", "创意画", "手工", "陶艺", "书法"),
    ),
    GlyphRule(Icons.Rounded.Calculate, listOf("数学", "奥数", "珠心算", "思维")),
    GlyphRule(
        Icons.Rounded.MenuBook,
        listOf("英语", "语文", "作文", "阅读", "识字", "拼音", "写作", "物理", "化学", "生物", "历史", "地理"),
    ),
    GlyphRule(Icons.Rounded.Code, listOf("编程", "机器人", "代码", "信息学", "人工智能")),
    GlyphRule(Icons.Rounded.Science, listOf("科学", "实验", "steam")),
    GlyphRule(Icons.Rounded.Extension, listOf("乐高", "积木", "拼图")),
    GlyphRule(Icons.Rounded.Cake, listOf("烘焙", "厨艺", "烹饪")),
    GlyphRule(Icons.Rounded.DirectionsRun, listOf("跑步", "田径", "体测")),
)

/** 课程图标：先认课程名，认不出来再回落到科目 */
fun courseIcon(courseName: String, category: Category): ImageVector {
    val name = courseName.trim().lowercase()
    if (name.isNotEmpty()) {
        glyphRules.firstOrNull { rule -> rule.keywords.any { name.contains(it.lowercase()) } }
            ?.let { return it.icon }
    }
    return categoryIcon(category)
}

/** 科目图标：课程名认不出来时的兜底，绝不用 emoji */
fun categoryIcon(category: Category): ImageVector = when (category) {
    Category.MUSIC -> Icons.Rounded.MusicNote
    Category.SPORTS -> Icons.Rounded.SportsTennis
    Category.ACADEMIC -> Icons.Rounded.MenuBook
    Category.ART -> Icons.Rounded.Brush
    Category.DANCE -> Dance
    Category.CODING -> Icons.Rounded.Code
    Category.OTHER -> Icons.Rounded.Category
}
