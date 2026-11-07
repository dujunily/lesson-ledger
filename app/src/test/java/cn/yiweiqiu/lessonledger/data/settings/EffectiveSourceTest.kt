package cn.yiweiqiu.lessonledger.data.settings

import cn.yiweiqiu.lessonledger.domain.DataSource
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 演示数据总开关的判定规则。
 *
 * 全 App（首页 / 课程 / 统计 / 我的 / 小组件 / 提醒排程）都只认 [AppSettings.effectiveSource]，
 * 所以这条规则一旦守住，示例数据就没有别的缝可以漏出来。
 */
class EffectiveSourceTest {

    @Test
    fun `默认关闭 只看我的数据`() {
        val s = AppSettings()
        assertEquals(false, s.demoEnabled)
        assertEquals(DataSource.MINE, s.effectiveSource)
    }

    @Test
    fun `开关关着时 即使上次停在演示页也只给我的数据`() {
        // 老版本会把 dataSource 写成 demo；升级后总开关默认关，
        // 不能因此让用户一打开 App 就看到示例账本
        val s = AppSettings(dataSource = DataSource.DEMO.key, demoEnabled = false)
        assertEquals(DataSource.MINE, s.effectiveSource)
    }

    @Test
    fun `开关打开 才允许看演示数据`() {
        val s = AppSettings(dataSource = DataSource.DEMO.key, demoEnabled = true)
        assertEquals(DataSource.DEMO, s.effectiveSource)
    }

    @Test
    fun `开关打开但选的是我的数据 仍是我的数据`() {
        val s = AppSettings(dataSource = DataSource.MINE.key, demoEnabled = true)
        assertEquals(DataSource.MINE, s.effectiveSource)
    }

    @Test
    fun `脏数据 key 回落成我的数据`() {
        val s = AppSettings(dataSource = "some-garbage", demoEnabled = true)
        assertEquals(DataSource.MINE, s.effectiveSource)
    }
}
