package com.pinksheep.pinksheeptech.customs;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import java.util.ArrayList;
import java.util.List;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryView;

/**
 * 电能工作台（workbenches.yml）。对应 RSC 的 {@code CustomWorkbench}：
 *
 * <ul>
 *   <li>不参与 tick 周期（RSC WorkBenchMachineTicker 为空 tick），合成由玩家点击
 *       {@code click} 槽位触发；</li>
 *   <li>点击时先按 {@code energyPerCraft} 扣电（电量不足直接无效），再按
 *       <b>精确槽位</b>匹配配方（{@link PSLinkedRecipe}），命中后消耗输入并产出；</li>
 *   <li>GUI 可由同名（机器 ID == 菜单 ID）自定义菜单（menus.yml）提供，否则回退默认布局。</li>
 * </ul>
 *
 * <p>复用 {@link PSMachine} 的菜单构建、电力组件与破坏掉落逻辑；tick 覆写为空以关闭
 * 自动合成（与 RSC 空工作台 ticker 对齐）。配方为空集合——自动配方匹配/LogiTech 导出
 * 均基于空集，不会产生副作用。</p>
 */
public class PSWorkbench extends PSMachine {

    private final int clickSlot;
    private final int energyPerCraft;
    private final List<PSLinkedRecipe> linkedRecipes;

    public PSWorkbench(ItemGroup group, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe,
                       int[] inputSlots, int[] outputSlots, int energyPerCraft, int capacity,
                       int clickSlot, List<PSLinkedRecipe> linkedRecipes, PSMenu menu) {
        super(group, item, recipeType, recipe, inputSlots, outputSlots,
                energyPerCraft, capacity, 1, List.of(), menu);
        this.clickSlot = clickSlot;
        this.energyPerCraft = Math.max(0, energyPerCraft);
        this.linkedRecipes = List.copyOf(linkedRecipes);
    }

    /** RSC 工作台不参与 tick（空 ticker），关闭自动合成。 */
    @Override
    protected void tick(Block b) {
        // 空：点击触发合成
    }

    /** 注册 click 槽位的点击合成处理。 */
    @Override
    protected void decorateExtra(BlockMenuPreset preset) {
        preset.addMenuClickHandler(clickSlot, (Player player, int slot, ItemStack clicked, ClickAction action) -> {
            if (player.getOpenInventory() instanceof InventoryView view
                    && view.getTopInventory().getHolder() instanceof BlockMenu menu) {
                craft(menu);
            }
            // 与 RSC 一致：恒返回 false（点击槽物品不被取走，也不吞事件）
            return false;
        });
    }

    /** 点击合成：扣电 → 匹配 → 消耗 → 产出（顺序与 RSC CustomWorkbench 一致）。 */
    private void craft(BlockMenu menu) {
        Location l = menu.getLocation();
        if (getCharge(l) < energyPerCraft) return;
        removeCharge(l, energyPerCraft);

        for (PSLinkedRecipe recipe : linkedRecipes) {
            if (recipe.isForDisplay()) continue;
            if (!recipe.matches(menu, getOutputSlots())) continue;
            recipe.consumeInputs(menu);
            recipe.pushOutputs(menu, getOutputSlots());
            return;
        }
    }

    @Override
    public List<ItemStack> getDisplayRecipes() {
        // 指南展示：每条配方以 首输入 → 逐个输出 成对展示；隐藏配方不展示
        List<ItemStack> list = new ArrayList<>();
        for (PSLinkedRecipe recipe : linkedRecipes) {
            if (recipe.isHide()) continue;
            ItemStack input = null;
            for (PSLinkedRecipe.LinkedInput in : recipe.getInputs().values()) {
                input = in.template().clone();
                break;
            }
            if (input == null) input = new ItemStack(Material.KNOWLEDGE_BOOK);
            for (PSLinkedRecipe.Out out : recipe.getOutputs()) {
                list.add(input.clone());
                list.add(out.item().clone());
            }
        }
        return list;
    }

    /** 供状态排查使用：本机已加载的强配方数。 */
    public int linkedRecipeCount() {
        return linkedRecipes.size();
    }

    /** 供日志定位：BlockMenu 的 Slimefun 数据（转发 StorageCacheUtils）。 */
    public static BlockMenu menuAt(Location l) {
        return StorageCacheUtils.getMenu(l);
    }
}
