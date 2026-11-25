package com.pinksheep.pinksheeptech.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 颜色解析，对应 RSC 的 {@code CMIChatColor.translate}。
 *
 * <p>支持四种写法（按 RSC 的处理顺序依次展开）：</p>
 * <ol>
 *   <li>{@code {#RRGGBB}} —— CMI 花括号风格；</li>
 *   <li>{@code &amp;#RRGGBB} —— “quirky”风格；</li>
 *   <li>{@code #RRGGBB} —— 裸十六进制（Spigot 官方风格，粉羊科技大量使用，
 *       例如 FSQZ 的 {@code #c4d9af&l附}）；</li>
 *   <li>{@code &x} 传统色码，最后统一交由
 *       {@link org.bukkit.ChatColor#translateAlternateColorCodes} 处理
 *       （只替换 {@code &} 后跟合法色码字符的情况，普通 {@code &} 原样保留，
 *       如 menus.yml 光暗矿机标题里的 “&amp;暗”）。</li>
 * </ol>
 *
 * <p>与 RSC 一致：花括号形式先展开，因此其中的 {@code #} 不会被裸 {@code #} 规则
 * 重复处理；非法超长串（如 {@code #bc53722}）匹配前 6 位、余下字符按字面保留，
 * 与 RSC 正则的贪心匹配行为相同。</p>
 */
public final class Colors {

    private Colors() {}

    /** {@code {#RRGGBB}} 形式。 */
    private static final Pattern BRACED_HEX = Pattern.compile("\\{#([0-9A-Fa-f]{6})}");
    /** {@code &#RRGGBB} 形式。 */
    private static final Pattern AMP_HEX = Pattern.compile("&#([0-9A-Fa-f]{6})");
    /** 裸 {@code #RRGGBB} 形式（此时花括号形式已全部展开，无需 lookbehind）。 */
    private static final Pattern BARE_HEX = Pattern.compile("#([0-9A-Fa-f]{6})");

    /**
     * 依次展开三种十六进制色码（转为 {@code §x§r§r§g§g§b§b}），
     * 再替换传统 {@code &} 色码。顺序不可颠倒：{@code &#hex} 含 {@code &}，
     * 必须在传统替换之前处理。
     */
    public static String c(String s) {
        if (s == null || s.isEmpty()) return s;
        String r = expand(BRACED_HEX, s);
        r = expand(AMP_HEX, r);
        r = expand(BARE_HEX, r);
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', r);
    }

    public static List<String> c(List<String> list) {
        List<String> out = new ArrayList<>(list.size());
        for (String s : list) out.add(c(s));
        return out;
    }

    /** 将 hex 串展开为 {@code §x§r§r§g§g§b§b}。 */
    private static String hexToLegacy(String hex) {
        StringBuilder sb = new StringBuilder(hex.length() * 2 + 2).append('§').append('x');
        for (char ch : hex.toCharArray()) sb.append('§').append(ch);
        return sb.toString();
    }

    private static String expand(Pattern pattern, String s) {
        Matcher m = pattern.matcher(s);
        if (!m.find()) return s;
        StringBuilder sb = new StringBuilder();
        do {
            m.appendReplacement(sb, Matcher.quoteReplacement(hexToLegacy(m.group(1))));
        } while (m.find());
        m.appendTail(sb);
        return sb.toString();
    }
}
