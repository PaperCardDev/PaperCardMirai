package cn.paper_card.mirai;

import cn.paper_card.paper_card_mirai.api.AccountInfo;
import cn.paper_card.paper_card_mirai.api.DeviceInfo;
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
    private final @NotNull ThePlugin plugin;

    private final @NotNull Tool tool;

    public MiraiGo(@NotNull ThePlugin plugin) {
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
                plugin.handleException("wait", e);
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

    public void doLogin(@NotNull AccountInfo accountInfo, @NotNull CommandSender sender) {
        // 获取最新版本协议


        // 解析协议
        final BotConfiguration.MiraiProtocol protocol;
        try {
            protocol = BotConfiguration.MiraiProtocol.valueOf(accountInfo.protocol());
        } catch (IllegalArgumentException e) {
            plugin.handleException("illegal protocol", e);
            plugin.sendException(sender, e);
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
            if (s != null) s.notifyClose();
            solver = new TheLoginSolver2(sender, accountInfo.qq());
            this.loginSolvers.put(accountInfo.qq(), solver);
        }

        final ThePlugin plugin = this.plugin;

        final AtomicReference<DeviceInfo> deviceFromDb = new AtomicReference<>(null);

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
            final DeviceInfo info;

            try {
                info = plugin.getPaperCardMiraiApi().getDeviceInfoService().queryByQq(accountInfo.qq());
            } catch (Exception e) {
                plugin.handleException("query device info by qq", e);
                plugin.sendException(sender, e);
                botConfiguration.fileBasedDeviceInfo();
                return;
            }

            if (info != null) {
                final String json = info.json();
                if (json != null && !json.isEmpty()) {
                    plugin.sendInfo(sender, "使用数据库中的保存的设备信息");
                    botConfiguration.loadDeviceInfoJson(json);
                    deviceFromDb.set(info);
                }
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

            plugin.sendInfo(sender, "QQ: %d, 昵称: %s, 等级: %d".formatted(accountInfo.qq(), nick, qLevel));

            // 保存到数据库
            try {
                final boolean added = plugin.getPaperCardMiraiApi().getQqAccountService().addOrUpdateByQq(info);

                plugin.sendInfo(sender, "%s了QQ机器人[%d]账号信息".formatted(added ? "添加" : "更新", accountInfo.qq()));

            } catch (Exception e) {
                plugin.handleException("qq account service -> add or update by qq", e);
                plugin.sendException(sender, e);
            }

        } catch (Exception e) {
            plugin.handleException("login failed", e);

            plugin.sendError(sender, "登录失败！");
            plugin.sendException(sender, e);
        }


        synchronized (this.loginSolvers) {
            this.loginSolvers.remove(accountInfo.qq());
        }


        final DeviceInfo devInfo = deviceFromDb.get();
        final File file = new File(plugin.getBotWorkingDir(accountInfo.qq()), "device.json");

        if (devInfo != null) {
            // 保存到文件中
            try {
                plugin.writeString(file, devInfo.json());
                plugin.sendInfo(sender, "已将设备信息保存到device.json中");
            } catch (IOException e) {
                plugin.handleException("write string to file", e);
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
                        added = plugin.getPaperCardMiraiApi().getDeviceInfoService().insertOrUpdateByQq(new DeviceInfo(
                                accountInfo.qq(), json, "备注"
                        ));

                        plugin.sendInfo(sender, "%s成功，已将设备信息保存到数据库".formatted(added ? "添加" : "更新"));

                    } catch (Exception e) {
                        plugin.handleException("device info service -> add or update", e);
                        plugin.sendException(sender, e);
                    }
                }
            } catch (IOException e) {
                plugin.handleException("read file content", e);
                plugin.sendWarning(sender, e.toString());
            }
        }
    }


}
