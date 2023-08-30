package cn.paper_card.mirai;

import cn.paper_card.mc_command.TheMcCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.utils.BotConfiguration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
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
        this.addSubCommand(new Logout());
        this.addSubCommand(new AutoLogin());
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
            if (strings.length == 1) {
                final String arg = strings[0];
                final LinkedList<String> list = new LinkedList<>();
                if (arg.isEmpty()) list.add("<完成滑块验证后得到Ticket，请直接复制粘贴>");
                return list;
            }

            if (strings.length == 2) {
                final String arg = strings[1];
                final LinkedList<String> list = new LinkedList<>();
                if (arg.isEmpty()) list.add("[要登录的QQ机器人的号码]");

                final Set<Long> waitingSliderIds = plugin.getTheLoginSolver().getWaitingSliderIds();

                for (final Long waitingSliderId : waitingSliderIds) {
                    final String str = "%d".formatted(waitingSliderId);
                    if (str.startsWith(arg)) list.add(str);
                }
                return list;
            }

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
            if (strings.length == 1) {
                final String arg = strings[0];
                final LinkedList<String> list = new LinkedList<>();
                if (arg.isEmpty()) list.add("<短信验证码>");
                return list;
            }

            if (strings.length == 2) {
                final String arg = strings[1];
                final LinkedList<String> list = new LinkedList<>();
                if (arg.isEmpty()) list.add("[等待短信验证码的QQ机器人号码]");

                final Set<Long> waitingSmsIds = plugin.getTheLoginSolver().getWaitingSmsIds();
                for (final Long waitingSmsId : waitingSmsIds) {
                    final String str = "%d".formatted(waitingSmsId);
                    if (str.startsWith(arg)) list.add(str);
                }
                return list;
            }
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

            final long qq;

            try {
                qq = Long.parseLong(argQq);
            } catch (NumberFormatException e) {
                sendError(commandSender, "%s 不是正确的QQ号码！".formatted(argQq));
                return true;
            }


            plugin.getTaskScheduler().runTaskAsynchronously(() -> {
                final QqAccountStorageService.AccountInfo accountInfo;

                if (argPassword == null) {
                    try {
                        accountInfo = plugin.getQqAccountStorageService().queryByQq(qq);
                    } catch (Exception e) {
                        e.printStackTrace();
                        sendError(commandSender, e.toString());
                        return;
                    }

                    if (accountInfo == null) {
                        sendError(commandSender, "当不提供登录时，该QQ[%d]应该至少成功登录过一次！".formatted(qq));
                        return;
                    }

                } else {

                    // 计算密码的MD5并转为HEX
                    final String md5 = Tool.encodeHex(Tool.md5Digest(argPassword.getBytes(StandardCharsets.UTF_8)));

                    accountInfo = new QqAccountStorageService.AccountInfo(
                            qq,
                            md5,
                            BotConfiguration.MiraiProtocol.ANDROID_PAD.name(),
                            System.currentTimeMillis(),
                            false
                    );
                }

                plugin.doLogin(commandSender, accountInfo);
            });

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {

            if (strings.length == 1) {
                final String arg = strings[0];
                final LinkedList<String> list = new LinkedList<>();
                if (arg.isEmpty()) list.add("<QQ号码>");

                final List<Long> qqs;

                try {
                    qqs = plugin.getQqAccountStorageService().queryAllAccountsQq();
                } catch (Exception e) {
                    e.printStackTrace();
                    sendError(commandSender, e.toString());
                    return list;
                }

                for (final Long qq : qqs) {
                    final String str = "%d".formatted(qq);
                    if (str.startsWith(arg)) list.add(str);
                }
                return list;
            }


            if (strings.length == 2) {
                final String arg = strings[1];
                final LinkedList<String> list = new LinkedList<>();
                if (arg.isEmpty()) list.add("[QQ密码]");
                return list;
            }

            return null;
        }
    }

    class Logout extends TheMcCommand {

        private final @NotNull Permission permission;

        Logout() {
            super("logout");
            this.permission = plugin.addPermission(TheCommand.this.permission.getName() + ".logout");
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {

            plugin.getTaskScheduler().runTaskAsynchronously(() -> {
                final String argQq = strings.length > 0 ? strings[0] : null;

                final long qq;
                if (argQq == null) {
                    final List<Bot> instances = Bot.getInstances();
                    if (instances.size() != 1) {
                        sendError(commandSender, "当QQ机器人数不为1时，你必须指定参数：QQ号码");
                        return;
                    }
                    qq = instances.get(0).getId();
                } else {
                    try {
                        qq = Long.parseLong(argQq);
                    } catch (NumberFormatException ignored) {
                        sendError(commandSender, "%s 不是一个正确的QQ号码！".formatted(argQq));
                        return;
                    }
                }

                final Bot bot = Bot.getInstanceOrNull(qq);
                if (bot == null) {
                    sendError(commandSender, "号码为%d的机器人不存在！".formatted(qq));
                    return;
                }

                bot.closeAndJoin(null);
                commandSender.sendMessage(Component.text("已退出机器人：%d".formatted(qq)));
            });


            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            if (strings.length == 1) {
                final String arg = strings[0];
                final LinkedList<String> list = new LinkedList<>();
                if (arg.isEmpty()) list.add("<QQ号码>");
                for (final Bot instance : Bot.getInstances()) {
                    final String str = "%d".formatted(instance.getId());
                    if (str.startsWith(arg)) list.add(str);
                }
                return list;
            }
            return null;
        }
    }

    class AutoLogin extends TheMcCommand.HasSub {

        private final @NotNull Permission permission;

        protected AutoLogin() {
            super("auto-login");
            this.permission = plugin.addPermission(TheCommand.this.permission.getName() + ".auto-login");

            this.addSubCommand(new AddRemove(true));
            this.addSubCommand(new AddRemove(false));
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }


        class AddRemove extends TheMcCommand {


            private final @NotNull Permission permission;

            private final boolean isAdd;

            protected AddRemove(boolean isAdd) {
                super(isAdd ? "add" : "remove");
                this.permission = plugin.addPermission(AutoLogin.this.permission.getName() + "." + this.getLabel());
                this.isAdd = isAdd;
            }

            @Override
            protected boolean canNotExecute(@NotNull CommandSender commandSender) {
                return !commandSender.hasPermission(this.permission);
            }

            @Override
            public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
                // <QQ>

                final String argQq = strings.length > 0 ? strings[0] : null;

                if (argQq == null) {
                    sendError(commandSender, "你必须提供参数：QQ号码！");
                    return true;
                }

                final long qq;

                try {
                    qq = Long.parseLong(argQq);
                } catch (NumberFormatException ignored) {
                    sendError(commandSender, "%s 不是一个正确的QQ号码！".formatted(argQq));
                    return true;
                }


                boolean ok;
                try {
                    ok = plugin.getQqAccountStorageService().setAutoLogin(qq, this.isAdd);
                } catch (Exception e) {
                    e.printStackTrace();
                    sendError(commandSender, e.toString());
                    return true;
                }

                if (!ok) {
                    commandSender.sendMessage(Component.text("QQ[%d]应该至少成功登录过一次！".formatted(qq)));
                    return true;
                }

                commandSender.sendMessage(Component.text("已%sQQ[%d]的自动登录".formatted(this.isAdd ? "开启" : "关闭", qq)));

                return true;
            }

            @Override
            public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
                if (strings.length == 1) {
                    final String arg = strings[0];
                    final LinkedList<String> list = new LinkedList<>();
                    if (arg.isEmpty()) list.add("<成功登录过的QQ号码>");

                    final List<Long> qqs;

                    try {
                        qqs = plugin.getQqAccountStorageService().queryAllAccountsQq();
                    } catch (Exception e) {
                        e.printStackTrace();
                        sendError(commandSender, e.toString());
                        return list;
                    }

                    for (final Long qq : qqs) {
                        final String str = "%d".formatted(qq);
                        if (str.startsWith(arg)) list.add(str);
                    }
                    return list;
                }
                return null;
            }
        }
    }

}
