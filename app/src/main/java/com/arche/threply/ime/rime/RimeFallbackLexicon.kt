package com.arche.threply.ime.rime

internal object RimeFallbackLexicon {
    private data class Candidate(val text: String, val score: Int)

    private fun c(text: String, score: Int) = Candidate(text, score)
    private fun entry(key: String, vararg candidates: Candidate) = key to candidates.toList()

    private val baseConversationDictionary: Map<String, List<Candidate>> = mapOf(
        entry("ni", c("你", 1200), c("呢", 900), c("尼", 780), c("泥", 720), c("拟", 680)),
        entry("hao", c("好", 1200), c("号", 820), c("浩", 740), c("毫", 700), c("豪", 680)),
        entry("ma", c("吗", 1150), c("嘛", 780), c("马", 760), c("妈", 740), c("码", 700)),
        entry("wo", c("我", 1250), c("握", 760), c("窝", 720), c("沃", 680), c("卧", 650)),
        entry("men", c("们", 1100), c("门", 840), c("闷", 700), c("焖", 620)),
        entry("women", c("我们", 1280), c("我们的", 900), c("我们在", 820)),
        entry("nimen", c("你们", 1240), c("你们好", 860)),
        entry("tamen", c("他们", 1240), c("她们", 920), c("它们", 820)),
        entry("ta", c("他", 1240), c("她", 1200), c("它", 960), c("塔", 720)),
        entry("nihao", c("你好", 1300), c("你好呀", 900), c("你好啊", 860)),
        entry("nihaoma", c("你好吗", 1260), c("你好吗？", 950)),
        entry("zaijian", c("再见", 1260), c("回头见", 900), c("拜拜", 880)),
        entry("wanan", c("晚安", 1240), c("晚安啦", 860), c("早点休息", 820)),
        entry("zaoshanghao", c("早上好", 1220), c("早安", 980)),
        entry("zhongwuhao", c("中午好", 1180)),
        entry("xiawuhao", c("下午好", 1140)),
        entry("xiexie", c("谢谢", 1280), c("谢谢你", 940), c("谢谢啦", 860)),
        entry("xie", c("谢", 950), c("写", 920), c("些", 900), c("鞋", 760), c("协", 700)),
        entry("duibuqi", c("对不起", 1260), c("不好意思", 980)),
        entry("meiguanxi", c("没关系", 1260), c("没事", 980), c("没关系的", 860)),
        entry("bu", c("不", 1250), c("步", 760), c("部", 740), c("布", 700), c("补", 660)),
        entry("keqi", c("客气", 1080), c("可期", 740)),
        entry("buqikeqi", c("不客气", 1300), c("不用客气", 920)),
        entry("qing", c("请", 1180), c("情", 920), c("清", 900), c("轻", 820), c("青", 780)),
        entry("qingwen", c("请问", 1260), c("请问一下", 900)),
        entry("bang", c("帮", 1120), c("棒", 880), c("榜", 760), c("邦", 700)),
        entry("mang", c("忙", 1080), c("芒", 740), c("盲", 700), c("茫", 680)),
        entry("bangmang", c("帮忙", 1220), c("帮个忙", 900)),
        entry("keyi", c("可以", 1260), c("可以的", 900), c("可以吗", 860)),
        entry("keyima", c("可以吗", 1260), c("行吗", 920)),
        entry("haode", c("好的", 1280), c("好的呢", 880), c("好的好的", 820)),
        entry("haoba", c("好吧", 1260), c("好吧。", 760)),
        entry("shoudao", c("收到", 1250), c("已收到", 920), c("收到啦", 840)),
        entry("huifu", c("回复", 1220), c("回复你", 860), c("回覆", 740)),
        entry("en", c("嗯", 1220), c("恩", 900)),
        entry("a", c("啊", 1220), c("阿", 900)),
        entry("ya", c("呀", 1180), c("压", 760)),
        entry("o", c("哦", 1180), c("噢", 860)),
        entry("la", c("啦", 1160), c("拉", 900), c("辣", 760)),
        entry("haha", c("哈哈", 1180), c("哈哈哈", 900)),
        entry("hehe", c("呵呵", 900), c("嘿嘿", 860)),
        entry("wohao", c("我好", 860), c("我好像", 830)),
        entry("wozai", c("我在", 1260), c("我在呢", 900), c("我在线", 860)),
        entry("nizai", c("你在", 1240), c("你在吗", 920), c("你在线吗", 860)),
        entry("zai", c("在", 1250), c("再", 980), c("载", 700)),
        entry("zaima", c("在吗", 1260), c("你在吗", 900)),
        entry("nizaima", c("你在吗", 1280), c("你在嘛", 820)),
        entry("zaiganma", c("在干嘛", 1260), c("在做什么", 960)),
        entry("zaizuoshenme", c("在做什么", 1260), c("在干嘛", 980)),
        entry("ganma", c("干嘛", 1220), c("干吗", 900)),
        entry("shenme", c("什么", 1280), c("什么事", 860), c("什么啊", 820)),
        entry("zenme", c("怎么", 1220), c("怎么了", 920), c("怎么说", 860)),
        entry("zenmeyang", c("怎么样", 1260), c("怎么样了", 860)),
        entry("weishenme", c("为什么", 1260), c("为啥", 900)),
        entry("yinwei", c("因为", 1240), c("因为你", 860), c("因为我", 840)),
        entry("suoyi", c("所以", 1230), c("所以呢", 860), c("所以说", 840)),
        entry("keshi", c("可是", 1220), c("可是啊", 820)),
        entry("danshi", c("但是", 1220), c("但", 760)),
        entry("ranhou", c("然后", 1210), c("然后呢", 820)),
        entry("zaishuo", c("再说", 1220), c("之后再说", 860)),
        entry("keyizaishuo", c("可以再说", 1240), c("我们可以再说", 860)),
        entry("wozhidao", c("我知道", 1260), c("我知道了", 980), c("知道了", 940)),
        entry("wozhidaole", c("我知道了", 1280), c("知道了", 1200)),
        entry("mingbai", c("明白", 1240), c("明白了", 980)),
        entry("mingbaile", c("明白了", 1280), c("了解了", 960)),
        entry("liaojie", c("了解", 1220), c("了解了", 980)),
        entry("liaojiele", c("了解了", 1260), c("明白了", 980)),
        entry("ok", c("OK", 1220), c("好的", 1180)),
        entry("okle", c("OK了", 1240), c("搞定了", 980)),
        entry("gaoding", c("搞定", 1240), c("搞定了", 980)),
        entry("gaodingle", c("搞定了", 1280), c("已经搞定", 980)),
        entry("wenti", c("问题", 1240), c("有问题", 860)),
        entry("meiwenti", c("没问题", 1320), c("没有问题", 1040), c("没啥问题", 900)),
        entry("fangbian", c("方便", 1220), c("方便吗", 980)),
        entry("fangbianma", c("方便吗", 1280), c("现在方便吗", 900)),
        entry("xianzaiyoukongma", c("现在有空吗", 1280), c("你现在有空吗", 960)),
        entry("youkong", c("有空", 1240), c("有时间", 980)),
        entry("youkongma", c("有空吗", 1280), c("现在有空吗", 980)),
        entry("woyaoshuo", c("我要说", 860), c("我想说", 980)),
        entry("woxiangshuo", c("我想说", 1240), c("我想说的是", 920)),
        entry("woxiangwen", c("我想问", 1240), c("我想问下", 920), c("我想问一下", 900)),
        entry("woxiangwenxia", c("我想问下", 1260), c("我想问一下", 980)),
        entry("woxiangwenyixia", c("我想问一下", 1300), c("请问", 980)),
        entry("qingkuang", c("情况", 1220), c("什么情况", 940)),
        entry("shenmeqingkuang", c("什么情况", 1280), c("现在什么情况", 920)),
        entry("haoxiang", c("好像", 1240), c("好像是", 980), c("我好像", 920)),
        entry("yinggai", c("应该", 1240), c("应该是", 980)),
        entry("keneng", c("可能", 1240), c("有可能", 920), c("可能是", 900)),
        entry("queding", c("确定", 1240), c("确定吗", 980), c("我确定", 920)),
        entry("quedingma", c("确定吗", 1280), c("你确定吗", 960)),
        entry("meiwen", c("没问", 580), c("没问题", 1200)),
        entry("haodehaode", c("好的好的", 1280), c("好嘞", 980)),
        entry("haole", c("好了", 1260), c("好啦", 980), c("好了好了", 900)),
        entry("haolehaole", c("好了好了", 1280), c("行了行了", 900)),
        entry("xingxingxing", c("行行行", 1240), c("好好好", 980)),
        entry("haohaohao", c("好好好", 1240), c("行行行", 980)),
        entry("xing", c("行", 1260), c("性", 860), c("型", 820), c("星", 800)),
        entry("xingba", c("行吧", 1240), c("好吧", 980)),
        entry("xingde", c("行的", 1260), c("好的", 1200)),
        entry("keyide", c("可以的", 1260), c("可以", 1200)),
        entry("shibushi", c("是不是", 1300), c("是吗", 980)),
        entry("duibudui", c("对不对", 1280), c("对吗", 960)),
        entry("duima", c("对吗", 1260), c("对不对", 980)),
        entry("shima", c("是吗", 1260), c("真的吗", 980)),
        entry("zhenma", c("真吗", 1240), c("真的吗", 980)),
        entry("zhendema", c("真的吗", 1300), c("真的嘛", 920)),
        entry("dongle", c("懂了", 1260), c("明白了", 1200)),
        entry("budong", c("不懂", 1220), c("我不懂", 900)),
        entry("tingbudong", c("听不懂", 1260), c("看不懂", 980)),
        entry("kanbudong", c("看不懂", 1260), c("不太懂", 980)),
        entry("mafan", c("麻烦", 1240), c("麻烦你", 980), c("麻烦了", 920)),
        entry("mafanni", c("麻烦你", 1280), c("麻烦你了", 980)),
        entry("mafannile", c("麻烦你了", 1300), c("辛苦了", 980)),
        entry("xinkule", c("辛苦了", 1320), c("辛苦辛苦", 900)),
        entry("xiexieni", c("谢谢你", 1300), c("谢谢", 1220)),
        entry("taihaole", c("太好了", 1280), c("真棒", 980)),
        entry("zhenbang", c("真棒", 1240), c("太棒了", 980)),
        entry("taibangle", c("太棒了", 1300), c("真棒", 980))
    )

    private val highFrequencyDictionary: Map<String, List<Candidate>> = mapOf(
        entry("you", c("有", 1240), c("又", 980), c("优", 700), c("由", 680)),
        entry("mei", c("没", 1240), c("每", 920), c("美", 860), c("妹", 720)),
        entry("youmei", c("有没有", 1280), c("有没", 740)),
        entry("meiyou", c("没有", 1280), c("没用", 760), c("没由", 620)),
        entry("shi", c("是", 1300), c("时", 900), c("事", 880), c("市", 760), c("试", 720)),
        entry("de", c("的", 1320), c("得", 980), c("地", 960), c("德", 700)),
        entry("le", c("了", 1280), c("乐", 800), c("勒", 680)),
        entry("ge", c("个", 1260), c("各", 920), c("歌", 860), c("格", 760)),
        entry("yi", c("一", 1320), c("已", 900), c("以", 880), c("意", 820), c("义", 760)),
        entry("er", c("二", 1220), c("而", 900), c("儿", 860)),
        entry("san", c("三", 1210), c("散", 700), c("伞", 680)),
        entry("si", c("四", 1200), c("是", 820), c("思", 800), c("司", 760)),
        entry("wu", c("五", 1200), c("无", 900), c("物", 840), c("务", 780)),
        entry("liu", c("六", 1180), c("流", 820), c("留", 800)),
        entry("qi", c("七", 1180), c("起", 940), c("其", 920), c("气", 880), c("期", 860)),
        entry("ba", c("吧", 1160), c("八", 1020), c("把", 940), c("爸", 780)),
        entry("jiu", c("就", 1220), c("九", 980), c("久", 820)),
        entry("ren", c("人", 1260), c("认", 900), c("任", 860), c("仁", 760)),
        entry("zhe", c("这", 1260), c("着", 900), c("者", 860), c("折", 720)),
        entry("na", c("那", 1260), c("哪", 980), c("拿", 860), c("呐", 700)),
        entry("nar", c("哪儿", 1200), c("那儿", 860)),
        entry("zheli", c("这里", 1230), c("这边", 900), c("这儿", 860)),
        entry("nali", c("哪里", 1230), c("那边", 900), c("哪儿", 860)),
        entry("xing", c("行", 1240), c("性", 860), c("型", 820), c("星", 800)),
        entry("zhende", c("真的", 1260), c("真的吗", 900)),
        entry("zhen", c("真", 1240), c("针", 700), c("珍", 680)),
        entry("jintian", c("今天", 1240), c("今天下午", 860), c("今天晚上", 840)),
        entry("mingtian", c("明天", 1220), c("明天见", 900), c("明天再说", 840)),
        entry("xianzai", c("现在", 1240), c("现在就", 880), c("现在可以", 840)),
        entry("zuotian", c("昨天", 1220), c("昨天晚上", 840)),
        entry("jintianwan", c("今天晚上", 1220), c("今晚", 980)),
        entry("jintianxiawu", c("今天下午", 1200)),
        entry("dengyi", c("等一", 700), c("等一等", 760)),
        entry("dengyixia", c("等一下", 1260), c("稍等一下", 900), c("等会儿", 860)),
        entry("shaodeng", c("稍等", 1240), c("稍等一下", 900)),
        entry("mashang", c("马上", 1260), c("马上来", 900), c("马上好", 860)),
        entry("yihuir", c("一会儿", 1240), c("等我一会儿", 860)),
        entry("shenmeshihou", c("什么时候", 1240), c("啥时候", 920)),
        entry("zenmeban", c("怎么办", 1240), c("怎么处理", 860))
    )

    private val topicDictionary: Map<String, List<Candidate>> = mapOf(
        entry("zhong", c("中", 1240), c("种", 900), c("重", 880), c("终", 760)),
        entry("guo", c("国", 1230), c("过", 980), c("果", 920), c("锅", 760)),
        entry("zhongguo", c("中国", 1320), c("中国人", 920), c("中国的", 860)),
        entry("meiguo", c("美国", 1240)),
        entry("yingguo", c("英国", 1180)),
        entry("faguo", c("法国", 1160)),
        entry("hanguo", c("韩国", 1160)),
        entry("riben", c("日本", 1220)),
        entry("shijie", c("世界", 1260), c("全世界", 840)),
        entry("woaini", c("我爱你", 1240), c("我爱你呀", 820)),
        entry("woaizhongguo", c("我爱中国", 1160)),
        entry("jia", c("家", 1220), c("加", 960), c("假", 860), c("价", 760)),
        entry("jiaren", c("家人", 1220), c("家里人", 840)),
        entry("pengyou", c("朋友", 1240), c("男朋友", 820), c("女朋友", 820)),
        entry("gongsi", c("公司", 1240), c("公司里", 820)),
        entry("laoshi", c("老师", 1240), c("老实", 680)),
        entry("xuesheng", c("学生", 1240)),
        entry("tongxue", c("同学", 1220)),
        entry("gongzuo", c("工作", 1260), c("工作中", 860), c("工作了", 820)),
        entry("shangban", c("上班", 1240), c("上班了", 860)),
        entry("xiaban", c("下班", 1240), c("下班了", 860)),
        entry("xuexi", c("学习", 1240), c("学习中", 840)),
        entry("shenghuo", c("生活", 1220), c("生活中", 820)),
        entry("wenti", c("问题", 1240), c("有问题", 860)),
        entry("jiejue", c("解决", 1220), c("已解决", 860)),
        entry("queren", c("确认", 1240), c("请确认", 900)),
        entry("tongyi", c("同意", 1230), c("我同意", 900)),
        entry("jujue", c("拒绝", 1160), c("已拒绝", 760)),
        entry("anzhuang", c("安装", 1220)),
        entry("gengxin", c("更新", 1220), c("已更新", 860)),
        entry("ceshi", c("测试", 1220), c("测试中", 820)),
        entry("mingzi", c("名字", 1260), c("用户名", 840))
    )

    private val dailyActionDictionary: Map<String, List<Candidate>> = mapOf(
        entry("chi", c("吃", 1180), c("持", 760), c("迟", 720)),
        entry("fan", c("饭", 1160), c("反", 900), c("范", 760)),
        entry("chifan", c("吃饭", 1260), c("吃饭了吗", 900), c("去吃饭", 860)),
        entry("he", c("和", 1280), c("喝", 980), c("合", 920), c("河", 760)),
        entry("heshui", c("喝水", 1220), c("多喝水", 900)),
        entry("shuijiao", c("睡觉", 1260), c("睡觉了", 860)),
        entry("qichuang", c("起床", 1220), c("起床了", 840)),
        entry("chumen", c("出门", 1210), c("准备出门", 820)),
        entry("huijia", c("回家", 1240), c("回家了", 900), c("先回家", 860)),
        entry("daole", c("到了", 1240), c("我到了", 900), c("已经到了", 860)),
        entry("lai", c("来", 1240), c("莱", 680), c("赖", 660)),
        entry("guoqu", c("过去", 1220), c("过去了", 840)),
        entry("guolai", c("过来", 1230), c("过来一下", 860)),
        entry("qu", c("去", 1240), c("取", 900), c("区", 760)),
        entry("quna", c("去哪", 1220), c("去哪里", 980)),
        entry("qunali", c("去哪里", 1240), c("去哪儿", 900)),
        entry("kankan", c("看看", 1220), c("看一下", 980)),
        entry("ting", c("听", 1160), c("停", 900), c("庭", 700)),
        entry("shuo", c("说", 1220), c("硕", 700), c("朔", 680)),
        entry("gaosu", c("告诉", 1230), c("告诉我", 920)),
        entry("fasong", c("发送", 1220), c("已发送", 860)),
        entry("xiaoxi", c("消息", 1220), c("新消息", 860)),
        entry("lianxi", c("联系", 1220), c("联系我", 860))
    )

    private val emotionDictionary: Map<String, List<Candidate>> = mapOf(
        entry("kaixin", c("开心", 1240), c("真开心", 860)),
        entry("gaoxing", c("高兴", 1240), c("很高兴", 900)),
        entry("nanguo", c("难过", 1220), c("有点难过", 820)),
        entry("shengqi", c("生气", 1220), c("别生气", 860)),
        entry("fangxin", c("放心", 1230), c("请放心", 900)),
        entry("danxin", c("担心", 1220), c("别担心", 900)),
        entry("zhichi", c("支持", 1220), c("支持你", 920)),
        entry("jiayou", c("加油", 1260), c("一起加油", 900)),
        entry("zhufu", c("祝福", 1180), c("祝你", 920)),
        entry("zhunbei", c("准备", 1220), c("准备好了", 900), c("准备中", 820)),
        entry("wancheng", c("完成", 1220), c("已完成", 900)),
        entry("chenggong", c("成功", 1220), c("成功了", 860)),
        entry("shibai", c("失败", 1120), c("失败了", 760)),
        entry("xiwang", c("希望", 1220), c("希望你", 860)),
        entry("juede", c("觉得", 1220), c("我觉得", 900), c("你觉得", 860))
    )

    private val dictionary: Map<String, List<Candidate>> = buildMap {
        putAll(baseConversationDictionary)
        putAll(highFrequencyDictionary)
        putAll(topicDictionary)
        putAll(dailyActionDictionary)
        putAll(emotionDictionary)
    }

    fun lookup(raw: String, limit: Int = 8, page: Int = 0): List<String> {
        val input = raw.trim().lowercase()
        if (input.isBlank()) return emptyList()

        val direct = dictionary[input].orEmpty()
        val merged = if (direct.isNotEmpty()) {
            rankAndDeduplicate(direct)
        } else {
            val prefixMatches = dictionary
                .asSequence()
                .filter { (key, _) -> key.startsWith(input) }
                .flatMap { (key, values) ->
                    val gap = (key.length - input.length).coerceAtLeast(0)
                    val prefixBonus = (180 - gap * 22).coerceAtLeast(30)
                    values.asSequence().map { candidate ->
                        Candidate(candidate.text, candidate.score + prefixBonus)
                    }
                }
                .toList()

            rankAndDeduplicate(prefixMatches)
        }

        val safeLimit = limit.coerceIn(1, 20)
        val safePage = page.coerceAtLeast(0)
        val from = safePage * safeLimit
        if (from >= merged.size) return emptyList()
        val to = (from + safeLimit).coerceAtMost(merged.size)
        return merged.subList(from, to).map { it.text }
    }

    private fun rankAndDeduplicate(candidates: List<Candidate>): List<Candidate> {
        if (candidates.isEmpty()) return emptyList()
        return candidates
            .groupBy { it.text }
            .map { (text, group) -> Candidate(text, group.maxOf { it.score }) }
            .sortedByDescending { it.score }
    }
}
