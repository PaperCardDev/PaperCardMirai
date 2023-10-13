package cn.paper_card.mirai;

import cn.paper_card.database.DatabaseApi;
import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.BotFactory;
import net.mamoe.mirai.contact.Friend;
import net.mamoe.mirai.data.UserProfile;
import net.mamoe.mirai.utils.BotConfiguration;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.cssxsh.mirai.tool.FixProtocolVersion;
import xyz.cssxsh.mirai.tool.KFCFactory;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("unused")
public final class PaperCardMirai extends JavaPlugin implements PaperCardMiraiApi {


    private final @NotNull TaskScheduler taskScheduler;

    private final @NotNull HashMap<Long, TheLoginSolver2> loginSolvers;

    private final @NotNull AccountStorageImpl accountStorage;

    private final @NotNull DeviceInfoStorageImpl deviceInfoStorage;

    private final @NotNull TextComponent prefix;

    private final @NotNull DatabaseApi.MySqlConnection mySqlConnection;

    private final @NotNull Gson gson;

    private final static String KEY_NO_BOT_LOG = "no-bot-log";
    private final static String KEY_NO_NETWORK_LOG = "no-network-log";
    private final static String KEY_AUTO_LOGIN_ENABLE = "auto-login-enable";

    private final static String KEY_MYSQL_MAX_CON_TIME = "mysql-max-con-time";


    public PaperCardMirai() {
        this.mySqlConnection = this.getMySqlConnection0();
        this.taskScheduler = UniversalScheduler.getScheduler(this);
        this.loginSolvers = new HashMap<>();

        this.prefix = Component.text()
                .append(Component.text("[").color(NamedTextColor.AQUA))
                .append(Component.text(this.getName()).color(NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD))
                .append(Component.text("]").color(NamedTextColor.AQUA))
                .build();

        this.accountStorage = new AccountStorageImpl(this);
        this.deviceInfoStorage = new DeviceInfoStorageImpl(this);

        this.gson = new Gson();
    }

    @NotNull DatabaseApi.MySqlConnection getMySqlConnection() {
        return this.mySqlConnection;
    }

    private @NotNull DatabaseApi.MySqlConnection getMySqlConnection0() {
        final Plugin p = this.getServer().getPluginManager().getPlugin("Database");
        if (p instanceof final DatabaseApi api) {
            return api.getRemoteMySqlDb().getConnectionImportant();
        }
        throw new NoSuchElementException("Database插件未安装！");
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

    long getMySqlMaxConTime() {
        return this.getConfig().getLong(KEY_MYSQL_MAX_CON_TIME, 20 * 60 * 1000L);
    }

    @Override
    public void onEnable() {
        final PluginCommand command = this.getCommand("paper-card-mirai");
        assert command != null;
        final TheCommand theCommand = new TheCommand(this);
        command.setExecutor(theCommand);
        command.setTabCompleter(theCommand);

        this.saveDefaultConfig();

        // 自动登录
        if (this.isAutoLoginEnable()) {
            final List<PaperCardMiraiApi.AccountInfo> ais;

            try {
                ais = getAccountStorage().queryAutoLoginAccounts();
            } catch (Exception e) {
                getLogger().severe(e.toString());
                e.printStackTrace();
                return;
            }

            int i = 0;
            for (final AccountInfo ai : ais) {
                this.taskScheduler.runTaskLaterAsynchronously(() -> doLogin(ai, getServer().getConsoleSender()), (i + 1) * 1000L);
                ++i;
            }
        }
    }

    @Override
    public void onDisable() {

        try {
            this.accountStorage.closeTable();
        } catch (SQLException e) {
            this.getLogger().severe(e.toString());
            e.printStackTrace();
        }


        synchronized (this.loginSolvers) {
            for (TheLoginSolver2 value : this.loginSolvers.values()) {
                if (value == null) continue;
                value.notifyClose();
            }
            this.loginSolvers.clear();
        }

        final Object lock = new Object();

        new Thread(() -> {
            for (Bot instance : Bot.getInstances()) {
                // 会阻塞
                instance.closeAndJoin(null);
            }

            synchronized (lock) {
                lock.notifyAll();
            }

        }).start();

        synchronized (lock) {
            getLogger().info("等待QQ机器人关闭，5秒后将强制关闭...");
            try {
                lock.wait(5 * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        getLogger().info("GoodBye!");

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

    @Override
    public @NotNull AccountStorage getAccountStorage() {
        return this.accountStorage;
    }

    @Override
    public @NotNull DeviceInfoStorage getDeviceInfoStorage() {
        return this.deviceInfoStorage;
    }

    @NotNull File getProtocolFile(@NotNull BotConfiguration.MiraiProtocol protocol) {
        final File folder = this.getDataFolder();

        if (!folder.isDirectory() && !folder.mkdir()) {
            this.getLogger().warning("创建文件夹失败：" + folder.getPath());
        }

        return new File(folder, protocol.name().toLowerCase() + ".json");
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

    @Override
    public void doLogin(@NotNull AccountInfo accountInfo, @NotNull CommandSender sender) {
        // 获取最新版本协议


        // 解析协议
        final BotConfiguration.MiraiProtocol protocol;
        try {
            protocol = BotConfiguration.MiraiProtocol.valueOf(accountInfo.protocol());
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            sendError(sender, e.toString());
            return;
        }

        sendInfo(sender, "使用[%s]协议来登录QQ机器人[%d]".formatted(protocol.name(), accountInfo.qq()));

        sendInfo(sender, "FixProtocolVersion本地加载协议信息...");

        try {
            FixProtocolVersion.load(protocol, this.getProtocolFile(protocol));
        } catch (Exception e) {
            sendWarning(sender, e.toString());
            sendInfo(sender, "FixProtocolVersion从网络下载协议信息...");
            FixProtocolVersion.fetch(protocol, "latest");
        }

        sendInfo(sender, "FixProtocolVersion.update()...");
        FixProtocolVersion.update();

        final String protocolInfo = FixProtocolVersion.info().get(protocol);
        sendInfo(sender, "%s: %s".formatted(protocol.toString(), protocolInfo));

        sendInfo(sender, "KFCFactory.install()...");
        KFCFactory.install();

        // 检查登录IP
        String ip = null;

        try {
            ip = getPublicIp();
        } catch (Exception e) {
            e.printStackTrace();
            sendWarning(sender, "无法获取公网IP: " + e);
        }

        if (ip != null && !ip.isEmpty()) {
            final String ip1 = accountInfo.ip();
            if (!ip1.isEmpty() && !ip.equals(ip1)) {
                final long cur = System.currentTimeMillis();
                final long lastLogin = accountInfo.time();
                if (lastLogin > 0) {
                    final long d = lastLogin + 60 * 60 * 1000L - cur;
                    if (d > 0) {
                        sendWarning(sender, "不建议登录，登录IP变动，容易被腾讯风控：%s -> %s，建议在%s后再登录"
                                .formatted(ip1, ip, toReadableTime(d)));
                        return;
                    }
                }
            }
        }

        sendInfo(sender, "本次登录的公网IP为：%s".formatted(ip));

        // 登录解决器
        final TheLoginSolver2 solver;
        synchronized (this.loginSolvers) {
            final TheLoginSolver2 s = this.loginSolvers.get(accountInfo.qq());
            if (s != null) {
                s.notifyClose();
            }
            solver = new TheLoginSolver2(sender, accountInfo.qq());
            this.loginSolvers.put(accountInfo.qq(), solver);
        }


        final PaperCardMirai plugin = this;

        final AtomicReference<String> device = new AtomicReference<>(null);

        final BotFactory.BotConfigurationLambda botConfigurationLambda = botConfiguration -> {

            // 工作目录
            botConfiguration.setWorkingDir(this.getBotWorkingDir(accountInfo.qq()));
            // 协议
            botConfiguration.setProtocol(protocol);
            // 登录解决器
            botConfiguration.setLoginSolver(solver);

            // 日志
            if (this.isNoBotLog()) botConfiguration.noBotLog();
            if (this.isNetworkLog()) botConfiguration.noNetworkLog();

            // 设备信息
            final String info;

            try {
                info = plugin.getDeviceInfoStorage().queryByQq(accountInfo.qq());
            } catch (Exception e) {
                e.printStackTrace();
                botConfiguration.fileBasedDeviceInfo();
                return;
            }

            if (info != null && !info.isEmpty()) {
                plugin.sendInfo(sender, "使用数据库中的保存的设备信息");
                botConfiguration.loadDeviceInfoJson(info);
                device.set(info);
            } else {
                botConfiguration.fileBasedDeviceInfo();
            }

        };

        // 将MD5转为字节数组
        final byte[] md5 = Tool.decodeHex(accountInfo.passwordMd5());

        final Bot bot = BotFactory.INSTANCE.newBot(accountInfo.qq(), md5, botConfigurationLambda);

        sendInfo(sender, "登录QQ机器人[%d]...".formatted(accountInfo.qq()));

        try {
            bot.login();

            sendInfo(sender, "登录QQ[%d]成功".formatted(accountInfo.qq()));

            final String nick = bot.getNick();
            final Friend asFriend = bot.getAsFriend();
            final UserProfile userProfile = asFriend.queryProfile();
            final int qLevel = userProfile.getQLevel();

            final AccountInfo info = new AccountInfo(
                    accountInfo.qq(),
                    nick,
                    qLevel,
                    accountInfo.passwordMd5(),
                    protocol.name(),
                    System.currentTimeMillis(),
                    ip != null ? ip : accountInfo.ip(),
                    accountInfo.autoLogin(),
                    accountInfo.remark()
            );

            sendInfo(sender, "QQ: %d, 昵称: %s, 等级: %d".formatted(accountInfo.qq(), nick, qLevel));

            // 保存到数据库
            try {
                final boolean added = plugin.getAccountStorage().addOrUpdateByQq(info);

                sendInfo(sender, "%s了QQ机器人[%d]账号信息".formatted(added ? "添加" : "更新", accountInfo.qq()));

            } catch (Exception e) {
                e.printStackTrace();
                sendError(sender, e.toString());
            }

        } catch (RuntimeException e) {
            e.printStackTrace();
            final StringBuilder sb = new StringBuilder();

            sb.append(e);
            sb.append('\n');

            Throwable t = e;
            while ((t = t.getCause()) != null) {
                sb.append(t);
                sb.append('\n');
            }

            sendError(sender, "登录QQ[%d]失败: %s".formatted(accountInfo.qq(), sb.toString()));
        }


        synchronized (this.loginSolvers) {
            this.loginSolvers.remove(accountInfo.qq());
        }


        final String devInfo = device.get();
        final File file = new File(this.getBotWorkingDir(accountInfo.qq()), "device.json");
        if (devInfo != null) {
            // 保存到文件中
            try {
                this.writeString(file, devInfo);
                plugin.sendInfo(sender, "已将设备信息保存到device.json中");
            } catch (IOException e) {
                e.printStackTrace();
                plugin.sendWarning(sender, e.toString());
            }
        } else {
            // 读取设备信息保存到数据库
            final String json;
            try {
                json = this.readToString(file);
                if (!json.isEmpty()) {
                    final boolean added;
                    try {
                        added = plugin.getDeviceInfoStorage().insertOrUpdateByQq(accountInfo.qq(), json);
                        plugin.sendInfo(sender, "%s成功，已将设备信息保存到数据库".formatted(added ? "添加" : "更新"));

                    } catch (Exception e) {
                        e.printStackTrace();
                        plugin.sendWarning(sender, e.toString());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                plugin.sendWarning(sender, e.toString());
            }
        }
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

    private void closeInputStream(@NotNull InputStream inputStream, @NotNull InputStreamReader reader,
                                  @NotNull BufferedReader bufferedReader) throws IOException {
        IOException exception = null;

        try {
            bufferedReader.close();
        } catch (IOException e) {
            exception = e;
        }

        try {
            reader.close();
        } catch (IOException e) {
            exception = e;
        }


        try {
            inputStream.close();
        } catch (IOException e) {
            exception = e;
        }

        if (exception != null) throw exception;
    }

    @NotNull String readToString(@NotNull File file) throws IOException {
        final FileInputStream inputStream = new FileInputStream(file);

        final InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);

        final BufferedReader reader = new BufferedReader(inputStreamReader);

        final StringBuilder sb = new StringBuilder();

        String line;

        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line);
                sb.append('\n');
            }
        } catch (IOException e) {

            try {
                this.closeInputStream(inputStream, inputStreamReader, reader);
            } catch (IOException ignored) {
            }

            throw e;
        }

        this.closeInputStream(inputStream, inputStreamReader, reader);

        return sb.toString();
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

    @NotNull String getPublicIp() throws Exception {
//        https://openapi.lddgo.net/base/gtool/api/v1/GetIp
        final URL url;
        try {
            url = new URL("https://openapi.lddgo.net/base/gtool/api/v1/GetIp");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        final HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

        connection.setDoInput(true);
        connection.setDoOutput(false);
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(2000);

        connection.connect();

        final InputStream inputStream = connection.getInputStream();

        final InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);

        final BufferedReader bufferedReader = new BufferedReader(inputStreamReader);


        final StringBuilder sb = new StringBuilder();

        String line;
        try {
            while ((line = bufferedReader.readLine()) != null) {
                sb.append(line);
                sb.append('\n');
            }

        } catch (IOException e) {
            try {
                this.closeInputStream(inputStream, inputStreamReader, bufferedReader);
            } catch (IOException ignored) {
            }
            throw e;
        }


        this.closeInputStream(inputStream, inputStreamReader, bufferedReader);

        connection.disconnect();

        final String json = sb.toString();

        final JsonObject jsonObject;
        try {
            jsonObject = this.gson.fromJson(json, JsonObject.class);
        } catch (JsonSyntaxException e) {
            throw new Exception("解析JSON时错误！", e);
        }


        final JsonElement code = jsonObject.get("code");
        if (code == null) throw new Exception("JSON对象中没有code元素！");

        final int codeInt = code.getAsInt();

        if (codeInt != 0) throw new Exception("状态码不为0");

        final JsonObject dataObject = jsonObject.get("data").getAsJsonObject();
        final JsonElement ipEle = dataObject.get("ip");
        return ipEle.getAsString();
    }

    void sendError(@NotNull CommandSender sender, @NotNull String error) {
        sender.sendMessage(Component.text()
                .append(this.prefix)
                .appendSpace()
                .append(Component.text(error).color(NamedTextColor.RED))
                .build()
        );
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
