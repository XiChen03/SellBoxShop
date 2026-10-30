package com.sellbox.mod.util;

import java.util.HashMap;
import java.util.Map;

public class PinyinUtil {
    private static final Map<Character, String> PINYIN = new HashMap<>();

    static {
        // 高频汉字拼音映射（覆盖 vanilla + 常见模组物品名）
        // 矿物/材料
        put('矿', "kuang"); put('石', "shi"); put('锭', "ding"); put('块', "kuai");
        put('铁', "tie"); put('金', "jin"); put('铜', "tong"); put('煤', "mei");
        put('炭', "tan"); put('钻', "zuan"); put('宝', "bao"); put('红', "hong");
        put('绿', "lv"); put('青', "qing"); put('蓝', "lan"); put('紫', "zi");
        put('白', "bai"); put('黑', "hei"); put('灰', "hui"); put('棕', "zong");
        put('橙', "cheng"); put('黄', "huang"); put('粉', "fen"); put('银', "yin");
        put('原', "yuan"); put('粗', "cu"); put('粒', "li"); put('粉', "mo");
        put('钢', "gang"); put('铝', "lv"); put('铅', "qian"); put('镍', "nie");
        put('锡', "xi"); put('锌', "xin"); put('铀', "you"); put('钛', "tai");
        put('钨', "wu"); put('铂', "bo"); put('锂', "li"); put('钠', "na");
        put('镁', "mei"); put('硅', "gui"); put('硫', "liu"); put('磷', "lin");
        put('硝', "xiao"); put('盐', "yan"); put('皮', "pi"); put('革', "ge");
        put('橡', "xiang"); put('胶', "jiao"); put('塑', "su"); put('料', "liao");
        put('陶', "tao"); put('瓷', "ci"); put('玻', "bo"); put('璃', "li");
        put('纤', "xian"); put('维', "wei"); put('混', "hun"); put('凝', "ning");
        put('土', "tu"); put('水', "shui"); put('泥', "ni"); put('水', "shui");
        put('晶', "jing"); put('碎', "sui"); put('片', "pian"); put('末', "mo");
        put('尘', "chen"); put('渣', "zha"); put('屑', "xie"); put('板', "ban");
        put('杆', "gan"); put('柱', "zhu"); put('框', "kuang"); put('架', "jia");
        put('齿', "chi"); put('轮', "lun"); put('轴', "zhou"); put('承', "cheng");
        put('螺', "luo"); put('栓', "shuan"); put('母', "mu"); put('钉', "ding");
        put('弹', "dan"); put('簧', "huang"); put('电', "dian"); put('路', "lu");
        put('板', "ban"); put('线', "xian"); put('缆', "lan"); put('池', "chi");
        put('机', "ji"); put('器', "qi"); put('械', "xie"); put('壳', "ke");
        put('管', "guan"); put('道', "dao"); put('阀', "fa"); put('泵', "beng");
        put('炉', "lu"); put('锅', "guo"); put('容', "rong"); put('液', "ye");
        put('体', "ti"); put('气', "qi"); put('蒸', "zheng"); put('汽', "qi");
        put('废', "fei"); put('料', "liao");
        // 自然方块
        put('草', "cao"); put('花', "hua"); put('树', "shu"); put('叶', "ye");
        put('藤', "teng"); put('蔓', "man"); put('菇', "gu"); put('苗', "miao");
        put('种', "zhong"); put('子', "zi"); put('耕', "geng"); put('地', "di");
        put('雪', "xue"); put('冰', "bing"); put('霜', "shuang"); put('珊', "shan");
        put('瑚', "hu"); put('礁', "jiao"); put('块', "kuai"); put('海', "hai");
        put('泡', "pao"); put('棉', "mian"); put('粘', "nian"); put('液', "ye");
        put('蜜', "mi"); put('脾', "pi"); put('干', "gan"); put('海', "hai");
        put('带', "dai"); put('蘑', "mo"); put('菇', "gu"); put('木', "mu");
        put('岩', "yan"); put('浆', "jiang"); put('沙', "sha"); put('黏', "nian");
        put('球', "qiu"); put('绳', "sheng"); put('羽', "yu"); put('箭', "jian");
        put('弓', "gong"); put('剑', "jian"); put('斧', "fu"); put('镐', "gao");
        put('锹', "qiao"); put('锄', "chu"); put('头', "tou"); put('甲', "jia");
        put('裤', "ku"); put('靴', "xue"); put('鞋', "xie"); put('帽', "mao");
        put('盔', "kui");
        // 生物
        put('皮', "pi"); put('革', "ge"); put('毛', "mao"); put('角', "jiao");
        put('牙', "ya"); put('爪', "zhao"); put('眼', "yan"); put('珠', "zhu");
        put('魂', "hun"); put('骨', "gu"); put('蜘', "zhi"); put('蛛', "zhu");
        put('蠹', "du"); put('虫', "chong"); put('蝙', "bian"); put('蝠', "fu");
        put('鱿', "you"); put('龟', "gui"); put('豚', "tun"); put('鲑', "gui");
        put('鳕', "xue"); put('河', "he"); put('豚', "tun"); put('狐', "hu");
        put('狸', "li"); put('熊', "xiong"); put('猫', "mao"); put('骆', "luo");
        put('驼', "tuo"); put('羊', "yang"); put('商', "shang"); put('人', "ren");
        put('流', "liu"); put('浪', "lang"); put('者', "zhe"); put('村', "cun");
        put('民', "min"); put('卫', "wei"); put('士', "shi"); put('掠', "lue");
        put('夺', "duo"); put('女', "nv"); put('巫', "wu"); put('唤', "huan");
        put('魔', "mo"); put('恼', "nao"); put('鬼', "gui"); put('幻', "huan");
        put('术', "shu"); put('师', "shi"); put('末', "mo"); put('影', "ying");
        put('洞', "dong"); put('穴', "xue"); put('骸', "hai"); put('凋', "diao");
        put('灵', "ling"); put('僵', "jiang"); put('尸', "shi"); put('骷', "ku");
        put('髅', "lou"); put('爬', "pa"); put('行', "xing"); put('苦', "ku");
        put('力', "li"); put('帕', "pa"); put('斯', "si"); put('史', "shi");
        put('莱', "lai"); put('姆', "mu"); put('道', "dao"); put('潜', "qian");
        put('贝', "bei"); put('龙', "long"); put('蛋', "dan"); put('蜂', "feng");
        put('蜜', "mi"); put('蜂', "feng"); put('窝', "wo"); put('蜂', "feng");
        // 红石/机械
        put('红', "hong"); put('火', "huo"); put('把', "ba"); put('中', "zhong");
        put('继', "ji"); put('比', "bi"); put('较', "jiao"); put('观', "guan");
        put('察', "cha"); put('侦', "zhen"); put('测', "ce"); put('雷', "lei");
        put('达', "da"); put('标', "biao"); put('靶', "ba"); put('点', "dian");
        put('钟', "zhong"); put('拉', "la"); put('按', "an"); put('钮', "niu");
        put('压', "ya"); put('踏', "ta"); put('绞', "jiao"); put('链', "lian");
        put('轨', "gui"); put('激', "ji"); put('活', "huo"); put('铁', "tie");
        put('活', "huo"); put('塞', "sai"); put('粘', "zhan"); put('性', "xing");
        put('侦', "zhen"); put('测', "ce"); put('阳', "yang"); put('极', "ji");
        put('阴', "yin"); put('极', "ji"); put('合', "he"); put('成', "cheng");
        put('工', "gong"); put('作', "zuo"); put('熔', "rong"); put('炉', "lu");
        put('切', "qie"); put('织', "zhi"); put('布', "bu"); put('酿', "niang");
        put('造', "zao"); put('炼', "lian"); put('药', "yao"); put('锅', "guo");
        put('漏', "lou"); put('斗', "dou"); put('投', "tou"); put('掷', "zhi");
        put('发', "fa"); put('射', "she"); put('音', "yin"); put('符', "fu");
        put('盒', "he"); put('唱', "chang"); put('片', "pian"); put('末', "mo");
        put('地', "di"); put('箱', "xiang"); put('陷', "xian"); put('阱', "jing");
        put('锻', "duan"); put('造', "zao"); put('台', "tai"); put('砂', "sha");
        // 武器/工具/装备
        put('弓', "gong"); put('剑', "jian"); put('斧', "fu"); put('镐', "gao");
        put('锹', "qiao"); put('锄', "chu"); put('弩', "nu"); put('盾', "dun");
        put('三', "san"); put('叉', "cha"); put('戟', "ji"); put('钓', "diao");
        put('竿', "gan"); put('鞭', "bian"); put('刀', "dao"); put('刃', "ren");
        put('锤', "chui"); put('杖', "zhang"); put('法', "fa"); put('杖', "zhang");
        put('头', "tou"); put('盔', "kui"); put('甲', "jia"); put('胸', "xiong");
        put('腿', "tui"); put('靴', "xue"); put('鞋', "xie"); put('护', "hu");
        put('腿', "tui"); put('靴', "xue"); put('子', "zi"); put('裤', "ku");
        // 食物
        put('食', "shi"); put('物', "wu"); put('果', "guo"); put('猪', "zhu");
        put('牛', "niu"); put('羊', "yang"); put('鸡', "ji"); put('兔', "tu");
        put('鱼', "yu"); put('肉', "rou"); put('熟', "shou"); put('生', "sheng");
        put('面', "mian"); put('包', "bao"); put('饼', "bing"); put('干', "gan");
        put('蛋', "dan"); put('糕', "gao"); put('南', "nan"); put('瓜', "gua");
        put('派', "pai"); put('豆', "dou"); put('萝', "luo"); put('卜', "bo");
        put('甜', "tian"); put('菜', "cai"); put('根', "gen"); put('汤', "tang");
        put('奶', "nai"); put('骨', "gu"); put('饼', "bing"); put('干', "gan");
        put('曲', "qu"); put('奇', "qi"); put('巧', "qiao"); put('克', "ke");
        put('力', "li"); put('糖', "tang"); put('果', "guo"); put('酱', "jiang");
        put('料', "liao"); put('油', "you"); put('黄', "huang"); put('油', "you");
        put('奶', "nai"); put('酪', "lao");
        // 附魔/状态
        put('效', "xiao"); put('率', "lv"); put('时', "shi"); put('运', "yun");
        put('精', "jing"); put('准', "zhun"); put('财', "cai"); put('富', "fu");
        put('经', "jing"); put('验', "yan"); put('修', "xiu"); put('补', "bu");
        put('耐', "nai"); put('久', "jiu"); put('锋', "feng"); put('利', "li");
        put('灵', "ling"); put('魂', "hun"); put('绑', "bang"); put('定', "ding");
        put('穿', "chuan"); put('刺', "ci"); put('退', "tui"); put('矢', "shi");
        put('燃', "ran"); put('烧', "shao"); put('保', "bao"); put('护', "hu");
        put('弹', "dan"); put('射', "she"); put('灾', "zai"); put('防', "fang");
        put('爆', "bao"); put('炸', "zha"); put('水', "shui"); put('下', "xia");
        put('呼', "hu"); put('吸', "xi"); put('速', "su"); put('度', "du");
        put('冰', "bing"); put('霜', "shuang"); put('荆', "jing"); put('棘', "ji");
        put('沉', "chen"); put('重', "zhong"); put('坠', "zhui"); put('落', "luo");
        put('减', "jian"); put('缓', "huan"); put('慢', "man"); put('中', "zhong");
        put('毒', "du"); put('凋', "diao"); put('零', "ling"); put('黯', "an");
        put('灭', "mie"); put('强', "qiang"); put('化', "hua");
        // 数字/方向/颜色
        put('一', "yi"); put('二', "er"); put('三', "san"); put('四', "si");
        put('五', "wu"); put('六', "liu"); put('七', "qi"); put('八', "ba");
        put('九', "jiu"); put('十', "shi"); put('百', "bai"); put('千', "qian");
        put('万', "wan"); put('上', "shang"); put('下', "xia"); put('左', "zuo");
        put('右', "you"); put('前', "qian"); put('后', "hou"); put('东', "dong");
        put('南', "nan"); put('西', "xi"); put('北', "bei"); put('中', "zhong");
        put('正', "zheng"); put('负', "fu"); put('大', "da"); put('小', "xiao");
        put('长', "chang"); put('短', "duan"); put('高', "gao"); put('低', "di");
        put('深', "shen"); put('浅', "qian"); put('厚', "hou"); put('薄', "bo");
        put('红', "hong"); put('橙', "cheng"); put('黄', "huang"); put('绿', "lv");
        put('青', "qing"); put('蓝', "lan"); put('紫', "zi"); put('黑', "hei");
        put('白', "bai"); put('灰', "hui"); put('棕', "zong"); put('粉', "fen");
        put('透', "tou"); put('明', "ming"); put('闪', "shan"); put('亮', "liang");
        put('暗', "an"); put('淡', "dan"); put('深', "shen");
        // 杂项/常用
        put('颗', "ke"); put('个', "ge"); put('条', "tiao"); put('张', "zhang");
        put('面', "mian"); put('扇', "shan"); put('组', "zu"); put('总', "zong");
        put('价', "jia"); put('单', "dan"); put('位', "wei"); put('数', "shu");
        put('量', "liang"); put('已', "yi"); put('未', "wei"); put('全', "quan");
        put('部', "bu"); put('搜', "sou"); put('索', "suo"); put('排', "pai");
        put('序', "xu"); put('名', "ming"); put('称', "cheng"); put('字', "mu");
        put('过', "guo"); put('滤', "lv"); put('选', "xuan"); put('择', "ze");
        put('清', "qing"); put('除', "chu"); put('应', "ying"); put('用', "yong");
        put('显', "xian"); put('示', "shi"); put('隐', "yin"); put('藏', "cang");
        put('添', "tian"); put('加', "jia"); put('删', "shan"); put('改', "gai");
        put('编', "bian"); put('辑', "ji"); put('复', "fu"); put('制', "zhi");
        put('粘', "zhan"); put('贴', "tie"); put('切', "qie"); put('换', "huan");
        put('移', "yi"); put('动', "dong"); put('复', "fu"); put('制', "zhi");
        put('粘', "zhan"); put('贴', "tie"); put('移', "yi"); put('动', "dong");
        put('分', "fen"); put('类', "lei"); put('按', "an"); put('钮', "niu");
        put('页', "ye"); put('面', "mian"); put('窗', "chuang"); put('口', "kou");
        put('列', "lie"); put('表', "biao"); put('图', "tu"); put('标', "biao");
        put('光', "guang"); put('标', "biao"); put('指', "zhi"); put('针', "zhen");
        put('鼠', "shu"); put('标', "biao"); put('键', "jian"); put('盘', "pan");
        put('动', "dong"); put('画', "hua"); put('文', "wen"); put('本', "ben");
        put('图', "tu"); put('片', "pian"); put('字', "zi"); put('体', "ti");
        put('颜', "yan"); put('色', "se"); put('背', "bei"); put('景', "jing");
        put('前', "qian"); put('景', "jing"); put('边', "bian"); put('框', "kuang");
        put('圆', "yuan"); put('角', "jiao"); put('阴', "yin"); put('影', "ying");
        put('发', "fa"); put('光', "guang"); put('不', "bu"); put('锈', "xiu");
        put('腐', "fu"); put('蚀', "shi"); put('抗', "kang"); put('氧', "yang");
        put('化', "hua"); put('合', "he"); put('金', "jin"); put('陶', "tao");
        put('瓷', "ci"); put('复', "fu"); put('合', "he"); put('材', "cai");
        put('纳', "na"); put('米', "mi"); put('量', "liang"); put('子', "zi");
        put('聚', "ju"); put('合', "he"); put('物', "wu"); put('纺', "fang");
        put('织', "zhi"); put('品', "pin"); put('编', "bian"); put('织', "zhi");
        put('物', "wu"); put('合', "he"); put('成', "cheng"); put('纤', "xian");
        put('维', "wei"); put('碳', "tan"); put('纳', "na"); put('米', "mi");
        put('管', "guan"); put('石', "shi"); put('墨', "mo"); put('烯', "xi");
        put('金', "jin"); put('属', "shu"); put('非', "fei"); put('金', "jin");
        put('属', "shu"); put('有', "you"); put('机', "ji"); put('无', "wu");
        put('机', "ji"); put('高', "gao"); put('分', "fen"); put('子', "zi");
        put('化', "hua"); put('合', "he"); put('物', "wu"); put('元', "yuan");
        put('素', "su"); put('周', "zhou"); put('期', "qi"); put('表', "biao");
        put('原', "yuan"); put('子', "zi"); put('序', "xu"); put('号', "hao");
        put('化', "hua"); put('学', "xue"); put('式', "shi");
    }

    private static void put(char c, String pinyin) {
        PINYIN.put(c, pinyin);
    }

    public static String toPinyin(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            String py = PINYIN.get(c);
            if (py != null) {
                sb.append(py);
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    public static String getFirstLetterString(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            String py = PINYIN.get(c);
            if (py != null && !py.isEmpty()) {
                sb.append(py.charAt(0));
            } else if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z') {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    public static boolean matches(String text, String query) {
        if (text == null || query == null) return false;
        String lowerQuery = query.toLowerCase();
        String lowerText = text.toLowerCase();
        if (lowerText.contains(lowerQuery)) return true;
        String pinyin = toPinyin(text);
        if (pinyin.contains(lowerQuery)) return true;
        String firstLetters = getFirstLetterString(text);
        return firstLetters.contains(lowerQuery);
    }

    public static char getFirstLetter(char c) {
        String py = PINYIN.get(c);
        if (py != null && !py.isEmpty()) {
            return py.charAt(0);
        }
        if (c >= 'a' && c <= 'z') return c;
        if (c >= 'A' && c <= 'Z') return Character.toLowerCase(c);
        return c;
    }
}
