package com.pinksheep.pinksheeptech.customs.groups;

import io.github.thebusybiscuit.slimefun4.api.items.groups.NestedItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.groups.SubItemGroup;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.guide.SurvivalSlimefunGuide;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 粉羊科技顶层嵌套组（groups.yml 的 {@code type: nested}）。
 *
 * <p>在原版 {@link NestedItemGroup} 基础上追加「按钮组」渲染：原版只渲染
 * {@link SubItemGroup}，而粉羊的 groups.yml 还有一个 {@code type: button} 的链接入口
 * （B 站休息室）。本类维护自己的内容顺序表（子组 + 按钮），重写
 * {@link #open(Player, PlayerProfile, SlimefunGuideMode)} 使按钮与子组一同分页展示，
 * 布局与原版 NestedItemGroup.openGuide 一致（GROUP_SIZE=36、翻页 46/52、返回 1）。</p>
 */
public class PSNestedGroup extends NestedItemGroup {

    /** 与原版 NestedItemGroup 一致的每页容量。 */
    private static final int GROUP_SIZE = 36;

    /** 内容顺序表：key = 组 id，value = SubItemGroup（子组）或 PSButtonGroup（按钮）。 */
    private final Map<String, Object> entries = new LinkedHashMap<>();

    public PSNestedGroup(org.bukkit.NamespacedKey key, ItemStack item) {
        super(key, item);
    }

    public PSNestedGroup(org.bukkit.NamespacedKey key, ItemStack item, int tier) {
        super(key, item, tier);
    }

    /** 登记子组（SubItemGroup 构造器已自动加入父组内部列表，这里同步进本类顺序表）。 */
    public void addSubEntry(String id, SubItemGroup group) {
        entries.put(id, group);
    }

    /** 登记按钮组（按钮无法加入原版内部列表，仅存在于本类顺序表）。 */
    public void addButtonEntry(String id, PSButtonGroup button) {
        entries.put(id, button);
    }

    @Override
    public boolean isVisible(Player p, PlayerProfile profile, SlimefunGuideMode mode) {
        for (Object e : entries.values()) {
            if (e instanceof SubItemGroup sub && sub.isVisibleInNested(p)) return true;
            if (e instanceof PSButtonGroup) return true;
        }
        return false;
    }

    @Override
    public void open(Player p, PlayerProfile profile, SlimefunGuideMode mode) {
        openPage(p, profile, mode, 1);
    }

    private void openPage(Player p, PlayerProfile profile, SlimefunGuideMode mode, int page) {
        if (mode == SlimefunGuideMode.SURVIVAL_MODE) {
            profile.getGuideHistory().add(this, page);
        }

        ChestMenu menu = new ChestMenu(Slimefun.getLocalization().getMessage(p, "guide.title.main"));
        SurvivalSlimefunGuide guide =
                (SurvivalSlimefunGuide) Slimefun.getRegistry().getSlimefunGuide(mode);

        menu.setEmptySlotsClickable(false);
        menu.addMenuOpeningHandler(SoundEffect.GUIDE_BUTTON_CLICK_SOUND::playFor);
        guide.createHeader(p, profile, menu);

        menu.addItem(1, new CustomItemStack(ChestMenuUtils.getBackButton(
                p, "", ChatColor.GRAY + Slimefun.getLocalization().getMessage(p, "guide.back.guide"))));
        menu.addMenuClickHandler(1, (pl, s, is, action) -> {
            SlimefunGuide_openMainMenu(profile, mode);
            return false;
        });

        // 仅渲染本页窗口内的条目
        List<Object> visible = new ArrayList<>();
        for (Object e : entries.values()) {
            if (e instanceof SubItemGroup sub && !sub.isVisibleInNested(p)) continue;
            visible.add(e);
        }

        int index = 9;
        int start = GROUP_SIZE * (page - 1);
        int pages = Math.max(1, (visible.size() + GROUP_SIZE - 1) / GROUP_SIZE);
        for (int i = start; i < visible.size() && index < GROUP_SIZE + 9; i++, index++) {
            Object e = visible.get(i);
            ItemStack icon = e instanceof SubItemGroup sub ? sub.getItem(p)
                    : e instanceof PSButtonGroup button ? getItemOf(button, p) : null;
            if (icon == null) continue;
            menu.addItem(index, icon);
            menu.addMenuClickHandler(index, (pl, slot, item, action) -> {
                if (e instanceof SubItemGroup sub) {
                    SlimefunGuide_openItemGroup(profile, sub, mode);
                } else if (e instanceof PSButtonGroup button) {
                    button.open(p, profile, mode);
                }
                return false;
            });
        }

        menu.addItem(46, ChestMenuUtils.getPreviousButton(p, page, pages));
        menu.addMenuClickHandler(46, (pl, slot, item, action) -> {
            int next = page - 1;
            if (next != page && next > 0) openPage(p, profile, mode, next);
            return false;
        });

        menu.addItem(52, ChestMenuUtils.getNextButton(p, page, pages));
        menu.addMenuClickHandler(52, (pl, slot, item, action) -> {
            int next = page + 1;
            if (next != page && next <= pages) openPage(p, profile, mode, next);
            return false;
        });

        menu.open(p);
    }

    private static ItemStack getItemOf(PSButtonGroup button, Player p) {
        return button.getItem(p);
    }

    // 独立包装静态调用，便于阅读与后续拦截（如 JEG 兼容层）
    private static void SlimefunGuide_openMainMenu(PlayerProfile profile, SlimefunGuideMode mode) {
        io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide.openMainMenu(
                profile, mode, profile.getGuideHistory().getMainMenuPage());
    }

    private static void SlimefunGuide_openItemGroup(PlayerProfile profile,
                                                    io.github.thebusybiscuit.slimefun4.api.items.ItemGroup group,
                                                    SlimefunGuideMode mode) {
        io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide.openItemGroup(profile, group, mode, 1);
    }
}
