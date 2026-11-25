package com.pinksheep.pinksheeptech.load;

import com.pinksheep.pinksheeptech.PST;
import com.pinksheep.pinksheeptech.customs.groups.PSNestedGroup;
import com.pinksheep.pinksheeptech.customs.groups.PSButtonGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.groups.SubItemGroup;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/**
 * 物品组加载（groups.yml）。对应 RSC 的 {@code ItemGroupReader}。
 *
 * <p>groups.yml 里的 {@code type} 支持：</p>
 * <ul>
 *   <li>{@code nested} —— 顶层可嵌套组（{@link PSNestedGroup}），本项目为 {@code pinksheep}；
 *       在原版 NestedItemGroup 基础上追加按钮组渲染</li>
 *   <li>{@code sub} —— 子组，依赖 {@code parent}，须在其父组之后创建</li>
 *   <li>{@code button} —— 按钮组（{@link PSButtonGroup}）：不含物品，点击执行 {@code actions}
 *       （本项内容仅用 {@code link}，即聊天栏可点击链接）</li>
 *   <li>{@code seasonal} —— 季节性组（原版 Slimefun 无直接对应），按子组处理并读取
 *       {@code month}（仅信息保留，恒可见；与 RSC 的月份可见行为不同，此处沿用 HaimanTech2
 *       迁移时的取舍）</li>
 * </ul>
 *
 * <p>因 sub/button 依赖 parent，采用两遍加载：先建 nested，再建其余。</p>
 */
public final class GroupLoader {

    private GroupLoader() {}

    public static void load() {
        YamlConfiguration y = Yaml.loadResource("groups.yml");
        List<Map.Entry<String, ConfigurationSection>> deferred = new ArrayList<>();

        // 第一遍：nested（无 parent 依赖）
        for (String id : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(id);
            if (s == null) continue;
            if (!RegisterConditions.pass(s)) continue;
            String type = s.getString("type", "sub");
            if ("nested".equalsIgnoreCase(type)) {
                createNested(id, s);
            } else {
                deferred.add(Map.entry(id, s));
            }
        }

        // 第二遍：sub / button / seasonal（parent 已存在）
        int ok = 0, skip = 0;
        for (Map.Entry<String, ConfigurationSection> e : deferred) {
            try {
                if (createChild(e.getKey(), e.getValue())) ok++;
                else skip++;
            } catch (Exception ex) {
                PST.warn("物品组 " + e.getKey() + " 注册失败: " + ex);
                skip++;
            }
        }
        PST.log("groups.yml: 继承项注册 " + ok + ", 跳过 " + skip);
    }

    private static ItemStack display(ConfigurationSection s) {
        return Read.item(s.getConfigurationSection("item"), false);
    }

    private static void createNested(String id, ConfigurationSection s) {
        ItemStack stack = display(s);
        if (stack == null) {
            PST.warn("物品组 " + id + " 缺少有效 item");
            return;
        }
        NamespacedKey nk = new NamespacedKey(PST.plugin, id.toLowerCase(Locale.ROOT));
        int tier = s.getInt("tier", 0);
        PSNestedGroup g = tier > 0
                ? new PSNestedGroup(nk, stack, tier)
                : new PSNestedGroup(nk, stack);
        g.register(PST.plugin);
        PST.putGroup(id, g);
    }

    private static boolean createChild(String id, ConfigurationSection s) {
        ItemStack stack = display(s);
        if (stack == null) return false;

        String parentId = s.getString("parent");
        ItemGroup parent = parentId != null ? PST.group(parentId) : PST.group("pinksheep");
        String type = s.getString("type", "sub");
        NamespacedKey nk = new NamespacedKey(PST.plugin, id.toLowerCase(Locale.ROOT));

        // 按钮组：不含物品，点击执行 actions（link 等），挂在嵌套组面板上
        if ("button".equalsIgnoreCase(type)) {
            if (!(parent instanceof PSNestedGroup nested)) {
                PST.warn("物品组 " + id + " 的按钮组父级不是嵌套组，已跳过");
                return false;
            }
            PSButtonGroup button = new PSButtonGroup(nk, stack, s.getStringList("actions"));
            button.register(PST.plugin);
            PST.putGroup(id, button);
            nested.addButtonEntry(id, button);
            return true;
        }

        // seasonal 组在原版 Slimefun 无对应实现，退化为挂在顶层组下的普通子组，
        // 其 month 字段仅作信息保留（不影响注册）。
        if (parent instanceof PSNestedGroup nested) {
            SubItemGroup g = new SubItemGroup(nk, nested, stack);
            g.register(PST.plugin);
            PST.putGroup(id, g);
            nested.addSubEntry(id, g);
            return true;
        }
        // parent 缺失或不是嵌套组：降级为普通 ItemGroup，保证内容仍能被引用
        ItemGroup g = new ItemGroup(nk, stack);
        g.register(PST.plugin);
        PST.putGroup(id, g);
        return true;
    }

    /** 供其它 Loader 使用：延时/未解析组的兜底（保持 hook）。 */
    public static Map<String, ConfigurationSection> snapshotPending() {
        return new LinkedHashMap<>();
    }
}
