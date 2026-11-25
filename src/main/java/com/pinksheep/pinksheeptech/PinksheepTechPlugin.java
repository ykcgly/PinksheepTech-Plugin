package com.pinksheep.pinksheeptech;

import com.pinksheep.pinksheeptech.load.Setup;
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 粉羊科技 PinksheepTech —— 独立 Slimefun 附属插件主类。
 *
 * <p>由原 RSC（RykenSlimeCustomizer）配置版改写而来：内容仍来自同一组 YAML（已打包进 jar
 * 的 content/，运行期从自身资源读取），但加载逻辑全部以原生 Java 实现，不再依赖 RSC
 * 运行时及其脚本引擎。</p>
 *
 * <p>依赖策略与原 info.yml 的 pluginDepends 一致，均为软依赖：缺失仅提示，不阻断加载。
 * （这些插件仅作为配方可引用来源存在；若想恢复「缺失即卸载」的强约束，
 * 把它移入 {@link #HARD_DEPS} 即可。）</p>
 */
public final class PinksheepTechPlugin extends JavaPlugin implements SlimefunAddon {

    private static PinksheepTechPlugin instance;

    /** 软依赖：{插件名, 中文名}，缺失仅提示后继续加载。取自 RSC info.yml 的 pluginDepends。 */
    private static final String[][] SOFT_DEPS = {
            {"TranscEndence", "TranscEndence"},
            {"InfinityExpansion", "无尽贪婪"},
    };

    /** 硬依赖；当前为必装 Slimefun。 */
    private static final String[][] HARD_DEPS = {{"Slimefun", "粘液科技"}};

    @Override
    public void onEnable() {
        instance = this;
        PST.plugin = this;
        saveDefaultConfig();
        getLogger().info("========================================================");
        getLogger().info("                 PinksheepTech 粉羊科技 开始加载");
        getLogger().info("                 版本号： Release 1.1.3 (Build 22)");
        getLogger().info("                 作者： PinksheepYTG");
        getLogger().info("========================================================");

        if (!checkDependencies()) return;
        initMetrics();
        try {
            Setup.loadAll();
            getLogger().info("PinksheepTech 加载成功，欢迎使用本附属");
        } catch (Throwable e) {
            getLogger().severe("PinksheepTech 加载过程中出现异常: " + e);
            e.printStackTrace();
        }
    }

    /**
     * bStats 匿名统计（serviceId 35674）。在硬依赖检查通过后初始化——
     * 插件若因缺依赖被禁用，不应上报虚假数据。统计失败绝不拖垮插件加载。
     * 服务端级开关在 plugins/bStats/config.yml（bStats 全局 opt-out，无需本插件配置）。
     */
    private void initMetrics() {
        try {
            new org.bstats.bukkit.Metrics(this, 35674);
        } catch (Throwable t) {
            getLogger().warning("[bStats] 初始化失败，不影响功能: " + t);
        }
    }

    private boolean checkDependencies() {
        for (String[] dep : HARD_DEPS) {
            if (Bukkit.getPluginManager().getPlugin(dep[0]) == null) {
                getLogger().severe("缺少 " + label(dep) + "，PinksheepTech 已自动卸载！");
                Bukkit.getPluginManager().disablePlugin(this);
                return false;
            }
        }
        for (String[] dep : SOFT_DEPS) {
            if (Bukkit.getPluginManager().getPlugin(dep[0]) == null) {
                getLogger().warning("缺少 " + label(dep) + "，部分配方可能无法加载");
            }
        }
        return true;
    }

    private static String label(String[] dep) {
        return dep[1].equalsIgnoreCase(dep[0]) ? dep[0] : dep[0] + "（" + dep[1] + "）";
    }

    @Override
    public void onDisable() {
        // 移除矿物生成器（与 RSC 卸载行为一致）
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            world.getPopulators().removeIf(x -> x instanceof com.pinksheep.pinksheeptech.customs.PSOrePopulator);
        }
        getLogger().info("PinksheepTech 已卸载");
    }

    public static PinksheepTechPlugin getInstance() {
        return instance;
    }

    @Override
    public JavaPlugin getJavaPlugin() {
        return this;
    }

    @Override
    public String getBugTrackerURL() {
        return null;
    }
}
