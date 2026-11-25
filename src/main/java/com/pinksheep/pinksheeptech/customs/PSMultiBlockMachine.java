package com.pinksheep.pinksheeptech.customs;

import io.github.thebusybiscuit.slimefun4.api.events.MultiBlockCraftEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlockMachine;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.ItemUtils;
import io.github.thebusybiscuit.slimefun4.libraries.paperlib.PaperLib;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Dispenser;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * 多方块机器（mb_machines.yml）。对应 RSC 的 {@code CustomMultiBlockMachine}：
 *
 * <ul>
 *   <li>结构配方（recipe 的 3×3 竖直平面）必须包含发射器（DISPENSER）；</li>
 *   <li>{@code work}（1-9）指定工作槽（玩家右键该方块触发合成）；</li>
 *   <li>实际合成材料放在发射器内，按配方 9 格一一对应匹配；</li>
 *   <li>{@code sound} 为合成成功音效（Slimefun SoundEffect 枚举名，可选）；</li>
 *   <li>输出直接塞进发射器（空间不足时提示 full-inventory）。</li>
 * </ul>
 *
 * <p>onInteract 逻辑（含发射器方位推导、虚拟背包检查、MultiBlockCraftEvent）与 RSC 同源。</p>
 */
public class PSMultiBlockMachine extends MultiBlockMachine {

    private final SoundEffect craftSound;
    private final int workIndex;
    private final BlockFace dispenserFace;

    /** RSC getFace(work)：结构触发面。 */
    public static BlockFace faceFor(int work) {
        return switch (work) {
            case 2 -> BlockFace.UP;
            case 8 -> BlockFace.DOWN;
            default -> BlockFace.SELF;
        };
    }

    public PSMultiBlockMachine(ItemGroup group, SlimefunItemStack item, ItemStack[] structureRecipe,
                               Map<ItemStack[], ItemStack> craftRecipes, int work, SoundEffect sound) {
        super(group, item, structureRecipe, faceFor(work));
        this.workIndex = work - 1;
        this.craftSound = sound;
        this.dispenserFace = dispenserFaceGet();

        for (Map.Entry<ItemStack[], ItemStack> e : craftRecipes.entrySet()) {
            addRecipe(e.getKey(), e.getValue());
        }
    }

    @Override
    public List<ItemStack> getDisplayRecipes() {
        // RSC 同款：多方块机器不在指南展示配方对
        return new ArrayList<>();
    }

    @Override
    public void onInteract(Player p, Block block) {
        Material material = super.getRecipe()[workIndex].getType();
        if (!block.getType().equals(material)) return;

        Block disBlock = block.getRelative(dispenserFace);
        BlockState bs = PaperLib.getBlockState(disBlock, false).getState();
        if (!(bs instanceof Dispenser dispenser)) return;

        Inventory inv = dispenser.getInventory();
        ItemStack[] contents = inv.getContents();

        List<ItemStack[]> inputs = io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType.getRecipeInputList(this);

        for (ItemStack[] input : inputs) {
            if (!isCraftable(inv, input)) continue;

            ItemStack output = io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
                    .getRecipeOutputList(this, input).clone();

            MultiBlockCraftEvent event = new MultiBlockCraftEvent(p, this, input, output);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled() || !SlimefunUtils.canPlayerUseItem(p, output, true)) return;

            Inventory fakeInv = createVirtualInventory(inv);
            Inventory outputInv = findOutputInventory(output, disBlock, inv, fakeInv);
            if (outputInv != null) {
                for (int j = 0; j < input.length; ++j) {
                    ItemStack item = contents[j];
                    if (item != null && item.getType() != Material.AIR) {
                        ItemUtils.consumeItem(item, input[j].getAmount(), true);
                    }
                }
                if (craftSound != null) craftSound.playAt(block);
                outputInv.addItem(output);
            } else {
                Slimefun.getLocalization().sendMessage(p, "machines.full-inventory", true);
            }
            return;
        }

        if (inv.isEmpty()) {
            Slimefun.getLocalization().sendMessage(p, "machines.inventory-empty", true);
        } else {
            Slimefun.getLocalization().sendMessage(p, "machines.pattern-not-found", true);
        }
    }

    /** 与 RSC 一致的发射器内容物匹配（先带元数据匹配，退化到仅材质匹配）。 */
    private static boolean isCraftable(Inventory inv, ItemStack[] recipe) {
        for (int j = 0; j < inv.getContents().length; ++j) {
            ItemStack cur = inv.getContents()[j];
            if (!SlimefunUtils.isItemSimilar(cur, recipe[j], true, true, false, false)
                    && !SlimefunUtils.isItemSimilar(cur, recipe[j], false, true, false, false)) {
                return false;
            }
        }
        return true;
    }

    /** 生成“扣除 1 个后”的虚拟背包用于输出空间检查。 */
    protected Inventory createVirtualInventory(Inventory inv) {
        Inventory fakeInv = Bukkit.createInventory(null, 9, "Fake Inventory");
        for (int j = 0; j < inv.getContents().length; ++j) {
            ItemStack stack = inv.getContents()[j];
            if (stack != null) {
                stack = stack.clone();
                ItemUtils.consumeItem(stack, true);
            }
            fakeInv.setItem(j, stack);
        }
        return fakeInv;
    }

    /**
     * 从结构配方推导发射器相对工作槽的方位（RSC dispenserFaceGet）：
     * 上(-3)/左(-1)/右(+1)/下(+3)，找不到退化为 SELF。
     */
    private BlockFace dispenserFaceGet() {
        int center = workIndex;
        ItemStack[] is = getRecipe();
        if (center - 3 > 0) {
            ItemStack o1 = is[center - 3];
            if (o1 != null && o1.getType().equals(Material.DISPENSER)) return BlockFace.UP;
        }
        ItemStack o2 = is[center - 1];
        if (o2 != null && o2.getType().equals(Material.DISPENSER)) return BlockFace.EAST;
        if (center + 1 >= 9) return BlockFace.SELF;
        ItemStack o3 = is[center + 1];
        if (o3 != null && o3.getType().equals(Material.DISPENSER)) return BlockFace.WEST;
        if ((center + 3) < 8) {
            ItemStack o4 = is[center + 3];
            if (o4 != null && o4.getType().equals(Material.DISPENSER)) return BlockFace.DOWN;
        }
        return BlockFace.SELF;
    }
}
