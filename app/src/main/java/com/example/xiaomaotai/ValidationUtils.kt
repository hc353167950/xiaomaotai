package com.example.xiaomaotai

/**
 * 输入校验：只保留纯本地功能需要的内容。
 * 账号/邮箱相关校验随账号系统一并移除。
 */
object ValidationUtils {

    /** 校验纪念日名称 */
    fun validateEventName(eventName: String?): ValidationResult {
        if (eventName.isNullOrEmpty()) {
            return ValidationResult(false, "事件名称不能为空")
        }
        if (eventName.length > 10) {
            return ValidationResult(false, "事件名称不能超过10个字符")
        }
        return ValidationResult(true, "")
    }

    /**
     * 校验事件日期字符串是否可解析且真实存在。
     *
     * 农历日期会真实转换一次（`Lunar(year, signedMonth, day)`），
     * 因此「闰月不存在」「农历三十遇到小月」这类非法组合会被拦下，
     * 不会等到倒计时阶段才发现。
     */
    fun validateEventDate(eventDate: String): ValidationResult {
        val parsed = DateParser.parse(eventDate)
            ?: return ValidationResult(false, "日期格式无效")

        // 农历：做一次真实转换，确认年月日组合存在
        if (parsed.type == DateParser.DateType.LUNAR && parsed.year != null) {
            val ok = LunarCalendarHelper.isValidLunarDate(
                lunarYear = parsed.year,
                lunarMonth = parsed.month,
                lunarDay = parsed.day,
                isLeap = parsed.isLeapMonth
            )
            if (!ok) {
                val label = "农历${LunarCalendarHelper.getLunarMonthName(parsed.month, parsed.isLeapMonth)}" +
                    LunarCalendarHelper.getLunarDayName(parsed.day)
                return ValidationResult(false, "该农历日期不存在：${parsed.year}年$label")
            }
        }

        // 公历：LocalDate.of 会校验月日合法性
        if (parsed.type == DateParser.DateType.SOLAR) {
            val year = parsed.year
            if (year == null ||
                !isValidSolarDate(year, parsed.month, parsed.day)
            ) {
                return ValidationResult(false, "该公历日期不存在")
            }
        }

        // 忽略年份格式：只校验月日范围（2月29日按 2月28日处理，属合法输入）
        if (parsed.type == DateParser.DateType.MONTH_DAY) {
            if (!isValidSolarDate(2024, parsed.month, parsed.day)) {
                return ValidationResult(false, "该日期不存在")
            }
        }

        return ValidationResult(true, "")
    }

    private fun isValidSolarDate(year: Int, month: Int, day: Int): Boolean = try {
        java.time.LocalDate.of(year, month, day)
        true
    } catch (e: Exception) {
        false
    }
}

data class ValidationResult(val isValid: Boolean, val message: String)
