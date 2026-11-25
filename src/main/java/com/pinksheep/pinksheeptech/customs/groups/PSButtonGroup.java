package com.pinksheep.pinksheeptech.customs.groups;

import io.github.thebusybiscuit.slimefun4.api.items.groups.FlexItemGroup;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import java.util.List;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 按钮组（groups.yml 的 {@code type: button}）。对应 RSC 按钮组语义的最小可用移植：
 *
 * <p>原 RSC 按钮组是 {@code FlexItemGroup}，点击后在聊天栏执行 {@code actions}
 * （粉羊仅用到 {@code link} —— 发送可点击的链接组件）。独立版保持同一交互：
 * 指南中点击本组图标 → 逐条执行 actions。</p>
 */
public class PSButtonGroup extends FlexItemGroup {

    private final List<String> actions;

    public PSButtonGroup(NamespacedKey key, ItemStack item, List<String> actions) {
        super(key, item);
        this.actions = List.copyOf(actions);
    }

    @Override
    public boolean isVisible(Player p, PlayerProfile profile, SlimefunGuideMode mode) {
        return !actions.isEmpty();
    }

    @Override
    public void open(Player p, PlayerProfile profile, SlimefunGuideMode mode) {
        for (String action : actions) {
            com.pinksheep.pinksheeptech.behavior.ScriptBehaviors.runGroupAction(p, action);
        }
    }
}
