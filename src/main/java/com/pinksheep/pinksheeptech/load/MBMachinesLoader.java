package com.pinksheep.pinksheeptech.load;

import com.pinksheep.pinksheeptech.PST;
import com.pinksheep.pinksheeptech.customs.PSMultiBlockMachine;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

/**
 * 多方块机器加载（mb_machines.yml）。对应 RSC 的 {@code MultiBlockMachineReader}。
 *
 * <p>条目字段：item_group / item / recipe（3×3 结构，必须含 DISPENSER）/
 * work（1-9 工作槽）/ sound（SoundEffect 枚举名，可选）/ recipes{}。
 * 每条工作配方：input{}（1-9 → 发射器 9 格）+ output{}（单产物，可带 amount）。</p>
 */
public final class MBMachinesLoader {

    private MBMachinesLoader() {}

    public static void load() {
        LoaderSupport.loadFile("mb_machines.yml", (file, id, s) -> {
            if (!RegisterConditions.pass(s)) return false;

            String effId = LoaderSupport.effId(id, s);
            LoaderSupport.Prepared p = LoaderSupport.prepare(s, effId);
            if (p == null) return false;

            int work = s.getInt("work", -1);
            if (work < 1 || work > 9) {
                PST.warn(file + " " + id + " 缺少或配置错误 '工作槽' (work)，已跳过");
                return false;
            }

            ItemStack[] structure = p.recipe();
            if (structure == null || structure.length != 9) {
                PST.warn(file + " " + id + " 缺少或配置错误 '配方' (recipe)，已跳过");
                return false;
            }
            if (structure[work - 1] == null) {
                PST.warn(file + " " + id + " 工作槽对应的方块不存在，已跳过");
                return false;
            }
            boolean hasDispenser = false;
            for (ItemStack is : structure) {
                if (is != null && is.getType() == Material.DISPENSER) {
                    hasDispenser = true;
                    break;
                }
            }
            if (!hasDispenser) {
                PST.warn(file + " " + id + " 结构配方缺少发射器 (DISPENSER)，已跳过");
                return false;
            }

            Map<ItemStack[], ItemStack> craftRecipes = readRecipes(file, id,
                    s.getConfigurationSection("recipes"));

            SoundEffect sound = null;
            if (s.contains("sound")) {
                Optional<SoundEffect> effect = soundOf(s.getString("sound"));
                if (effect.isEmpty()) {
                    PST.warn(file + " " + id + " 无效的声音类型 (sound): " + s.getString("sound") + "，已跳过");
                    return false;
                }
                sound = effect.get();
            }

            new PSMultiBlockMachine(p.group(), p.item(), structure, craftRecipes, work, sound)
                    .register(PST.plugin);
            return true;
        });
    }

    private static Optional<SoundEffect> soundOf(String name) {
        if (name == null || name.isEmpty()) return Optional.empty();
        try {
            return Optional.of(SoundEffect.valueOf(name));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /** 读取工作配方：input 1-9（缺格允许为空）→ 单输出。对应 RSC readRecipes。 */
    private static Map<ItemStack[], ItemStack> readRecipes(String file, String machineId,
                                                           ConfigurationSection recipes) {
        Map<ItemStack[], ItemStack> map = new HashMap<>();
        if (recipes == null) return map;

        for (String key : recipes.getKeys(false)) {
            ConfigurationSection recipe = recipes.getConfigurationSection(key);
            if (recipe == null) continue;
            if (!RegisterConditions.passRecipeVersion(recipe)) continue;

            ItemStack[] inputs = Read.recipe(recipe.getConfigurationSection("input"), 9);
            if (inputs == null) {
                PST.warn(file + " " + machineId + " 配方 " + key + " 缺少 '输入物品' (input) 已跳过");
                continue;
            }
            ItemStack output = Read.item(recipe.getConfigurationSection("output"), true);
            if (output == null) {
                PST.warn(file + " " + machineId + " 配方 " + key + " 缺少 '输出物品' (output) 已跳过");
                continue;
            }
            map.put(inputs, output);
        }
        return map;
    }
}
