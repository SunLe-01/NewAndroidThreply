package com.arche.threply.ime.pinyin

class PinyinComposer {
    private val rawBuffer = StringBuilder()

    private val staticDictionary: Map<String, List<String>> = mapOf(
        "ni" to listOf("你", "呢", "尼"),
        "hao" to listOf("好", "号", "浩"),
        "nihao" to listOf("你好", "你号"),
        "wo" to listOf("我", "握", "窝"),
        "women" to listOf("我们"),
        "shi" to listOf("是", "时", "事"),
        "zhong" to listOf("中", "种", "重"),
        "guo" to listOf("国", "过", "果"),
        "zhongguo" to listOf("中国"),
        "xie" to listOf("谢", "写", "些"),
        "xiexie" to listOf("谢谢"),
        "qing" to listOf("请", "情", "清"),
        "bang" to listOf("帮", "棒", "榜"),
        "mang" to listOf("忙", "芒", "盲"),
        "qingbangmang" to listOf("请帮忙"),
        "huifu" to listOf("回复"),
        "shoudao" to listOf("收到"),
        "wan" to listOf("晚", "万", "完"),
        "dian" to listOf("点", "电", "店"),
        "lianxi" to listOf("联系"),
        "haode" to listOf("好的"),
        "keyi" to listOf("可以")
    )

    fun push(ch: Char) {
        if (ch in 'a'..'z') {
            rawBuffer.append(ch)
        }
    }

    fun pop() {
        if (rawBuffer.isNotEmpty()) {
            rawBuffer.deleteCharAt(rawBuffer.lastIndex)
        }
    }

    fun clear() {
        rawBuffer.clear()
    }

    fun currentRaw(): String = rawBuffer.toString()

    fun candidates(): List<String> {
        val raw = currentRaw()
        if (raw.isBlank()) return emptyList()

        val direct = staticDictionary[raw].orEmpty()
        if (direct.isNotEmpty()) return direct

        val prefixMatches = staticDictionary
            .filterKeys { it.startsWith(raw) }
            .values
            .flatten()
            .distinct()
        if (prefixMatches.isNotEmpty()) return prefixMatches

        return listOf(raw)
    }
}
