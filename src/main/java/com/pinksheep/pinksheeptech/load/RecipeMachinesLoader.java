package com.pinksheep.pinksheeptech.load;

import com.pinksheep.pinksheeptech.PST;
import com.pinksheep.pinksheeptech.customs.PSMachine;
import com.pinksheep.pinksheeptech.customs.PSRecipe;
import com.pinksheep.pinksheeptech.customs.PSTransformMachine;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

/**
 * 配方机器加载（recipe_machines.yml）。对应 RSC 的 {@code RecipeMachineReader}。
 *
 * <p>条目字段：speed / capacity / energyPerCraft / input[] / output[] / recipes{}。
 * 每条配方：seconds + input{}（amount、noConsume）+ output{}（chance），
 * 以及 chooseOne / forDisplay / hide 开关。</p>
 *
 * <p><b>配方级版本</b>：每条配方可带 {@code version}（{@code '>= 1.21.2'}、{@code '< 1.21'}、
 * 区间或裸版本号）。不满足的配方在加载期直接剔除，不解析其材料、不进指南、不参与匹配与
 * {@link #isTransform} 判定，因此一台机器可以并列携带多个 MC 版本的配方而无需拆成
 * 多个 {@code id_alias} 条目。开关与整条目版本条件共用 config.yml 的 {@code version-filter}。</p>
 */
public final class RecipeMachinesLoader {

    private RecipeMachinesLoader() {}

    /** 本次加载因版本条件跳过的配方总数，加载结束汇总一行日志。 */
    private static int skippedByVersion;

    public static void load() {
        skippedByVersion = 0;
        LoaderSupport.loadFile("recipe_machines.yml", (file, id, s) -> {
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
            int speed = Math.max(1, s.getInt("speed", 1));

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

            String effId = LoaderSupport.effId(id, s);
            LoaderSupport.Prepared p = LoaderSupport.prepare(s, effId);
            if (p == null) return false;

            List<PSRecipe> recipes = readRecipes(file, id, s.getConfigurationSection("recipes"));
            if (isTransform(recipes)) {
                // 转化机（输入催化剂不消耗，仿逻辑工艺虚拟种植机）：
                // 界面与槽位固化（4 输入 / 13 状态 / 18-53 输出），yml 的 input/output 槽位表
                // 与同名菜单不生效——布局由 PSTransformMachine 固定提供。
                new PSTransformMachine(p.group(), p.item(), p.recipeType(), p.recipe(),
                        energy, capacity, speed, recipes).register(PST.plugin);
            } else {
                new PSMachine(p.group(), p.item(), p.recipeType(), p.recipe(),
                        input, output, energy, capacity, speed, recipes, MenusLoader.get(effId)).register(PST.plugin);
            }
            return true;
        });
        if (skippedByVersion > 0) {
            PST.log("配方版本过滤: 服务端 " + RegisterConditions.serverVersionText()
                    + "，共跳过 " + skippedByVersion + " 条不适用配方");
        }
    }

    /**
     * 是否为"转化机"：全部工作配方均为单一输入条目且 noConsume（输入催化剂不消耗、周期产出）。
     * 多输入条目（如钓鱼机的鱼竿+饵料）无法收敛到单输入槽布局，保持原机制与界面。
     * forDisplay 配方仅用于指南展示，不参与判定。
     */
    private static boolean isTransform(List<PSRecipe> recipes) {
        boolean hasWorking = false;
        for (PSRecipe r : recipes) {
            if (r.isForDisplay()) continue;
            hasWorking = true;
            if (r.getInputs().size() != 1 || !r.getInputs().get(0).noConsume()) {
                return false;
            }
        }
        return hasWorking;
    }

    /** 读取配方集合；坏配方逐条告警跳过。 */
    static List<PSRecipe> readRecipes(String file, String machineId, ConfigurationSection recipes) {
        List<PSRecipe> out = new ArrayList<>();
        if (recipes == null) {
            PST.warn("机器 " + machineId + " 不含任何工作配方");
            return out;
        }
        for (String key : recipes.getKeys(false)) {
            ConfigurationSection r = recipes.getConfigurationSection(key);
            if (r == null) continue;
            // 版本条件先判：不满足时连材料都不解析，避免新材质在旧服务端刷出缺失告警
            if (!RegisterConditions.passRecipeVersion(r)) {
                skippedByVersion++;
                continue;
            }
            int seconds = r.getInt("seconds", -1);
            if (seconds < 0) {
                PST.warn(file + " " + machineId + " 配方 " + key + " 缺少或配置错误 '配方耗时' (seconds)，已跳过");
                continue;
            }

            List<PSRecipe.Input> inputs = new ArrayList<>();
            ConfigurationSection in = r.getConfigurationSection("input");
            if (in != null) {
                for (String k : in.getKeys(false)) {
                    ConfigurationSection item = in.getConfigurationSection(k);
                    if (item == null) continue;
                    var stack = Read.item(item, false);
                    if (stack == null) {
                        PST.warn(file + " " + machineId + " 配方 " + key + " 输入物品 " + k + " 无效，已跳过");
                        continue;
                    }
                    inputs.add(new PSRecipe.Input(stack, Math.max(1, item.getInt("amount", 1)),
                            item.getBoolean("noConsume", false)));
                }
            }
            if (inputs.isEmpty()) {
                PST.warn(file + " " + machineId + " 配方 " + key + " 缺少 '输入物品' (input)，已跳过");
                continue;
            }

            List<PSRecipe.Output> outputs = readOutputs(file, machineId, key, r.getConfigurationSection("output"));
            if (outputs.isEmpty()) {
                PST.warn(file + " " + machineId + " 配方 " + key + " 缺少 '输出物品' (output)，已跳过");
                continue;
            }

            out.add(new PSRecipe(seconds, inputs, outputs,
                    r.getBoolean("chooseOne", false),
                    r.getBoolean("forDisplay", false),
                    r.getBoolean("hide", false)));
        }
        return out;
    }

    /** 读取输出集合（含 chance 概率，1-100 截断）。 */
    static List<PSRecipe.Output> readOutputs(String file, String machineId, String key, ConfigurationSection out) {
        List<PSRecipe.Output> outputs = new ArrayList<>();
        if (out == null) return outputs;
        for (String k : out.getKeys(false)) {
            ConfigurationSection item = out.getConfigurationSection(k);
            if (item == null) continue;
            var stack = Read.item(item, true);
            if (stack == null) {
                PST.warn(file + " " + machineId + " " + (key == null ? "" : "配方 " + key + " ") + "输出物品 " + k + " 无效，已跳过");
                continue;
            }
            int chance = item.getInt("chance", 100);
            outputs.add(new PSRecipe.Output(stack, Math.min(100, Math.max(1, chance))));
        }
        return outputs;
    }
}
