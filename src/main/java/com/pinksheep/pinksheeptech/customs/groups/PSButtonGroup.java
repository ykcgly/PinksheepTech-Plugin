package com.pinksheep.pinksheeptech.customs.groups;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import java.util.List;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 按钮组（groups.yml 的 {@code type: button}）：本质是指南里的一个"按钮"，
 * 点击后触发 {@code actions}（粉羊仅用 {@code link} —— 聊天栏可点击链接）。
 *
 * <p>实现为普通 {@link ItemGroup} 而非 FlexItemGroup：groups.yml 里存在以按钮组为
 * {@code item_group} 的占位符物品（如 XXSXX），{@code SlimefunItem.load()} 会调用
 * {@code itemGroup.add(this)}，而 FlexItemGroup 的 {@code add()} 是 final 且直接抛
 * {@link UnsupportedOperationException}，导致占位符物品加载失败。改为普通组后
 * 物品可正常注册进组；但本组的入口点击（见 {@link #open}）只执行 actions、从不
 * 渲染组内物品，占位符物品静默存在，符合其定位。</p>
 *
 * <p>{@link #isVisible(Player)} 恒为 {@code false}：按钮组只作为嵌套组面板
 * （{@code PSNestedGroup}）上的入口图标出现，不直接出现在指南主菜单。</p>
 */
public class PSButtonGroup extends ItemGroup {

    private final List<String> actions;

    public PSButtonGroup(NamespacedKey key, ItemStack item, List<String> actions) {
        super(key, item);
        this.actions = List.copyOf(actions);
    }

    /** 不在指南主菜单显示：仅作为嵌套组面板上的入口图标（渲染与点击由 PSNestedGroup 处理）。 */
    @Override
    public boolean isVisible(Player p) {
        return false;
    }

    /**
     * 点击入口图标：逐条执行 actions（不打开物品页面）。
     * 注意：普通 ItemGroup 没有 open 虚方法（仅 FlexItemGroup 有），
     * 此方法由 {@code PSNestedGroup} 的点击处理直接调用，非 Slimefun 调度。
     */
    public void open(Player p, PlayerProfile profile, SlimefunGuideMode mode) {
        for (String action : actions) {
            com.pinksheep.pinksheeptech.behavior.ScriptBehaviors.runGroupAction(p, action);
        }
    }
}
