package cn.paper_card.mirai;

import cn.paper_card.mc_command.TheMcCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.BotFactory;
import net.mamoe.mirai.utils.BotConfiguration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.cssxsh.mirai.tool.FixProtocolVersion;
import xyz.cssxsh.mirai.tool.KFCFactory;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

class TheCommand extends TheMcCommand.HasSub {

    private final @NotNull PaperCardMirai plugin;

    private final @NotNull Permission permission;

    TheCommand(@NotNull PaperCardMirai plugin) {
        super("paper-card-mirai");
        this.plugin = plugin;
        this.permission = this.plugin.addPermission("paper-card-mirai.command");

        this.addSubCommand(new SliderVerify());
        this.addSubCommand(new SmsVerify());
        this.addSubCommand(new Login());
    }

    @Override
    protected boolean canNotExecute(@NotNull CommandSender commandSender) {
        return !commandSender.hasPermission(this.permission);
    }

    private static void sendError(@NotNull CommandSender sender, @NotNull String error) {
        sender.sendMessage(Component.text(error).color(NamedTextColor.DARK_RED));
    }

    class SliderVerify extends TheMcCommand {

        private final @NotNull Permission permission;


        SliderVerify() {
            super("slider-verify");
            this.permission = plugin.addPermission(TheCommand.this.permission.getName() + ".slider-verify");
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            // <ticket> [botId]
            final String argTicket = strings.length > 0 ? strings[0] : null;
            final String argBotId = strings.length > 1 ? strings[1] : null;

            if (argTicket == null) {
                sendError(commandSender, "你必须提供参数：完成滑块验证后得到的Ticket");
                return true;
            }

            final long botId;
            if (argBotId == null) {
                final Set<Long> waitingSliderIds = plugin.getTheLoginSolver().getWaitingSliderIds();
                if (waitingSliderIds.size() != 1) {
                    sendError(commandSender, "当等待滑块验证的机器人数不为1时，你必须指定机器人QQ号码！");
                    return true;
                }
                botId = waitingSliderIds.toArray(new Long[1])[0];
            } else {

                try {
                    botId = Long.parseLong(argBotId);
                } catch (NumberFormatException ignored) {
                    sendError(commandSender, "%s 不是一个正确的QQ号码！".formatted(argBotId));
                    return true;
                }
            }


            plugin.getTheLoginSolver().setSliderResultAndNotify(botId, argTicket);

            commandSender.sendMessage(Component.text("您已提交滑块验证结果"));
            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            // todo
            return null;
        }
    }

    class SmsVerify extends TheMcCommand {

        private final @NotNull Permission permission;

        SmsVerify() {
            super("sms-verify");
            this.permission = plugin.addPermission(TheCommand.this.permission.getName() + ".sms-verify");
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            final String argCode = strings.length > 0 ? strings[0] : null;
            final String argId = strings.length > 1 ? strings[1] : null;

            if (argCode == null) {
                sendError(commandSender, "你必须提供参数：短信验证码");
                return true;
            }

            final long botId;
            if (argId == null) {
                final Set<Long> waitingSliderIds = plugin.getTheLoginSolver().getWaitingSmsIds();
                if (waitingSliderIds.size() != 1) {
                    sendError(commandSender, "当等待短信验证码的机器人数不为1时，你必须指定机器人QQ号码！");
                    return true;
                }
                botId = waitingSliderIds.toArray(new Long[1])[0];

            } else {

                try {
                    botId = Long.parseLong(argId);
                } catch (NumberFormatException ignored) {
                    sendError(commandSender, "%s 不是一个正确的QQ号码！".formatted(argId));
                    return true;
                }
            }


            plugin.getTheLoginSolver().setSmsResultAndNotify(botId, argCode);

            commandSender.sendMessage(Component.text("你已提交短信验证码"));

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            return null;
        }
    }

    class Login extends TheMcCommand {

        private final @NotNull Permission permission;

        protected Login() {
            super("login");
            this.permission = plugin.addPermission(TheCommand.this.permission.getName() + ".login");
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            final String argQq = strings.length > 0 ? strings[0] : null;
            final String argPassword = strings.length > 1 ? strings[1] : null;

            if (argQq == null) {
                sendError(commandSender, "你必须提供参数：机器人的QQ号码！");
                return true;
            }

            if (argPassword == null) {
                sendError(commandSender, "你必须提供参数：机器人的密码！");
                return true;
            }

            final long qq;

            try {
                qq = Long.parseLong(argQq);
            } catch (NumberFormatException e) {
                sendError(commandSender, "%s 不是正确的QQ号码！".formatted(argQq));
                return true;
            }


            plugin.getTaskScheduler().runTaskAsynchronously(() -> {
                // 获取最新版本协议
                plugin.getLogger().info("获取 ANDROID_PAD 最新版本协议信息...");
                try {
                    FixProtocolVersion.fetch(BotConfiguration.MiraiProtocol.ANDROID_PAD, "latest");
                } catch (NoSuchElementException e) {
                    e.printStackTrace();
                }

                // 获取最新版本协议
                plugin.getLogger().info("获取 ANDROID_PHONE 最新版本协议信息...");
                try {
                    FixProtocolVersion.fetch(BotConfiguration.MiraiProtocol.ANDROID_PHONE, "latest");
                } catch (NoSuchElementException e) {
                    e.printStackTrace();
                }


                plugin.getLogger().info("更新协议版本...");
                FixProtocolVersion.update();

                final Map<BotConfiguration.MiraiProtocol, String> info = FixProtocolVersion.info();
                for (BotConfiguration.MiraiProtocol miraiProtocol : info.keySet()) {
                    final String is = info.get(miraiProtocol);
                    plugin.getLogger().info("%s: %s".formatted(miraiProtocol.toString(), is));
                }

                plugin.getLogger().info("安装 KFCFactory...");
                KFCFactory.install();

                final BotFactory.BotConfigurationLambda botConfigurationLambda = botConfiguration -> {
                    final File parent = plugin.getDataFolder();
                    if (!parent.isDirectory() && parent.mkdir()) {
                        plugin.getLogger().warning("创建文件夹[%s]失败！".formatted(parent.getPath()));
                    }

                    final File dir = new File(plugin.getDataFolder(), argQq);
                    if (!dir.isDirectory() && !dir.mkdir()) {
                        plugin.getLogger().warning("创建文件夹[%s]失败！".formatted(dir.getPath()));
                    }

                    botConfiguration.setWorkingDir(dir);
                    botConfiguration.fileBasedDeviceInfo();
                    botConfiguration.setProtocol(BotConfiguration.MiraiProtocol.ANDROID_PAD);
                    botConfiguration.setLoginSolver(plugin.getTheLoginSolver());
                };

                final Bot bot = BotFactory.INSTANCE.newBot(qq, argPassword, botConfigurationLambda);

                plugin.getLogger().info("登录QQ机器人[%d]...".formatted(qq));

                bot.login();
            });

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            return null;
        }
    }

}
