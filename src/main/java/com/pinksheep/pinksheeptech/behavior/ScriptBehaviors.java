package com.pinksheep.pinksheeptech.behavior;

import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 把粉羊科技 RSC 版 scripts/ 下的 5 个 JS 脚本改写为原生 Java 行为（对应 items.yml 的
 * {@code script} 字段）。与原脚本逐函数对照：
 *
 * <ul>
 *   <li>{@code ikun} —— 10 秒冷却；冷却中改标题/lore 提示剩余秒数；就绪时让周围 3 格内
 *       未成年鸡立即长大。原 JS 用脚本级全局 {@code lastUseTime}（全体玩家共享一把冷却锁），
 *       独立版改为按玩家分别计时（更合理，且避免多人互相顶冷却）。</li>
 *   <li>{@code 格调生成器} —— 面朝方向按翻转图案立放 5 块黑染色玻璃，被占用则不动，
 *       成功后消耗 1 个（对应 {@code GE_DIAO}）。</li>
 *   <li>{@code 经验交互器} —— 经验等级 +2，80 级封顶（超限仅提示）；成功加级后播放
 *       {@code ENTITY_PLAYER_LEVELUP} 音效。</li>
 *   <li>{@code 经验交互器Plus} —— 经验等级 +20，1000 万级封顶（超限仅提示）；成功加级后
 *       同样播放 {@code ENTITY_PLAYER_LEVELUP} 音效。</li>
 *   <li>{@code 种子搜寻} —— 副手不得持有粘液物品；消耗 1 个后展示当前世界种子
 *       （原 JS 以 OP 执行 {@code minecraft:seed}，等价改为直接读种子，不提权更安全）。</li>
 * </ul>
 */
public final class ScriptBehaviors {

    private ScriptBehaviors() {}

    /** ikun 冷却表：玩家 -> 上次成功使用时间戳(ms)。 */
    private static final Map<UUID, Long> IKUN_COOLDOWN = new ConcurrentHashMap<>();

    private static final long IKUN_COOLDOWN_MS = 10_000L;

    /** 按 yml 的 {@code script} 名称返回对应行为，未实现返回 null。 */
    public static ItemUseHandler forName(String script) {
        if (script == null) return null;
        switch (script) {
            case "ikun":           return ScriptBehaviors::ikun;
            case "格调生成器":      return ScriptBehaviors::diao;
            case "经验交互器":      return ScriptBehaviors::exp;
            case "经验交互器Plus":   return ScriptBehaviors::expPlus;
            case "种子搜寻":        return ScriptBehaviors::seed;
            default:                return null;
        }
    }

    // ------------------------------------------------------------------ ikun

    private static void ikun(PlayerRightClickEvent e) {
        e.cancel();
        Player player = e.getPlayer();
        long now = System.currentTimeMillis();
        Long last = IKUN_COOLDOWN.get(player.getUniqueId());

        if (last != null && now - last < IKUN_COOLDOWN_MS) {
            int remaining = (int) Math.ceil((IKUN_COOLDOWN_MS - (now - last)) / 1000.0);
            player.sendTitle("§c§l你干嘛啊~哎呦！", "冷却剩余时间： " + remaining + "秒", 0, 10, 0);

            // 与原 JS 一致：把主手物品 lore 末尾两条去掉后追加剩余时间提示
            ItemStack item = player.getInventory().getItemInMainHand();
            ItemMeta meta = item != null ? item.getItemMeta() : null;
            List<String> lore = meta != null ? meta.getLore() : null;
            if (lore != null && lore.size() >= 2) {
                List<String> next = new java.util.ArrayList<>(lore.subList(0, lore.size() - 2));
                next.add("§9§l冷却剩余时间 : " + remaining);
                meta.setLore(next);
                item.setItemMeta(meta);
            }
            return;
        }

        IKUN_COOLDOWN.put(player.getUniqueId(), now);

        // 周围 3 格内的未成年鸡立刻长大
        for (org.bukkit.entity.Entity entity : player.getNearbyEntities(3, 3, 3)) {
            if (entity instanceof Chicken chicken && !chicken.isAdult()) {
                chicken.setAdult();
            }
        }
    }

    // ------------------------------------------------------------ 格调生成器

    /** 上下翻转后的立式图案；行对应 i（-1..1，y 偏移），列对应 j（-1..1，左手方向偏移）。 */
    private static final int[][] DIAO_PATTERN = {
            {1, 0, 1}, // 最高层（原底部）
            {0, 1, 0},
            {0, 1, 0}  // 最低层（原顶部）
    };

    private static void diao(PlayerRightClickEvent e) {
        e.cancel();
        Player player = e.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) return;

        SlimefunItem sfItem = SlimefunItem.getByItem(item);
        if (sfItem == null || !"GE_DIAO".equals(sfItem.getId())) return;

        org.bukkit.Location loc = player.getLocation();
        double dx = loc.getDirection().getX();
        double dz = loc.getDirection().getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.001) return;
        dx /= len;
        dz /= len;

        // 视线方向的左手侧向量
        double leftX = -dz;
        double leftZ = dx;

        World world = player.getWorld();
        int baseY = loc.getBlockY() + 1;
        int centerX = loc.getBlockX() + (int) Math.round(dx);
        int centerZ = loc.getBlockZ() + (int) Math.round(dz);

        // 先检查放置位置是否被占用（任意一格非空则整体放弃，与原 JS 一致）
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                if (DIAO_PATTERN[i + 1][j + 1] != 1) continue;
                Block block = world.getBlockAt(
                        centerX + (int) Math.round(leftX * j),
                        baseY + i,
                        centerZ + (int) Math.round(leftZ * j));
                if (!block.isEmpty()) return;
            }
        }

        // 放置黑染色玻璃
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                if (DIAO_PATTERN[i + 1][j + 1] != 1) continue;
                world.getBlockAt(
                        centerX + (int) Math.round(leftX * j),
                        baseY + i,
                        centerZ + (int) Math.round(leftZ * j))
                        .setType(Material.BLACK_STAINED_GLASS);
            }
        }

        // 消耗 1 个
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }

    // ---------------------------------------------------- 经验交互器 / Plus

    private static void exp(PlayerRightClickEvent e) {
        e.cancel();
        Player player = e.getPlayer();
        int level = player.getLevel();
        Location loc = player.getLocation();
        if (level >= 80) {
            player.sendMessage("☢ 经过pinksheepYTG检测您已超出上限等级 ☢");
        } else if (level <= 78) {
            player.setLevel(level + 2);
            player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        } else {
            player.setLevel(80);
            player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            player.sendMessage("☢ 经过pinksheepYTG检测您已超出上限等级 ☢");
        }
    }

    private static void expPlus(PlayerRightClickEvent e) {
        e.cancel();
        Player player = e.getPlayer();
        int level = player.getLevel();
        if (level >= 10_000_000) {
            player.sendMessage("☢ 经过pinksheepYTG检测您已超出上限等级，已超限 ☢");
        } else {
            player.setLevel(Math.min(10_000_000, level + 20));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }
    }

    // -------------------------------------------------------------- 种子搜寻

    private static void seed(PlayerRightClickEvent e) {
        e.cancel();
        Player player = e.getPlayer();

        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand != null && !offHand.getType().isAir()
                && SlimefunItem.getByItem(offHand) != null) {
            player.sendMessage("您必须使用主手查看且副手不能有粘液物品！");
            return;
        }

        // 消耗 1 个（原 JS 消耗后以 OP 执行 minecraft:seed，这里等价展示种子，不提权）
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main != null && main.getAmount() > 0) {
            if (main.getAmount() > 1) {
                main.setAmount(main.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        }

        long s = player.getWorld().getSeed();
        player.sendMessage("§a当前世界种子: §e" + s);
    }

    // ------------------------------------------------- 按钮组动作（groups.yml）

    /**
     * 处理 groups.yml 按钮组的 {@code actions}（对应 RSC {@code BaseRSCItemGroup.readAction}）。
     * 目前内容文件只用到 {@code link}；其余动作按 RSC 同语义实现。
     */
    public static void runGroupAction(Player p, String action) {
        if (action == null) return;
        String[] parts = action.split(" ", 2);
        if (parts.length != 2) return;
        String type = parts[0];
        String content = parts[1];
        switch (type) {
            case "link" -> {
                p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&e单击此处打开链接: "));
                TextComponent link = new TextComponent(content);
                link.setColor(ChatColor.GRAY);
                link.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new Text(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&e" + content))));
                link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, content));
                p.spigot().sendMessage(link);
            }
            case "console" -> {
                org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(),
                        content.replace("%player%", p.getName()));
            }
            case "open_itemgroup" -> {
                String[] seg = content.split(":");
                if (seg.length < 2) return;
                int page = seg.length > 2 ? parseIntOrDefault(seg[2], 1) : 1;
                var profileOpt = io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile.find(p);
                if (profileOpt.isEmpty()) return;
                var profile = profileOpt.get();
                for (io.github.thebusybiscuit.slimefun4.api.items.ItemGroup group :
                        io.github.thebusybiscuit.slimefun4.implementation.Slimefun.getRegistry().getAllItemGroups()) {
                    if (group.getKey().getNamespace().equals(seg[0])
                            && group.getKey().getKey().equals(seg[1])) {
                        var impl = io.github.thebusybiscuit.slimefun4.implementation.Slimefun.getRegistry()
                                .getSlimefunGuide(io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide.getDefaultMode());
                        impl.openItemGroup(profile, group, page);
                        return;
                    }
                }
            }
            case "display_slimefunitem" -> {
                var profileOpt = io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile.find(p);
                if (profileOpt.isEmpty()) return;
                SlimefunItem item = SlimefunItem.getById(content);
                if (item == null) return;
                var impl = io.github.thebusybiscuit.slimefun4.implementation.Slimefun.getRegistry()
                        .getSlimefunGuide(io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide.getDefaultMode());
                impl.displayItem(profileOpt.get(), item, true);
            }
            default -> { /* 未知动作类型：与 RSC 一致忽略 */ }
        }
    }

    private static int parseIntOrDefault(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException ex) {
            return def;
        }
    }
}
