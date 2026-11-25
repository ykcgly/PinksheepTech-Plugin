package com.pinksheep.pinksheeptech.load;

import com.pinksheep.pinksheeptech.PST;
import com.pinksheep.pinksheeptech.customs.PSLinkedRecipe;
import com.pinksheep.pinksheeptech.customs.PSMenu;
import com.pinksheep.pinksheeptech.customs.PSWorkbench;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

/**
 * 电能工作台加载（workbenches.yml）。对应 RSC 的 {@code WorkbenchReader}。
 *
 * <p>条目字段：item_group / item / click / recipe_type / recipe / capacity /
 * energyPerCraft / input[] / output[] / recipes{}。每条配方：seconds +
 * input{}（slot 必填、amount、noConsume）+ output{}（slot 可选、chance），
 * 以及 chooseOne / forDisplay / hide 开关。</p>
 *
 * <p>与 recipe_machines.yml 的关键差异：配方输入按<b>精确槽位</b>匹配（RSC linked 链路），
 * 且合成由点击 {@code click} 槽位触发，机器不参与 tick 周期。</p>
 */
public final class WorkbenchesLoader {

    private WorkbenchesLoader() {}

    public static void load() {
        LoaderSupport.loadFile("workbenches.yml", (file, id, s) -> {
            if (!RegisterConditions.pass(s)) return false;

            int capacity = s.getInt("capacity", 0);
            if (capacity < 0) {
                PST.warn(file + " " + id + " 缺少或配置错误 '能源容量' (capacity)，已跳过");
                return false;
            }
            int energy = s.getInt("energyPerCraft", 0);
            if (energy < 0) {
                PST.warn(file + " " + id + " 缺少或配置错误 '能量消耗' (energyPerCraft)，已跳过");
                return false;
            }

            int[] input = LoaderSupport.slots(s, "input");
            int[] output = LoaderSupport.slots(s, "output");
            if (input.length == 0) {
                PST.warn(file + " " + id + " 缺少 '输入槽' (input)，已跳过");
                return false;
            }
            if (output.length == 0) {
                PST.warn(file + " " + id + " 缺少 '输出槽' (output)，已跳过");
                return false;
            }

            int click = s.getInt("click", -1);
            if (click < 0 || click > 53) {
                PST.warn(file + " " + id + " 缺少或配置错误 '点击槽位' (click)，已跳过");
                return false;
            }

            String effId = LoaderSupport.effId(id, s);
            LoaderSupport.Prepared p = LoaderSupport.prepare(s, effId);
            if (p == null) return false;

            List<PSLinkedRecipe> recipes = readRecipes(file, id, s.getConfigurationSection("recipes"));
            if (recipes.isEmpty()) {
                PST.warn(file + " " + id + " 不含任何工作配方");
            }

            PSMenu menu = MenusLoader.get(effId);
            new PSWorkbench(p.group(), p.item(), p.recipeType(), p.recipe(),
                    input, output, energy, capacity, click, recipes, menu).register(PST.plugin);
            return true;
        });
    }

    /** 读取强槽位配方集合；坏配方逐条告警跳过。对应 RSC LinkedRecipeMachineTickerCreator.read。 */
    private static List<PSLinkedRecipe> readRecipes(String file, String machineId, ConfigurationSection recipes) {
        List<PSLinkedRecipe> out = new ArrayList<>();
        if (recipes == null) return out;

        for (String key : recipes.getKeys(false)) {
            ConfigurationSection r = recipes.getConfigurationSection(key);
            if (r == null) continue;
            if (!RegisterConditions.passRecipeVersion(r)) continue;

            int seconds = r.getInt("seconds", -1);
            if (seconds < 0) {
                PST.warn(file + " " + machineId + " 配方 " + key + " 缺少 '配方耗时' (seconds)，已跳过");
                continue;
            }

            Map<Integer, PSLinkedRecipe.LinkedInput> inputs = new LinkedHashMap<>();
            ConfigurationSection in = r.getConfigurationSection("input");
            if (in != null) {
                for (String k : in.getKeys(false)) {
                    ConfigurationSection item = in.getConfigurationSection(k);
                    if (item == null) continue;
                    int slot = item.getInt("slot", -1);
                    if (slot < 0 || slot > 53) {
                        PST.warn(file + " " + machineId + " 配方 " + key + " 输入物品 " + k
                                + " 缺少或非法 '槽位' (slot)，已跳过");
                        continue;
                    }
                    ItemStack stack = Read.item(item, false);
                    if (stack == null) {
                        PST.warn(file + " " + machineId + " 配方 " + key + " 输入物品 " + k + " 无效，已跳过");
                        continue;
                    }
                    stack.setAmount(Math.max(1, item.getInt("amount", 1)));
                    inputs.put(slot, new PSLinkedRecipe.LinkedInput(stack, item.getBoolean("noConsume", false)));
                }
            }
            if (inputs.isEmpty()) {
                PST.warn(file + " " + machineId + " 配方 " + key + " 缺少 '输入物品' (input)，已跳过");
                continue;
            }

            List<PSLinkedRecipe.Out> outputs = new ArrayList<>();
            ConfigurationSection outSec = r.getConfigurationSection("output");
            if (outSec != null) {
                for (String k : outSec.getKeys(false)) {
                    ConfigurationSection item = outSec.getConfigurationSection(k);
                    if (item == null) continue;
                    ItemStack stack = Read.item(item, true);
                    if (stack == null) {
                        PST.warn(file + " " + machineId + " 配方 " + key + " 输出物品 " + k + " 无效，已跳过");
                        continue;
                    }
                    int chance = Math.min(100, Math.max(1, item.getInt("chance", 100)));
                    outputs.add(new PSLinkedRecipe.Out(stack, chance, item.getInt("slot", -1)));
                }
            }
            if (outputs.isEmpty()) {
                PST.warn(file + " " + machineId + " 配方 " + key + " 缺少 '输出物品' (output)，已跳过");
                continue;
            }

            out.add(new PSLinkedRecipe(seconds, inputs, outputs,
                    r.getBoolean("chooseOne", false),
                    r.getBoolean("forDisplay", false),
                    r.getBoolean("hide", false)));
        }
        return out;
    }
}
