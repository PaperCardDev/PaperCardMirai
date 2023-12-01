package cn.paper_card.mirai;

import cn.paper_card.database.api.DatabaseApi;
import cn.paper_card.paper_card_mirai.api.AccountInfo;
import cn.paper_card.paper_card_mirai.api.PaperCardMiraiApi;
import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;

public final class ThePlugin extends JavaPlugin {


    private final @NotNull TaskScheduler taskScheduler;


    private final @NotNull TextComponent prefix;


    private final static String KEY_NO_BOT_LOG = "no-bot-log";
    private final static String KEY_NO_NETWORK_LOG = "no-network-log";
    private final static String KEY_AUTO_LOGIN_ENABLE = "auto-login-enable";

    private final @NotNull MiraiGo miraiGo;

    private PaperCardMiraiApiImpl paperCardMiraiApi = null;


    public ThePlugin() {
        final ClassLoader classLoader = this.getClassLoader();
        if (classLoader instanceof URLClassLoader urlClassLoader) {
            this.getLogger().info(urlClassLoader.toString());
        } else {
            throw new RuntimeException("不是URLClassLoader");
        }

        final URLClassLoaderAccess access = URLClassLoaderAccess.create(urlClassLoader);
        final File lib = new File(this.getDataFolder(), "lib");
        final File[] files = lib.listFiles();
        if (files != null) {
            for (File file : files) {
                this.getLogger().info("Loading %s".formatted(file.getName()));
                try {
                    access.addURL(file.toURI().toURL());
                } catch (MalformedURLException ignored) {
                }
            }
        }

        this.miraiGo = new MiraiGo(this);


        this.taskScheduler = UniversalScheduler.getScheduler(this);


        this.prefix = Component.text()
                .append(Component.text("[").color(NamedTextColor.AQUA))
                .append(Component.text(this.getName()).color(NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD))
                .append(Component.text("]").color(NamedTextColor.AQUA))
                .build();
    }

    boolean isNoBotLog() {
        return this.getConfig().getBoolean(KEY_NO_BOT_LOG, false);
    }

    boolean isNetworkLog() {
        return this.getConfig().getBoolean(KEY_NO_NETWORK_LOG, false);
    }

    boolean isAutoLoginEnable() {
        return this.getConfig().getBoolean(KEY_AUTO_LOGIN_ENABLE, false);
    }

    private void doAutoLogin() {
        if (!this.isAutoLoginEnable()) return;

        final List<AccountInfo> ais;
        try {
            ais = getPaperCardMiraiApi().getQqAccountService().queryAutoLoginAccounts();
        } catch (Exception e) {
            this.handleException("获取自动登录账号时异常", e);
            return;
        }

        int i = 0;
        for (final AccountInfo ai : ais) {
            this.taskScheduler.runTaskLaterAsynchronously(() -> doLogin(ai, getServer().getConsoleSender()), i * 1200L);
            ++i;
        }
    }

    @Override
    public void onLoad() {
        final DatabaseApi api = this.getServer().getServicesManager().load(DatabaseApi.class);
        if (api == null) throw new RuntimeException("无法连接到" + DatabaseApi.class.getSimpleName());

        final DatabaseApi.MySqlConnection connection = api.getRemoteMySQL().getConnectionImportant();
        this.paperCardMiraiApi = new PaperCardMiraiApiImpl(connection);

        this.getSLF4JLogger().info("注册%s...".formatted(PaperCardMiraiApi.class.getSimpleName()));
        this.getServer().getServicesManager().register(PaperCardMiraiApi.class, this.paperCardMiraiApi, this, ServicePriority.Highest);
    }

    @NotNull PaperCardMiraiApiImpl getPaperCardMiraiApi() {
        return this.paperCardMiraiApi;
    }

    @Override
    public void onEnable() {
        final DatabaseApi api = this.getServer().getServicesManager().load(DatabaseApi.class);
        if (api == null) {
            throw new RuntimeException("无法连接到" + DatabaseApi.class.getSimpleName());
        }

        final PluginCommand command = this.getCommand("paper-card-mirai");
        assert command != null;
        final TheCommand theCommand = new TheCommand(this);
        command.setExecutor(theCommand);
        command.setTabCompleter(theCommand);

        this.saveDefaultConfig();

        // 自动登录
        this.doAutoLogin();
    }

    void handleException(@NotNull String msg, @NotNull Throwable e) {
        this.getSLF4JLogger().error(msg, e);
    }

    @Override
    public void onDisable() {

        if (this.paperCardMiraiApi != null) {
            try {
                this.paperCardMiraiApi.getQqAccountService().closeTable();
            } catch (SQLException e) {
                this.handleException("close qq account service", e);
            }

            try {
                this.paperCardMiraiApi.getDeviceInfoService().close();
            } catch (SQLException e) {
                this.handleException("close device info service", e);
            }
        }

        this.miraiGo.close();

        this.taskScheduler.cancelTasks(this);
        this.getServer().getServicesManager().unregisterAll(this);

        getLogger().info("GoodBye!");
    }


    @NotNull Permission addPermission(@NotNull String name) {
        final Permission permission = new Permission(name);
        this.getServer().getPluginManager().addPermission(permission);
        return permission;
    }


    @NotNull TaskScheduler getTaskScheduler() {
        return this.taskScheduler;
    }

    void doLogin(@NotNull AccountInfo accountInfo, @NotNull CommandSender sender) {
        this.getMiraiGo().doLogin(accountInfo, sender);
    }

    void listBotDirs(@NotNull List<String> list, @NotNull String arg) {
        final File parent = this.getDataFolder();
        final File botDir = new File(parent, "bots");
        if (!botDir.isDirectory()) return;

        final String[] l = botDir.list();

        if (l == null) return;

        for (String s : l) {
            if (s.startsWith(arg)) list.add(s);
        }
    }

    @NotNull File getBotWorkingDir(long qq) {
        final File parent = this.getDataFolder();
        if (!parent.isDirectory() && !parent.mkdir()) {
            this.getLogger().warning("创建文件夹[%s]失败！".formatted(parent.getPath()));
        }

        final File botDir = new File(parent, "bots");
        if (!botDir.isDirectory() && !botDir.mkdir()) {
            this.getLogger().warning("创建文件夹[%s]失败！".formatted(botDir.getPath()));
        }

        final File dir = new File(botDir, "%d".formatted(qq));

        if (!dir.isDirectory() && !dir.mkdir()) {
            this.getLogger().warning("创建文件夹[%s]失败！".formatted(dir.getPath()));
        }
        return dir;
    }


    @NotNull String toReadableTime(long ms) {
        final long second = 1000L;
        final long minute = second * 60;
        final long hour = minute * 60;
        final long day = hour * 24;

        final long days = ms / day;
        ms %= day;

        final long hours = ms / hour;
        ms %= hour;

        final long minutes = ms / minute;
        ms %= minute;

        final long seconds = ms / second;

        final StringBuilder builder = new StringBuilder();

        if (days != 0) {
            builder.append(days);
            builder.append("天");
        }

        if (hours != 0) {
            builder.append(hours);
            builder.append("时");
        }

        if (minutes != 0) {
            builder.append(minutes);
            builder.append("分");
        }

        if (seconds != 0) {
            builder.append(seconds);
            builder.append("秒");
        }

        final String string = builder.toString();

        return string.isEmpty() ? "0秒" : string;
    }


    void writeString(@NotNull File file, @NotNull String content) throws IOException {
        final FileOutputStream outputStream = new FileOutputStream(file);

        final PrintWriter printWriter = new PrintWriter(outputStream, false, StandardCharsets.UTF_8);

        printWriter.print(content);

        printWriter.flush();
        printWriter.close();

        outputStream.close();

        if (printWriter.checkError())
            throw new IOException("写入文件错误！");
    }

    @NotNull MiraiGo getMiraiGo() {
        return this.miraiGo;
    }

    void sendError(@NotNull CommandSender sender, @NotNull String error) {
        sender.sendMessage(Component.text()
                .append(this.prefix)
                .appendSpace()
                .append(Component.text(error).color(NamedTextColor.RED))
                .build()
        );
    }

    void sendException(@NotNull CommandSender sender, @NotNull Throwable e) {
        final TextComponent.Builder text = Component.text();
        text.append(this.prefix);
        text.appendSpace();
        text.append(Component.text("==== 异常信息 ===="));

        for (Throwable t = e; t != null; t = t.getCause()) {
            text.appendNewline();
            text.append(Component.text(t.toString()).color(NamedTextColor.RED));
        }

        sender.sendMessage(text.build());
    }

    void sendInfo(@NotNull CommandSender sender, @NotNull String info) {
        sender.sendMessage(Component.text()
                .append(this.prefix)
                .appendSpace()
                .append(Component.text(info).color(NamedTextColor.GREEN))
                .build()
        );
    }

    void sendInfo(@NotNull CommandSender sender, @NotNull TextComponent info) {
        sender.sendMessage(Component.text()
                .append(this.prefix)
                .appendSpace()
                .append(info)
                .build()
        );
    }

    void sendWarning(@NotNull CommandSender sender, @NotNull String warning) {
        sender.sendMessage(Component.text()
                .append(this.prefix)
                .appendSpace()
                .append(Component.text(warning).color(NamedTextColor.YELLOW))
                .build()
        );
    }
}
