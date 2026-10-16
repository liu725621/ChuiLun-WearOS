package com.chuilun.app.config

/**
 * 全部手感参数集中在这里。真机上如果觉得抛竿太灵敏、遛鱼太难，
 * 只需要改这一个文件里的数字，不用碰任何逻辑代码。
 */
object Tuning {

    // ---------------- 抛竿体感 ----------------
    /** 采样频率（微秒）。50Hz 足够捕捉挥臂峰值，又不至于费电 */
    const val SENSOR_DELAY_US = 20_000

    /** 抛竿准备后的检测窗口（毫秒） */
    const val CAST_WINDOW_MS = 3_000L

    /** 角速度峰值归一化基准（rad/s）。超过这个值即视为满力 */
    const val GYRO_FULL_SCALE = 9.0f

    /** 线加速度峰值归一化基准（m/s²）。与角速度取较大者 */
    const val ACCEL_FULL_SCALE = 22.0f

    /** 默认力度校准系数（用户校准后覆盖） */
    const val DEFAULT_SENSITIVITY = 1.0f

    /** 灵敏度允许范围 */
    const val SENSITIVITY_MIN = 0.35f
    const val SENSITIVITY_MAX = 2.60f

    /** 抛投距离换算：近端 / 远端（米） */
    const val CAST_DIST_NEAR = 3f
    const val CAST_DIST_FAR = 60f

    /** 抛物线飞行动画时长（毫秒） */
    const val FLY_DURATION_MS = 900L

    // ---------------- 等待咬钩 ----------------
    /** 咬钩等待时间区间（毫秒） */
    const val WAIT_MIN_MS = 3_000L
    const val WAIT_MAX_MS = 18_000L

    /** 咬钩前假动作（试探）次数区间 */
    const val TEASE_MIN = 1
    const val TEASE_MAX = 3

    // ---------------- 遛鱼博弈 ----------------
    /** 每次逻辑更新的间隔（毫秒） */
    const val FIGHT_TICK_MS = 33L

    /** 收线速度（每秒收掉的剩余距离比例） */
    const val REEL_RATE = 0.30f

    /** 松手时鱼游远的速度系数 */
    const val SLIP_RATE = 0.14f

    /** 按住时张力上升基础值 / 拉力加成 */
    const val TENSION_BASE = 0.34f
    const val TENSION_PULL = 0.62f

    /** 松手时张力回落速度 */
    const val TENSION_RECOVER = 0.46f

    /** 张力危险阈值与允许持续时间（秒）—— 超时即断线 */
    const val TENSION_DANGER = 0.90f
    const val DANGER_TOLERANCE = 1.15f

    /** 体力消耗：按住 / 松手（每秒） */
    const val STAMINA_DRAIN_REEL = 0.20f
    const val STAMINA_DRAIN_IDLE = 0.05f

    /** 鱼在两种挣扎模式之间切换的时间区间（秒），狡猾度越高切换越频繁 */
    const val AI_SWITCH_MIN = 0.8f
    const val AI_SWITCH_MAX = 2.6f

    // ---------------- 出现权重 ----------------
    /** 稀有度权重基数：weight = (10 - rarity) ^ RARITY_EXP */
    const val RARITY_EXP = 2.3f

    /** 时段 / 天气 / 季节 / 远近 命中时的权重倍率 */
    const val MULT_PHASE = 2.4f
    const val MULT_WEATHER = 1.6f
    const val MULT_SEASON = 1.35f
    const val MULT_FAR = 2.0f

    /** 远投加成：preferFar 与落点距离吻合时的插值权重 */
    const val FAR_MATCH_BONUS = 2.2f

    /** 每日活跃鱼种的额外权重倍率 */
    const val MULT_DAILY_ACTIVE = 5.0f

    /** 限定神秘事件触发概率（每次抛竿判定一次）。
     *  这个值直接决定那些荒诞彩蛋你能不能见到，调大一点会更欢乐。 */
    const val EVENT_CHANCE = 0.07f

    // ---------------- 画面 ----------------
    /** 像素网格列数（越小越"像素"） */
    const val PIXEL_COLS = 34
}
