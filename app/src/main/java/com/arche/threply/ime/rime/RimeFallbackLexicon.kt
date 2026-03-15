package com.arche.threply.ime.rime

/**
 * Fallback 拼音词库（静态词典，native Rime 不可用时兜底）
 * 主词库约 150 条高频会话词组 + Ext1/Ext2 扩展词库合计 500+ 条
 */
internal object RimeFallbackLexicon {

    // core candidate model
    private data class Candidate(val text: String, val score: Int)
    private fun c(t: String, s: Int) = Candidate(t, s)
    private fun entry(key: String, vararg cs: Candidate) = key to cs.toList()

    private val coreDictionary: Map<String, List<Candidate>> = mapOf(
        entry("ni", c("你",1200),c("呢",900),c("尼",780),c("泥",720),c("拟",680)),
        entry("hao", c("好",1200),c("号",820),c("浩",740),c("毫",700),c("豪",680)),
        entry("ma", c("吗",1150),c("嘛",780),c("马",760),c("妈",740),c("码",700)),
        entry("wo", c("我",1250),c("握",760),c("窝",720),c("沃",680),c("卧",650)),
        entry("men", c("们",1100),c("门",840),c("闷",700),c("焖",620)),
        entry("women", c("我们",1280),c("我们的",900),c("我们在",820)),
        entry("nimen", c("你们",1240),c("你们好",860)),
        entry("tamen", c("他们",1240),c("她们",920),c("它们",820)),
        entry("ta", c("他",1240),c("她",1200),c("它",960),c("塔",720)),
        entry("nihao", c("你好",1300),c("你好呀",900),c("你好啊",860)),
        entry("nihaoma", c("你好吗",1260),c("你好吗？",950)),
        entry("zaijian", c("再见",1260),c("回头见",900),c("拜拜",880)),
        entry("wanan", c("晚安",1240),c("晚安啦",860),c("早点休息",820)),
        entry("zaoshanghao", c("早上好",1220),c("早安",980)),
        entry("zhongwuhao", c("中午好",1180)),
        entry("xiawuhao", c("下午好",1140)),
        entry("xiexie", c("谢谢",1280),c("谢谢你",940),c("谢谢啦",860)),
        entry("xie", c("谢",950),c("写",920),c("些",900),c("鞋",760),c("协",700)),
        entry("duibuqi", c("对不起",1260),c("不好意思",980)),
        entry("meiguanxi", c("没关系",1260),c("没事",980),c("没关系的",860)),
        entry("bu", c("不",1250),c("步",760),c("部",740),c("布",700),c("补",660)),
        entry("buqikeqi", c("不客气",1300),c("不用客气",920)),
        entry("qing", c("请",1180),c("情",920),c("清",900),c("轻",820),c("青",780)),
        entry("qingwen", c("请问",1260),c("请问一下",900)),
        entry("bang", c("帮",1120),c("棒",880),c("榜",760),c("邦",700)),
        entry("mang", c("忙",1080),c("芒",740),c("盲",700),c("茫",680)),
        entry("bangmang", c("帮忙",1220),c("帮个忙",900)),
        entry("keyi", c("可以",1260),c("可以的",900),c("可以吗",860)),
        entry("keyima", c("可以吗",1260),c("行吗",920)),
        entry("haode", c("好的",1280),c("好的呢",880),c("好的好的",820)),
        entry("haoba", c("好吧",1260),c("好吧。",760)),
        entry("shoudao", c("收到",1250),c("已收到",920),c("收到啦",840)),
        entry("huifu", c("回复",1220),c("回复你",860),c("回覆",740)),
        entry("en", c("嗯",1220),c("恩",900)),
        entry("a", c("啊",1220),c("阿",900)),
        entry("ya", c("呀",1180),c("压",760)),
        entry("o", c("哦",1180),c("噢",860)),
        entry("la", c("啦",1160),c("拉",900),c("辣",760)),
        entry("haha", c("哈哈",1180),c("哈哈哈",900)),
        entry("hehe", c("呵呵",900),c("嘿嘿",860)),
        entry("wozai", c("我在",1260),c("我在呢",900),c("我在线",860)),
        entry("nizai", c("你在",1240),c("你在吗",920),c("你在线吗",860)),
        entry("zai", c("在",1250),c("再",980),c("载",700)),
        entry("zaima", c("在吗",1260),c("你在吗",900)),
        entry("nizaima", c("你在吗",1280),c("你在嘛",820)),
        entry("zaiganma", c("在干嘛",1260),c("在做什么",960)),
        entry("zaizuoshenme", c("在做什么",1260),c("在干嘛",980)),
        entry("ganma", c("干嘛",1220),c("干吗",900)),
        entry("shenme", c("什么",1280),c("什么事",860),c("什么啊",820)),
        entry("zenme", c("怎么",1220),c("怎么了",920),c("怎么说",860)),
        entry("zenmeyang", c("怎么样",1260),c("怎么样了",860)),
        entry("weishenme", c("为什么",1260),c("为啥",900)),
        entry("yinwei", c("因为",1240),c("因为你",860),c("因为我",840)),
        entry("suoyi", c("所以",1230),c("所以呢",860),c("所以说",840)),
        entry("keshi", c("可是",1220),c("可是啊",820)),
        entry("danshi", c("但是",1220),c("但",760)),
        entry("ranhou", c("然后",1210),c("然后呢",820)),
        entry("wozhidao", c("我知道",1260),c("我知道了",980),c("知道了",940)),
        entry("wozhidaole", c("我知道了",1280),c("知道了",1200)),
        entry("mingbai", c("明白",1240),c("明白了",980)),
        entry("mingbaile", c("明白了",1280),c("了解了",960)),
        entry("liaojie", c("了解",1220),c("了解了",980)),
        entry("liaojiele", c("了解了",1260),c("明白了",980)),
        entry("ok", c("OK",1220),c("好的",1180)),
        entry("okle", c("OK了",1240),c("搞定了",980)),
        entry("gaoding", c("搞定",1240),c("搞定了",980)),
        entry("gaodingle", c("搞定了",1280),c("已经搞定",980)),
        entry("wenti", c("问题",1240),c("有问题",860)),
        entry("meiwenti", c("没问题",1320),c("没有问题",1040),c("没啥问题",900)),
        entry("fangbian", c("方便",1220),c("方便吗",980)),
        entry("fangbianma", c("方便吗",1280),c("现在方便吗",900)),
        entry("xianzaiyoukongma", c("现在有空吗",1280),c("你现在有空吗",960)),
        entry("youkong", c("有空",1240),c("有时间",980)),
        entry("youkongma", c("有空吗",1280),c("现在有空吗",980)),
        entry("woxiangshuo", c("我想说",1240),c("我想说的是",920)),
        entry("woxiangwen", c("我想问",1240),c("我想问下",920),c("我想问一下",900)),
        entry("woxiangwenyixia", c("我想问一下",1300),c("请问",980)),
        entry("haoxiang", c("好像",1240),c("好像是",980),c("我好像",920)),
        entry("yinggai", c("应该",1240),c("应该是",980)),
        entry("keneng", c("可能",1240),c("有可能",920),c("可能是",900)),
        entry("queding", c("确定",1240),c("确定吗",980),c("我确定",920)),
        entry("quedingma", c("确定吗",1280),c("你确定吗",960)),
        entry("haodehaode", c("好的好的",1280),c("好嘞",980)),
        entry("haole", c("好了",1260),c("好啦",980),c("好了好了",900)),
        entry("haolehaole", c("好了好了",1280),c("行了行了",900)),
        entry("xingxingxing", c("行行行",1240),c("好好好",980)),
        entry("haohaohao", c("好好好",1240),c("行行行",980)),
        entry("xing", c("行",1260),c("性",860),c("型",820),c("星",800)),
        entry("xingba", c("行吧",1240),c("好吧",980)),
        entry("xingde", c("行的",1260),c("好的",1200)),
        entry("keyide", c("可以的",1260),c("可以",1200)),
        entry("shibushi", c("是不是",1300),c("是吗",980)),
        entry("duibudui", c("对不对",1280),c("对吗",960)),
        entry("duima", c("对吗",1260),c("对不对",980)),
        entry("shima", c("是吗",1260),c("真的吗",980)),
        entry("zhendema", c("真的吗",1300),c("真的嘛",920)),
        entry("dongle", c("懂了",1260),c("明白了",1200)),
        entry("budong", c("不懂",1220),c("我不懂",900)),
        entry("tingbudong", c("听不懂",1260),c("看不懂",980)),
        entry("kanbudong", c("看不懂",1260),c("不太懂",980)),
        entry("mafan", c("麻烦",1240),c("麻烦你",980),c("麻烦了",920)),
        entry("mafanni", c("麻烦你",1280),c("麻烦你了",980)),
        entry("mafannile", c("麻烦你了",1300),c("辛苦了",980)),
        entry("xinkule", c("辛苦了",1320),c("辛苦辛苦",900)),
        entry("xiexieni", c("谢谢你",1300),c("谢谢",1220)),
        entry("taihaole", c("太好了",1280),c("真棒",980)),
        entry("zhenbang", c("真棒",1240),c("太棒了",980)),
        entry("taibangle", c("太棒了",1300),c("真棒",980)),
        // 高频单字
        entry("you", c("有",1240),c("又",980),c("优",700),c("由",680)),
        entry("mei", c("没",1240),c("每",920),c("美",860),c("妹",720)),
        entry("youmei", c("有没有",1280),c("有没",740)),
        entry("meiyou", c("没有",1280),c("没用",760)),
        entry("shi", c("是",1300),c("时",900),c("事",880),c("市",760),c("试",720)),
        entry("de", c("的",1320),c("得",980),c("地",960),c("德",700)),
        entry("le", c("了",1280),c("乐",800),c("勒",680)),
        entry("ge", c("个",1260),c("各",920),c("歌",860),c("格",760)),
        entry("yi", c("一",1320),c("已",900),c("以",880),c("意",820),c("义",760)),
        entry("er", c("二",1220),c("而",900),c("儿",860)),
        entry("san", c("三",1210),c("散",700),c("伞",680)),
        entry("si", c("四",1200),c("思",800),c("司",760)),
        entry("wu", c("五",1200),c("无",900),c("物",840),c("务",780)),
        entry("liu", c("六",1180),c("流",820),c("留",800)),
        entry("qi", c("七",1180),c("其",900),c("期",860),c("气",820)),
        entry("ba", c("八",1160),c("把",900),c("吧",860),c("爸",760)),
        entry("jiu", c("九",1160),c("就",1200),c("久",820),c("酒",760)),
        entry("he", c("和",1250),c("喝",900),c("河",820),c("合",800)),
        entry("zhe", c("这",1280),c("者",760),c("着",900)),
        entry("na", c("那",1260),c("拿",860),c("哪",840)),
        entry("zuo", c("做",1200),c("坐",920),c("作",860),c("左",700)),
        entry("kan", c("看",1240),c("刊",700)),
        entry("lai", c("来",1260),c("赖",700)),
        entry("qu", c("去",1260),c("取",900),c("趣",760)),
        entry("shuo", c("说",1280),c("朔",680)),
        entry("ting", c("听",1220),c("停",880),c("挺",820)),
        entry("wen", c("问",1220),c("文",900),c("温",760)),
        entry("zhao", c("找",1200),c("照",880),c("朝",700)),
        entry("deng", c("等",1200),c("登",820),c("灯",760)),
        entry("xian", c("先",1180),c("现",980),c("线",820)),
        entry("dou", c("都",1280),c("斗",720),c("豆",700)),
        entry("zhi", c("只",1200),c("知",980),c("之",900),c("直",820)),
        entry("guo", c("国",1240),c("过",1100),c("果",860)),
        entry("zhong", c("中",1260),c("种",900),c("重",880)),
        entry("lian", c("联",1120),c("连",900),c("练",820)),
        entry("lianxi", c("联系",1280),c("练习",980)),
        entry("dian", c("点",1220),c("电",980),c("店",820)),
        entry("hua", c("话",1200),c("花",900),c("画",860)),
        entry("jia", c("家",1220),c("加",900),c("价",820))
    )

    // Bridge ext dictionaries with scored Candidates
    private fun extEntry(key: String, texts: List<String>) =
        key to texts.mapIndexed { i, t -> Candidate(t, 900 - i * 20) }

    private val ext1Dictionary: Map<String, List<Candidate>> =
        RimeFallbackLexiconExt1.entries.entries.associate { (k, v) -> extEntry(k, v) }

    private val ext2Dictionary: Map<String, List<Candidate>> =
        RimeFallbackLexiconExt2.entries.entries.associate { (k, v) -> extEntry(k, v) }

    /**
     * Look up pinyin candidates across core + ext1 + ext2 dictionaries.
     * Supports exact match (core priority) and prefix fallback.
     * Results sorted by score descending, paginated.
     */
    fun lookup(pinyin: String, limit: Int = 8, page: Int = 0): List<String> {
        if (pinyin.isBlank()) return emptyList()
        val key = pinyin.trim().lowercase()

        // Exact match — prefer core over ext
        val exact: List<Candidate> =
            coreDictionary[key]
                ?: ext1Dictionary[key]
                ?: ext2Dictionary[key]
                ?: emptyList()

        if (exact.isNotEmpty()) {
            return exact
                .sortedByDescending { it.score }
                .drop(page * limit)
                .take(limit)
                .map { it.text }
        }

        // Prefix match — merge all three, deduplicate, sort by score
        val prefixMatches: List<Candidate> =
            sequenceOf(coreDictionary, ext1Dictionary, ext2Dictionary)
                .flatMap { dict ->
                    dict.entries
                        .filter { it.key.startsWith(key) }
                        .flatMap { it.value }
                }
                .distinctBy { it.text }
                .sortedByDescending { it.score }
                .toList()

        return prefixMatches
            .drop(page * limit)
            .take(limit)
            .map { it.text }
    }
}
 