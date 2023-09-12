package cn.paper_card.mirai;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.BotFactory;
import net.mamoe.mirai.utils.BotConfiguration;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.cssxsh.mirai.tool.FixProtocolVersion;
import xyz.cssxsh.mirai.tool.KFCFactory;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

@SuppressWarnings("unused")
public final class PaperCardMirai extends JavaPlugin {


    private final @NotNull TaskScheduler taskScheduler;

    private final @NotNull QqAccountStorageService qqAccountStorageService;

    private final @NotNull HashMap<Long, TheLoginSolver2> loginSolvers;

    public PaperCardMirai() {
        this.taskScheduler = UniversalScheduler.getScheduler(this);
        this.qqAccountStorageService = new QqAccountStorageService(this);
        this.loginSolvers = new HashMap<>();
    }


    @Override
    public void onEnable() {
        final PluginCommand command = this.getCommand("paper-card-mirai");
        assert command != null;
        final TheCommand theCommand = new TheCommand(this);
        command.setExecutor(theCommand);
        command.setTabCompleter(theCommand);

        // 自动登录
        final List<QqAccountStorageService.AccountInfo> ais;

        try {
            ais = getQqAccountStorageService().queryAutoLoginAccounts();
        } catch (Exception e) {
            getLogger().severe(e.toString());
            e.printStackTrace();
            return;
        }

        for (QqAccountStorageService.AccountInfo ai : ais) {
            this.taskScheduler.runTaskAsynchronously(() -> doLogin(getServer().getConsoleSender(), ai));
        }
    }

    @Override
    public void onDisable() {

        this.qqAccountStorageService.destroy();

        synchronized (this.loginSolvers) {
            for (TheLoginSolver2 value : this.loginSolvers.values()) {
                if (value == null) continue;
                value.notifyClose();
            }
            this.loginSolvers.clear();
        }

        for (Bot instance : Bot.getInstances()) {
            instance.closeAndJoin(null);
        }
    }

    void doLogin(@NotNull CommandSender sender, @NotNull QqAccountStorageService.AccountInfo accountInfo) {
        // 获取最新版本协议
//        FixProtocolVersion.fetch(BotConfiguration.MiraiProtocol.ANDROID_PAD, "latest");

        final BotConfiguration.MiraiProtocol protocol = BotConfiguration.MiraiProtocol.valueOf(accountInfo.protocol());

        sender.sendMessage(Component.text("使用[%s]协议来登录QQ机器人[%d]".formatted(protocol.name(), accountInfo.qq())));
        sender.sendMessage(Component.text("FixProtocolVersion本地加载协议信息..."));
        try {
            FixProtocolVersion.load(protocol);
        } catch (Exception e) {
            sender.sendMessage(Component.text("FixProtocolVersion从网络下载协议信息..."));
            FixProtocolVersion.fetch(protocol, "latest");
        }


        sender.sendMessage(Component.text("FixProtocolVersion更新协议版本..."));
        FixProtocolVersion.update();

        final String protocolInfo = FixProtocolVersion.info().get(protocol);
        sender.sendMessage(Component.text("%s: %s".formatted(protocol.toString(), protocolInfo)));

        sender.sendMessage(Component.text("安装 KFCFactory..."));
        KFCFactory.install();

        // 登录解决器
        final TheLoginSolver2 solver;
        synchronized (this.loginSolvers) {
            final TheLoginSolver2 solver2 = this.loginSolvers.get(accountInfo.qq());
            if (solver2 != null) {
                solver2.notifyClose();
            }
            solver = new TheLoginSolver2(sender, accountInfo.qq());
            this.loginSolvers.put(accountInfo.qq(), solver2);
        }


        final PaperCardMirai plugin = this;

        final BotFactory.BotConfigurationLambda botConfigurationLambda = botConfiguration -> {


            final File parent = plugin.getDataFolder();
            if (!parent.isDirectory() && parent.mkdir()) {
                plugin.getLogger().warning("创建文件夹[%s]失败！".formatted(parent.getPath()));
            }

            final File dir = new File(plugin.getDataFolder(), "%d".formatted(accountInfo.qq()));
            if (!dir.isDirectory() && !dir.mkdir()) {
                plugin.getLogger().warning("创建文件夹[%s]失败！".formatted(dir.getPath()));
            }

            botConfiguration.setWorkingDir(dir);
            botConfiguration.fileBasedDeviceInfo();
            botConfiguration.setProtocol(protocol);

            botConfiguration.setLoginSolver(solver);
        };

        // 将MD5转为字节数组
        final byte[] md5 = Tool.decodeHex(accountInfo.passwordMd5());

        final Bot bot = BotFactory.INSTANCE.newBot(accountInfo.qq(), md5, botConfigurationLambda);

        sender.sendMessage(Component.text("登录QQ机器人[%d]...".formatted(accountInfo.qq())));

        try {
            bot.login();

            sender.sendMessage(Component.text("登录QQ[%d]成功".formatted(accountInfo.qq())));

            // 将md5转为16进制

            try {
                final boolean added = plugin.getQqAccountStorageService().addOrUpdateByQq(accountInfo);

                sender.sendMessage(Component.text("%s了QQ机器人[%d]账号信息".formatted(added ? "添加" : "更新", accountInfo.qq())));

            } catch (Exception e) {
                sender.sendMessage(Component.text(e.toString()).color(NamedTextColor.DARK_RED));
                e.printStackTrace();
            }

        } catch (RuntimeException e) {
            sender.sendMessage(Component.text("登录QQ[%d]失败: %s".formatted(accountInfo.qq(), e.toString())).color(NamedTextColor.DARK_RED));
        }

        synchronized (this.loginSolvers) {
            this.loginSolvers.remove(accountInfo.qq());
        }
    }

    @NotNull List<TheLoginSolver2> getWaitingSliderLoginSolvers() {
        final LinkedList<TheLoginSolver2> list = new LinkedList<>();

        synchronized (this.loginSolvers) {
            for (final TheLoginSolver2 value : this.loginSolvers.values()) {
                if (value == null) continue;
                if (value.isWaitingSliderVerify()) {
                    list.add(value);
                }
            }
        }

        return list;
    }

    @NotNull List<TheLoginSolver2> getWaitingSmsLoginSolvers() {
        final LinkedList<TheLoginSolver2> list = new LinkedList<>();

        synchronized (this.loginSolvers) {
            for (TheLoginSolver2 value : this.loginSolvers.values()) {
                if (value == null) continue;
                if (value.isWaitingSmsCode()) {
                    list.add(value);
                }
            }
        }

        return list;
    }

    @Nullable TheLoginSolver2 getLoginSolver(long qq) {
        synchronized (this.loginSolvers) {
            return this.loginSolvers.get(qq);
        }
    }


    @NotNull Permission addPermission(@NotNull String name) {
        final Permission permission = new Permission(name);
        this.getServer().getPluginManager().addPermission(permission);
        return permission;
    }


    @NotNull TaskScheduler getTaskScheduler() {
        return this.taskScheduler;
    }

    @NotNull QqAccountStorageService getQqAccountStorageService() {
        return this.qqAccountStorageService;
    }
}
