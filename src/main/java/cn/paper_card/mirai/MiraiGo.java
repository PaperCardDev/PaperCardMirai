package cn.paper_card.mirai;

import net.mamoe.mirai.Bot;
import net.mamoe.mirai.BotFactory;
import net.mamoe.mirai.contact.Friend;
import net.mamoe.mirai.data.UserProfile;
import net.mamoe.mirai.utils.BotConfiguration;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.cssxsh.mirai.tool.FixProtocolVersion;
import xyz.cssxsh.mirai.tool.KFCFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class MiraiGo {
    private final @NotNull HashMap<Long, TheLoginSolver2> loginSolvers;
    private final @NotNull PaperCardMirai plugin;

    private final @NotNull Tool tool;

    public MiraiGo(@NotNull PaperCardMirai plugin) {
        this.plugin = plugin;
        this.loginSolvers = new HashMap<>();
        this.tool = new Tool();
    }

    void close() {
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
            plugin.getLogger().info("等待QQ机器人关闭，5秒后将强制关闭...");
            try {
                lock.wait(5 * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
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


    @NotNull File getProtocolFile(@NotNull BotConfiguration.MiraiProtocol protocol) {
        final File folder = plugin.getDataFolder();

        if (!folder.isDirectory() && !folder.mkdir()) {
            plugin.getLogger().warning("创建文件夹失败：" + folder.getPath());
        }

        return new File(folder, protocol.name().toLowerCase() + ".json");
    }

    public void doLogin(@NotNull PaperCardMiraiApi.AccountInfo accountInfo, @NotNull CommandSender sender) {
        // 获取最新版本协议


        // 解析协议
        final BotConfiguration.MiraiProtocol protocol;
        try {
            protocol = BotConfiguration.MiraiProtocol.valueOf(accountInfo.protocol());
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            plugin.sendError(sender, e.toString());
            return;
        }

        plugin.sendInfo(sender, "使用[%s]协议来登录QQ机器人[%d]".formatted(protocol.name(), accountInfo.qq()));

        plugin.sendInfo(sender, "FixProtocolVersion本地加载协议信息...");

        try {
            FixProtocolVersion.load(protocol, this.getProtocolFile(protocol));
        } catch (Exception e) {
            plugin.sendWarning(sender, e.toString());
            plugin.sendInfo(sender, "FixProtocolVersion从网络下载协议信息...");
            FixProtocolVersion.fetch(protocol, "latest");
        }

        plugin.sendInfo(sender, "FixProtocolVersion.update()...");
        FixProtocolVersion.update();

        final String protocolInfo = FixProtocolVersion.info().get(protocol);
        plugin.sendInfo(sender, "%s: %s".formatted(protocol.toString(), protocolInfo));

        plugin.sendInfo(sender, "KFCFactory.install()...");
        KFCFactory.install();

        // 检查登录IP
        String ip = null;

        try {
            ip = this.tool.getPublicIp();
        } catch (Exception e) {
            e.printStackTrace();
            plugin.sendWarning(sender, "无法获取公网IP: " + e);
        }

        if (ip != null && !ip.isEmpty()) {
            final String ip1 = accountInfo.ip();
            if (!ip1.isEmpty() && !ip.equals(ip1)) {
                final long cur = System.currentTimeMillis();
                final long lastLogin = accountInfo.time();
                if (lastLogin > 0) {
                    final long d = lastLogin + 60 * 60 * 1000L - cur;
                    if (d > 0) {
                        plugin.sendWarning(sender, "不建议登录，登录IP变动，容易被腾讯风控：%s -> %s，建议在%s后再登录"
                                .formatted(ip1, ip, plugin.toReadableTime(d)));
                        return;
                    }
                }
            }
        }

        plugin.sendInfo(sender, "本次登录的公网IP为：%s".formatted(ip));

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


        final PaperCardMirai plugin = this.plugin;

        final AtomicReference<String> device = new AtomicReference<>(null);

        final BotFactory.BotConfigurationLambda botConfigurationLambda = botConfiguration -> {

            // 工作目录
            botConfiguration.setWorkingDir(plugin.getBotWorkingDir(accountInfo.qq()));
            // 协议
            botConfiguration.setProtocol(protocol);
            // 登录解决器
            botConfiguration.setLoginSolver(solver);

            // 日志
            if (plugin.isNoBotLog()) botConfiguration.noBotLog();
            if (plugin.isNetworkLog()) botConfiguration.noNetworkLog();

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

        plugin.sendInfo(sender, "登录QQ机器人[%d]...".formatted(accountInfo.qq()));

        try {
            bot.login();

            plugin.sendInfo(sender, "登录QQ[%d]成功".formatted(accountInfo.qq()));

            final String nick = bot.getNick();
            final Friend asFriend = bot.getAsFriend();
            final UserProfile userProfile = asFriend.queryProfile();
            final int qLevel = userProfile.getQLevel();

            final PaperCardMiraiApi.AccountInfo info = new PaperCardMiraiApi.AccountInfo(
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

            plugin.sendInfo(sender, "QQ: %d, 昵称: %s, 等级: %d".formatted(accountInfo.qq(), nick, qLevel));

            // 保存到数据库
            try {
                final boolean added = plugin.getAccountStorage().addOrUpdateByQq(info);

                plugin.sendInfo(sender, "%s了QQ机器人[%d]账号信息".formatted(added ? "添加" : "更新", accountInfo.qq()));

            } catch (Exception e) {
                e.printStackTrace();
                plugin.sendError(sender, e.toString());
            }

        } catch (RuntimeException e) {
            e.printStackTrace();
            final StringBuilder sb = new StringBuilder();

            sb.append(e);
            sb.append('\n' );

            Throwable t = e;
            while ((t = t.getCause()) != null) {
                sb.append(t);
                sb.append('\n' );
            }

            plugin.sendError(sender, "登录QQ[%d]失败: %s".formatted(accountInfo.qq(), sb.toString()));
        }


        synchronized (this.loginSolvers) {
            this.loginSolvers.remove(accountInfo.qq());
        }


        final String devInfo = device.get();
        final File file = new File(plugin.getBotWorkingDir(accountInfo.qq()), "device.json");
        if (devInfo != null) {
            // 保存到文件中
            try {
                plugin.writeString(file, devInfo);
                plugin.sendInfo(sender, "已将设备信息保存到device.json中");
            } catch (IOException e) {
                e.printStackTrace();
                plugin.sendWarning(sender, e.toString());
            }
        } else {
            // 读取设备信息保存到数据库
            final String json;
            try {
                json = Tool.readToString(file);
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


}
