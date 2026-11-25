package com.pinksheep.pinksheeptech.customs;

import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * 强槽位（linked）配方：workbenches.yml 的 recipes 段。
 *
 * <p>对应 RSC 的 {@code CustomLinkedMachineRecipe}（WorkbenchReader →
 * LinkedRecipeMachineTickerCreator 链路）。与 {@link PSRecipe} 的聚合匹配不同，本模型
 * <b>按精确槽位</b>匹配与消耗（与 RSC 语义一致）：</p>
 *
 * <ul>
 *   <li>每个输入条目必须带 {@code slot}（0-53），对应槽位上的物品须同种且数量充足；</li>
 *   <li>输入条目可带 {@code noConsume: true}（该槽位物品不消耗）；</li>
 *   <li>输出条目可带 {@code slot}（绑定输出槽位）或不带（自由输出，推进 output 槽位表）；</li>
 *   <li>输出条目可带 {@code chance}（1-100，默认 100）；</li>
 *   <li>{@code chooseOne: true} 时在全部输出（含绑定/自由）中按概率权重随机取一个。</li>
 * </ul>
 *
 * <p>配方里的 {@code seconds} 字段在 RSC 的工作台链路中同样只是元数据
 * （WorkBenchMachineTicker 为空 tick，实际合成由点击触发），此处保留字段不参与逻辑。</p>
 */
public final class PSLinkedRecipe {

    /** 绑定槽位的输入条目：模板（amount 即需求量）+ 是否不消耗。 */
    public record LinkedInput(ItemStack template, boolean noConsume) {}

    /** 输出条目：产物 + 概率 + 绑定槽位（-1 表示自由输出）。 */
    public record Out(ItemStack item, int chance, int slot) {}

    private final int seconds;
    private final Map<Integer, LinkedInput> inputs;
    private final List<Out> outputs;
    private final boolean chooseOne;
    private final boolean forDisplay;
    private final boolean hide;

    public PSLinkedRecipe(int seconds, Map<Integer, LinkedInput> inputs, List<Out> outputs,
                          boolean chooseOne, boolean forDisplay, boolean hide) {
        this.seconds = seconds;
        this.inputs = new LinkedHashMap<>(inputs);
        this.outputs = List.copyOf(outputs);
        this.chooseOne = chooseOne;
        this.forDisplay = forDisplay;
        this.hide = hide;
    }

    public Map<Integer, LinkedInput> getInputs() { return inputs; }
    public List<Out> getOutputs() { return outputs; }
    public boolean isChooseOne() { return chooseOne; }
    public boolean isForDisplay() { return forDisplay; }
    public boolean isHide() { return hide; }
    public int getSeconds() { return seconds; }

    private static boolean similar(ItemStack a, ItemStack b) {
        return SlimefunUtils.isItemSimilar(a, b, true, false);
    }

    /**
     * 匹配：全部绑定输入槽位的物品同种且数量充足，且输出放得下。
     * 与 RSC {@code CustomLinkedMachineRecipe.matches(…, false)} 同语义。
     */
    public boolean matches(BlockMenu inv, int[] outputSlots) {
        for (Map.Entry<Integer, LinkedInput> e : inputs.entrySet()) {
            ItemStack cur = inv.getItemInSlot(e.getKey());
            int required = Math.max(1, e.getValue().template().getAmount());
            if (cur == null || cur.getType().isAir() || cur.getAmount() < required
                    || !similar(cur, e.getValue().template())) {
                return false;
            }
        }
        return fits(inv, outputSlots);
    }

    /** 消耗全部输入（noConsume 条目跳过）。匹配成功后调用。 */
    public void consumeInputs(BlockMenu inv) {
        for (Map.Entry<Integer, LinkedInput> e : inputs.entrySet()) {
            if (e.getValue().noConsume()) continue;
            inv.consumeItem(e.getKey(), Math.max(1, e.getValue().template().getAmount()));
        }
    }

    /** 输出容量检查：自由输出按输出槽聚合计算；绑定输出要求目标槽位为空或同种可叠。 */
    private boolean fits(BlockMenu inv, int[] outputSlots) {
        List<Out> free = freeOutputs();
        int emptyCount = 0;
        for (int slot : outputSlots) {
            ItemStack cur = inv.getItemInSlot(slot);
            boolean usedAsLinked = false;
            for (Out out : linkedOutputs()) {
                if (out.slot() == slot) {
                    // 绑定槽：空槽或同种可叠都视为可放
                    if (cur != null && !cur.getType().isAir()
                            && (cur.getAmount() >= cur.getMaxStackSize() || !similar(cur, out.item()))) {
                        return false;
                    }
                    usedAsLinked = true;
                    break;
                }
            }
            if (!usedAsLinked && (cur == null || cur.getType().isAir())) emptyCount++;
        }
        // 自由输出需要的空槽数（同种可叠按可叠加数抵扣）
        Map<ItemStack, Integer> need = new LinkedHashMap<>();
        for (Out out : free) {
            boolean merged = false;
            for (Map.Entry<ItemStack, Integer> e : need.entrySet()) {
                if (similar(e.getKey(), out.item())) {
                    e.setValue(e.getValue() + out.item().getAmount());
                    merged = true;
                    break;
                }
            }
            if (!merged) need.put(out.item(), out.item().getAmount());
        }
        for (Map.Entry<ItemStack, Integer> e : need.entrySet()) {
            int left = e.getValue();
            int max = Math.max(1, e.getKey().getMaxStackSize());
            // 先尝试并入已有同种非空槽
            for (int slot : outputSlots) {
                ItemStack cur = inv.getItemInSlot(slot);
                if (cur != null && !cur.getType().isAir() && similar(cur, e.getKey())) {
                    left -= (cur.getMaxStackSize() - cur.getAmount());
                    if (left <= 0) break;
                }
            }
            if (left > 0 && left > (long) emptyCount * max) return false;
        }
        return true;
    }

    private List<Out> freeOutputs() {
        List<Out> list = new ArrayList<>();
        for (Out out : outputs) {
            if (out.slot() < 0) list.add(out);
        }
        return list;
    }

    private List<Out> linkedOutputs() {
        List<Out> list = new ArrayList<>();
        for (Out out : outputs) {
            if (out.slot() >= 0) list.add(out);
        }
        return list;
    }

    private static boolean hitChance(int chance) {
        if (chance >= 100) return true;
        if (chance < 1) return false;
        return ThreadLocalRandom.current().nextInt(100) < chance;
    }

    /**
     * 产出：chooseOne 时按概率权重在全部输出中取一个；否则逐个按概率结算。
     * 绑定输出放入其槽位（空槽直接放，同种可叠则叠加）；自由输出推入输出槽。
     * 返回是否有物品被成功放入（与 RSC pushOutputs 布尔语义一致）。
     */
    public boolean pushOutputs(BlockMenu inv, int[] outputSlots) {
        List<Out> chosen = new ArrayList<>();
        if (chooseOne && !outputs.isEmpty()) {
            int total = 0;
            for (Out out : outputs) total += Math.max(1, out.chance());
            int roll = ThreadLocalRandom.current().nextInt(Math.max(1, total));
            for (Out out : outputs) {
                roll -= Math.max(1, out.chance());
                if (roll < 0) {
                    chosen.add(out);
                    break;
                }
            }
            if (chosen.isEmpty()) chosen.add(outputs.get(outputs.size() - 1));
        } else {
            for (Out out : outputs) {
                if (hitChance(out.chance())) chosen.add(out);
            }
        }

        boolean any = false;
        for (Out out : chosen) {
            ItemStack stack = out.item().clone();
            if (out.slot() >= 0) {
                ItemStack cur = inv.getItemInSlot(out.slot());
                if (cur == null || cur.getType().isAir()) {
                    inv.replaceExistingItem(out.slot(), stack);
                    any = true;
                } else if (similar(cur, stack) && cur.getAmount() < cur.getMaxStackSize()) {
                    int add = Math.min(stack.getAmount(), cur.getMaxStackSize() - cur.getAmount());
                    cur.setAmount(cur.getAmount() + add);
                    if (add >= stack.getAmount()) any = true;
                }
            } else {
                ItemStack rest = inv.pushItem(stack, outputSlots);
                if (rest == null || rest.getType().isAir() || rest.getAmount() < stack.getAmount()) {
                    any = true;
                }
            }
        }
        return any || chosen.isEmpty();
    }

    /** 指南展示用占位图标。 */
    public static ItemStack displayHint() {
        return new CustomItemStack(Material.GREEN_STAINED_GLASS_PANE,
                "§a强配方物品输入/输出", "", "§2> §a点击查看");
    }
}
