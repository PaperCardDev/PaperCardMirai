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
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.NoSuchElementException;

@SuppressWarnings("unused")
public final class PaperCardMirai extends JavaPlugin implements PaperCardMiraiApi {


    private final @NotNull TaskScheduler taskScheduler;


    private final @NotNull AccountStorageImpl accountStorage;

    private final @NotNull DeviceInfoStorageImpl deviceInfoStorage;

    private final @NotNull TextComponent prefix;

    private final @NotNull DatabaseApi.MySqlConnection mySqlConnection;

    private final @NotNull Gson gson;

    private final static String KEY_NO_BOT_LOG = "no-bot-log";
    private final static String KEY_NO_NETWORK_LOG = "no-network-log";
    private final static String KEY_AUTO_LOGIN_ENABLE = "auto-login-enable";

    private final static String KEY_MYSQL_MAX_CON_TIME = "mysql-max-con-time";

    private final @NotNull MiraiGo miraiGo;


    public PaperCardMirai() {
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


        this.mySqlConnection = this.getMySqlConnection0();
        this.taskScheduler = UniversalScheduler.getScheduler(this);


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

    @Override
    public @NotNull AccountStorage getAccountStorage() {
        return this.accountStorage;
    }

    @Override
    public @NotNull DeviceInfoStorage getDeviceInfoStorage() {
        return this.deviceInfoStorage;
    }

    @Override
    public void doLogin(@NotNull AccountInfo accountInfo, @NotNull CommandSender sender) {
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
                sb.append('\n' );
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

    @NotNull MiraiGo getMiraiGo() {
        // todo
        return null;
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
                sb.append('\n' );
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
