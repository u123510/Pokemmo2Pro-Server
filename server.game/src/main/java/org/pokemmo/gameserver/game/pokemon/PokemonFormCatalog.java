package org.pokemmo.gameserver.game.pokemon;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 宝可梦多形态与地区形态目录。
 * <p>
 * 提供全国图鉴中拥有形态差异的宝可梦形态编号与其对应的中文形态名称，
 * 供 GM 命令查询、形态展示及切换参考。
 */
public final class PokemonFormCatalog {
    private static final Map<Integer, Map<Integer, String>> FORM_MAP = new LinkedHashMap<>();

    static {
        // --- 地区形态 (阿罗拉 / 伽勒尔 / 洗翠) ---
        register(19, form(0, "关都形态(一般)", 1, "阿罗拉形态(恶/一般)")); // 小拉达
        register(20, form(0, "关都形态(一般)", 1, "阿罗拉形态(恶/一般)")); // 拉达
        register(26, form(0, "关都形态(电)", 1, "阿罗拉形态(电/超能力)")); // 雷丘
        register(27, form(0, "关都形态(地面)", 1, "阿罗拉形态(冰/钢)")); // 穿山鼠
        register(28, form(0, "关都形态(地面)", 1, "阿罗拉形态(冰/钢)")); // 穿山王
        register(37, form(0, "关都形态(火)", 1, "阿罗拉形态(冰)")); // 六尾
        register(38, form(0, "关都形态(火)", 1, "阿罗拉形态(冰/妖精)")); // 九尾
        register(50, form(0, "关都形态(地面)", 1, "阿罗拉形态(地面/钢)")); // 地鼠
        register(51, form(0, "关都形态(地面)", 1, "阿罗拉形态(地面/钢)")); // 三地鼠
        register(52, form(0, "关都形态(一般)", 1, "阿罗拉形态(恶)", 2, "伽勒尔形态(钢)")); // 喵喵
        register(53, form(0, "关都形态(一般)", 1, "阿罗拉形态(恶)")); // 猫老大
        register(58, form(0, "关都形态(火)", 1, "洗翠形态(火/岩石)")); // 卡蒂狗
        register(59, form(0, "关都形态(火)", 1, "洗翠形态(火/岩石)")); // 风速狗
        register(74, form(0, "关都形态(岩石/地面)", 1, "阿罗拉形态(岩石/电)")); // 小拳石
        register(75, form(0, "关都形态(岩石/地面)", 1, "阿罗拉形态(岩石/电)")); // 隆隆石
        register(76, form(0, "关都形态(岩石/地面)", 1, "阿罗拉形态(岩石/电)")); // 隆隆岩
        register(77, form(0, "关都形态(火)", 1, "伽勒尔形态(超能力)")); // 小火马
        register(78, form(0, "关都形态(火)", 1, "伽勒尔形态(超能力/妖精)")); // 烈焰马
        register(79, form(0, "关都形态(水/超能力)", 1, "伽勒尔形态(超能力)")); // 呆呆兽
        register(80, form(0, "关都形态(水/超能力)", 1, "伽勒尔形态(毒/超能力)")); // 呆壳兽
        register(83, form(0, "关都形态(一般/飞行)", 1, "伽勒尔形态(格斗)")); // 大葱鸭
        register(88, form(0, "关都形态(毒)", 1, "阿罗拉形态(毒/恶)")); // 臭泥
        register(89, form(0, "关都形态(毒)", 1, "阿罗拉形态(毒/恶)")); // 臭臭泥
        register(100, form(0, "关都形态(电)", 1, "洗翠形态(电/草)")); // 霹雳电球
        register(101, form(0, "关都形态(电)", 1, "洗翠形态(电/草)")); // 顽皮雷弹
        register(103, form(0, "关都形态(草/超能力)", 1, "阿罗拉形态(草/龙)")); // 椰蛋树
        register(105, form(0, "关都形态(地面)", 1, "阿罗拉形态(火/幽灵)")); // 嘎啦嘎啦
        register(110, form(0, "关都形态(毒)", 1, "伽勒尔形态(毒/妖精)")); // 双弹瓦斯
        register(122, form(0, "关都形态(超能力/妖精)", 1, "伽勒尔形态(冰/超能力)")); // 魔墙傀儡
        register(144, form(0, "关都形态(冰/飞行)", 1, "伽勒尔形态(超能力/飞行)")); // 急冻鸟
        register(145, form(0, "关都形态(电/飞行)", 1, "伽勒尔形态(格斗/飞行)")); // 闪电鸟
        register(146, form(0, "关都形态(火/飞行)", 1, "伽勒尔形态(恶/飞行)")); // 火焰鸟
        register(157, form(0, "城都形态(火)", 1, "洗翠形态(火/幽灵)")); // 火暴兽
        register(199, form(0, "城都形态(水/超能力)", 1, "伽勒尔形态(毒/超能力)")); // 呆呆王
        register(211, form(0, "城都形态(水/毒)", 1, "洗翠形态(恶/毒)")); // 千针鱼
        register(215, form(0, "城都形态(恶/冰)", 1, "洗翠形态(格斗/毒)")); // 狃拉
        register(222, form(0, "城都形态(水/岩石)", 1, "伽勒尔形态(幽灵)")); // 太阳珊瑚
        register(263, form(0, "丰缘形态(一般)", 1, "伽勒尔形态(恶/一般)")); // 蛇纹熊
        register(264, form(0, "丰缘形态(一般)", 1, "伽勒尔形态(恶/一般)")); // 直冲熊
        register(503, form(0, "合众形态(水)", 1, "洗翠形态(水/恶)")); // 大剑鬼
        register(554, form(0, "合众形态(火)", 1, "伽勒尔形态(冰)")); // 火红不倒翁
        register(562, form(0, "合众形态(幽灵)", 1, "伽勒尔形态(地面/幽灵)")); // 哭哭面具
        register(570, form(0, "合众形态(恶)", 1, "洗翠形态(一般/幽灵)")); // 索罗亚
        register(571, form(0, "合众形态(恶)", 1, "洗翠形态(一般/幽灵)")); // 索罗亚克
        register(618, form(0, "合众形态(地面/电)", 1, "伽勒尔形态(地面/钢)")); // 泥巴鱼
        register(628, form(0, "合众形态(一般/飞行)", 1, "洗翠形态(超能力/飞行)")); // 勇士雄鹰
        register(705, form(0, "卡洛斯形态(龙)", 1, "洗翠形态(钢/龙)")); // 黏美儿
        register(706, form(0, "卡洛斯形态(龙)", 1, "洗翠形态(钢/龙)")); // 黏美龙
        register(713, form(0, "卡洛斯形态(冰)", 1, "洗翠形态(冰/岩石)")); // 冰岩怪
        register(724, form(0, "阿罗拉形态(草/幽灵)", 1, "洗翠形态(草/格斗)")); // 狙射树枭

        // --- 特殊多形态与神兽形态 ---
        register(351, form(0, "普通形态", 1, "太阳形态(火)", 2, "雨水形态(水)", 3, "雪云形态(冰)")); // 飘浮泡泡
        register(386, form(0, "普通形态", 1, "攻击形态", 2, "防御形态", 3, "速度形态")); // 代欧奇希斯
        register(412, form(0, "草木蓑衣", 1, "沙地蓑衣", 2, "垃圾蓑衣")); // 结草儿
        register(413, form(0, "草木蓑衣", 1, "沙地蓑衣", 2, "垃圾蓑衣")); // 结草贵妇
        register(421, form(0, "阴天形态", 1, "晴天形态")); // 樱花儿
        register(422, form(0, "西海(粉色)", 1, "东海(蓝色)")); // 无壳海兔
        register(423, form(0, "西海(粉色)", 1, "东海(蓝色)")); // 海兔兽
        register(479, form(0, "普通洛托姆(电/幽灵)", 1, "加热洛托姆/微波炉(电/火)", 2, "清洗洛托姆/洗衣机(电/水)",
                3, "结冰洛托姆/冰箱(电/冰)", 4, "旋转洛托姆/电风扇(电/飞)", 5, "切割洛托姆/除草机(电/草)")); // 洛托姆
        register(487, form(0, "别种形态", 1, "起源形态")); // 骑拉帝纳
        register(492, form(0, "陆上形态", 1, "天空形态")); // 谢米
        register(493, form(
                0, "一般形态(天青石板/无石板)",
                1, "格斗形态(拳头石板)",
                2, "飞行形态(青空石板)",
                3, "毒形态(剧毒石板)",
                4, "地面形态(大地石板)",
                5, "岩石形态(岩石石板)",
                6, "虫形态(玉虫石板)",
                7, "幽灵形态(幽灵石板)",
                8, "钢形态(钢铁石板)",
                9, "火形态(火球石板)",
                10, "水形态(水滴石板)",
                11, "草形态(碧绿石板)",
                12, "电形态(雷电石板)",
                13, "超能力形态(神奇石板)",
                14, "冰形态(冰冻石板)",
                15, "龙形态(龙之石板)",
                16, "恶形态(恶之石板)",
                17, "妖精形态(妖精石板)")); // 阿尔宙斯
        register(550, form(0, "红条纹的样子", 1, "蓝条纹的样子", 2, "白条纹的样子")); // 野蛮鲈鱼
        register(555, form(0, "普通模式", 1, "达摩模式", 2, "伽勒尔普通", 3, "伽勒尔达摩")); // 达摩狒狒
        register(585, form(0, "春天的样子", 1, "夏天的样子", 2, "秋天的样子", 3, "冬天的样子")); // 四季鹿
        register(586, form(0, "春天的样子", 1, "夏天的样子", 2, "秋天的样子", 3, "冬天的样子")); // 萌芽鹿
        register(641, form(0, "化身形态", 1, "灵兽形态")); // 龙卷云
        register(642, form(0, "化身形态", 1, "灵兽形态")); // 雷电云
        register(645, form(0, "化身形态", 1, "灵兽形态")); // 土地云
        register(646, form(0, "普通形态", 1, "暗黑酋雷姆", 2, "焰白酋雷姆")); // 酋雷姆
        register(647, form(0, "平凡形态", 1, "觉悟形态")); // 凯路迪欧
        register(648, form(0, "歌声形态", 1, "舞步形态")); // 美洛耶塔
        register(718, form(0, "50%形态", 1, "10%形态", 2, "完全体形态")); // 基格尔德
        register(720, form(0, "惩戒形态", 1, "解放形态")); // 胡帕
        register(741, form(0, "热辣热辣风格", 1, "扑哧扑哧风格", 2, "呼拉呼拉风格", 3, "买买风格")); // 花舞鸟
        register(745, form(0, "白昼的样子", 1, "黑夜的样子", 2, "黄昏的样子")); // 鬃岩狼人
        register(746, form(0, "单独的样子", 1, "鱼群的样子")); // 弱丁鱼
        register(774, form(0, "流星的样子", 1, "红核心", 2, "橙核心", 3, "黄核心", 4, "绿核心", 5, "蓝核心", 6, "靛核心", 7, "紫核心")); // 小陨星
        register(778, form(0, "伪装的样子", 1, "现形的样子")); // 谜拟丘
        register(800, form(0, "普通形态", 1, "黄昏之鬃", 2, "拂晓之翼", 3, "究极奈克洛兹玛")); // 奈克洛兹玛
        register(888, form(0, "百战勇者", 1, "剑之王")); // 苍响
        register(889, form(0, "百战勇者", 1, "盾之王")); // 藏玛然特
        register(890, form(0, "普通形态", 1, "无极巨化")); // 无极汰那
        register(892, form(0, "一击流", 1, "连击流")); // 武道熊师
        register(898, form(0, "普通形态", 1, "白马骑士", 2, "黑马骑士")); // 蕾冠王
        register(1017, form(0, "碧草面具", 1, "水井面具", 2, "火灶面具", 3, "础石面具")); // 厄诡椪
        register(1024, form(0, "普通形态", 1, "太晶形态", 2, "星晶形态")); // 太乐巴戈斯
    }

    private PokemonFormCatalog() {
    }

    private static void register(int speciesId, Map<Integer, String> forms) {
        FORM_MAP.put(speciesId, forms);
    }

    private static Map<Integer, String> form(Object... kvs) {
        Map<Integer, String> map = new LinkedHashMap<>();
        for (int i = 0; i < kvs.length; i += 2) {
            map.put((Integer) kvs[i], (String) kvs[i + 1]);
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * 获取指定物种已知的形态列表字典（形态编号 -> 中文形态名称）。
     * 若未在预置列表中，返回空字典。
     */
    public static Map<Integer, String> getKnownForms(int speciesIndexId) {
        Map<Integer, String> forms = FORM_MAP.get(speciesIndexId);
        return forms != null ? forms : Collections.emptyMap();
    }

    /**
     * 获取指定物种在特定形态编号下的显示名称。
     * 若无特殊命名，形态 0 显示为“默认形态”，其他显示为“形态 {formType}”。
     */
    public static String getFormName(int speciesIndexId, int formType) {
        Map<Integer, String> forms = FORM_MAP.get(speciesIndexId);
        if (forms != null && forms.containsKey(formType)) {
            return forms.get(formType);
        }
        return formType == 0 ? "默认形态" : ("形态 " + formType);
    }
}
