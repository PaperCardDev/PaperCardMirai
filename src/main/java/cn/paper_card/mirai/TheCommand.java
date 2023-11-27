package cn.paper_card.mirai;

import cn.paper_card.mc_command.TheMcCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
        this.addSubCommand(new ListAll());
        this.addSubCommand(new Reload());
        this.addSubCommand(new Remark());
        this.addSubCommand(new Ip());
        this.addSubCommand(new Delete());
        this.addSubCommand(new DeviceCommand(plugin));
    }

    @Override
    protected boolean canNotExecute(@NotNull CommandSender commandSender) {
        return !commandSender.hasPermission(this.permission);
    }

    void tabCompleteAllQqs(@NotNull List<String> list, @NotNull String arg) {

        if (arg.isEmpty()) list.add("<成功登录过的QQ号码>");

        final List<Long> qqs;

        try {
            qqs = plugin.getAccountStorage().queryAllQqs();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        for (final Long qq : qqs) {
            final String str = "%d".formatted(qq);
            if (str.startsWith(arg)) list.add(str);
        }
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
                plugin.sendError(commandSender, "你必须提供参数：完成滑块验证后得到的Ticket");
                return true;
            }

            final long botId;
            final TheLoginSolver2 solver;

            if (argBotId == null) {
                final List<TheLoginSolver2> waitingSliderLoginSolvers = plugin.getMiraiGo().getWaitingSliderLoginSolvers();

                final int size = waitingSliderLoginSolvers.size();

                if (size == 0) {
                    plugin.sendError(commandSender, "当前没有任何一个登录解决器在等待滑块验证！");
                    return true;
                }

                if (size != 1) {
                    plugin.sendError(commandSender, "当等待滑块验证的机器人数不为1时，你必须指定机器人QQ号码！");
                    return true;
                }

                solver = waitingSliderLoginSolvers.get(0);
                botId = solver.getQq();
            } else {

                try {
                    botId = Long.parseLong(argBotId);
                } catch (NumberFormatException ignored) {
                    plugin.sendError(commandSender, "%s 不是一个正确的QQ号码！".formatted(argBotId));
                    return true;
                }

                solver = plugin.getMiraiGo().getLoginSolver(botId);
            }


            if (solver == null) {
                plugin.sendError(commandSender, "该QQ[%d]的登录解决器不存在！".formatted(botId));
                return true;
            }

            solver.setSliderResultAndNotify(argTicket);

            plugin.sendInfo(commandSender, "您已提交滑块验证结果");
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

                final List<TheLoginSolver2> waitingSliderLoginSolvers = plugin.getMiraiGo().getWaitingSliderLoginSolvers();

                for (final TheLoginSolver2 solver : waitingSliderLoginSolvers) {
                    final String str = "%d".formatted(solver.getQq());
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
                plugin.sendError(commandSender, "你必须提供参数：短信验证码");
                return true;
            }

            final long botId;
            final TheLoginSolver2 solver;


            if (argId == null) {
                final List<TheLoginSolver2> waitingSmsLoginSolvers = plugin.getMiraiGo().getWaitingSmsLoginSolvers();

                if (waitingSmsLoginSolvers.size() == 0) {
                    plugin.sendError(commandSender, "没有任何一个登录解决器在等待短信验证码！");
                    return true;
                }

                if (waitingSmsLoginSolvers.size() != 1) {
                    plugin.sendError(commandSender, "当等待短信验证码的机器人数不为1时，你必须指定机器人QQ号码！");
                    return true;
                }


                solver = waitingSmsLoginSolvers.get(0);
                botId = solver.getQq();

            } else {

                try {
                    botId = Long.parseLong(argId);
                } catch (NumberFormatException ignored) {
                    plugin.sendError(commandSender, "%s 不是一个正确的QQ号码！".formatted(argId));
                    return true;
                }

                solver = plugin.getMiraiGo().getLoginSolver(botId);
            }


            if (solver == null) {
                plugin.sendError(commandSender, "QQ[%d]没有登录解决器！".formatted(botId));
                return true;
            }

            solver.setSmsResultAndNotify(argCode);

            plugin.sendInfo(commandSender, "你已提交短信验证码");

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

                final List<TheLoginSolver2> waitingSmsLoginSolvers = plugin.getMiraiGo().getWaitingSmsLoginSolvers();
                for (final TheLoginSolver2 solver : waitingSmsLoginSolvers) {
                    final String str = "%d".formatted(solver.getQq());
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
            this.permission = plugin.addPermission(TheCommand.this.permission.getName() + "." + this.getLabel());
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            final String argQq = strings.length > 0 ? strings[0] : null;
            final String argPassword = strings.length > 1 ? strings[1] : null;
            final String argProtocol = strings.length > 2 ? strings[2] : null;

            if (argQq == null) {
                plugin.sendError(commandSender, "你必须提供参数：机器人的QQ号码！");
                return true;
            }

            final long qq;

            try {
                qq = Long.parseLong(argQq);
            } catch (NumberFormatException e) {
                plugin.sendError(commandSender, "%s 不是正确的QQ号码！".formatted(argQq));
                return true;
            }

            plugin.getTaskScheduler().runTaskAsynchronously(() -> {
                final PaperCardMiraiApi.AccountInfo accountInfo;

                // 没有指定密码，从数据库中查询
                if (argPassword == null) {
                    try {
                        accountInfo = plugin.getAccountStorage().queryByQq(qq);
                    } catch (Exception e) {
                        e.printStackTrace();
                        plugin.sendError(commandSender, e.toString());
                        return;
                    }

                    if (accountInfo == null) {
                        plugin.sendError(commandSender, "当不提供密码登录时，该QQ[%d]应该至少成功登录过一次！".formatted(qq));
                        return;
                    }

                } else {

                    // 计算密码的MD5并转为HEX
                    final String md5 = Tool.encodeHex(Tool.md5Digest(argPassword.getBytes(StandardCharsets.UTF_8)));

                    accountInfo = new PaperCardMiraiApi.AccountInfo(
                            qq,
                            null,
                            -1,
                            md5,
                            argProtocol != null ? argProtocol : BotConfiguration.MiraiProtocol.ANDROID_PHONE.name(),
                            0,
                            "",
                            false,
                            ""
                    );
                }

                plugin.doLogin(accountInfo, commandSender);
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
                    qqs = plugin.getAccountStorage().queryAllQqs();
                } catch (Exception e) {
                    e.printStackTrace();
                    plugin.sendError(commandSender, e.toString());
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

            if (strings.length == 3) {
                final LinkedList<String> list = new LinkedList<>();
                final String argPro = strings[2];
                if (argPro.isEmpty()) list.add("<协议>");

                for (BotConfiguration.MiraiProtocol value : BotConfiguration.MiraiProtocol.values()) {
                    final String name = value.name();
                    if (name.startsWith(argPro)) list.add(name);
                }

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
                        plugin.sendError(commandSender, "当QQ机器人数不为1时，你必须指定参数：QQ号码");
                        return;
                    }
                    qq = instances.get(0).getId();
                } else {
                    try {
                        qq = Long.parseLong(argQq);
                    } catch (NumberFormatException ignored) {
                        plugin.sendError(commandSender, "%s 不是一个正确的QQ号码！".formatted(argQq));
                        return;
                    }
                }

                final Bot bot = Bot.getInstanceOrNull(qq);
                if (bot == null) {
                    plugin.sendError(commandSender, "号码为%d的机器人不存在！".formatted(qq));
                    return;
                }

                bot.closeAndJoin(null);
                plugin.sendInfo(commandSender, "已退出机器人：%d".formatted(qq));
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
                    plugin.sendError(commandSender, "你必须提供参数：QQ号码！");
                    return true;
                }

                final long qq;

                try {
                    qq = Long.parseLong(argQq);
                } catch (NumberFormatException ignored) {
                    plugin.sendError(commandSender, "%s 不是一个正确的QQ号码！".formatted(argQq));
                    return true;
                }


                boolean ok;
                try {
                    ok = plugin.getAccountStorage().setAutoLogin(qq, this.isAdd);
                } catch (Exception e) {
                    e.printStackTrace();
                    plugin.sendError(commandSender, e.toString());
                    return true;
                }

                if (!ok) {
                    plugin.sendInfo(commandSender, Component.text("QQ[%d]应该至少成功登录过一次！".formatted(qq)));
                    return true;
                }

                plugin.sendInfo(commandSender, "已%sQQ[%d]的自动登录".formatted(this.isAdd ? "开启" : "关闭", qq));

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
                        qqs = plugin.getAccountStorage().queryAllQqs();
                    } catch (Exception e) {
                        e.printStackTrace();
                        plugin.sendError(commandSender, e.toString());
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

    class ListAll extends TheMcCommand {

        private final @NotNull Permission permission;

        protected ListAll() {
            super("list");
            this.permission = plugin.addPermission(TheCommand.this.permission.getName() + "." + this.getLabel());
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {

            final String argPage = strings.length > 0 ? strings[0] : null;

            final int pageNo;
            if (argPage == null) {
                pageNo = 1;
            } else {
                try {
                    pageNo = Integer.parseInt(argPage);
                } catch (NumberFormatException e) {
                    plugin.sendError(commandSender, "%s 不是正确的页码！".formatted(argPage));
                    return true;
                }
            }

            plugin.getTaskScheduler().runTaskAsynchronously(() -> {

                final List<PaperCardMiraiApi.AccountInfo> list;
                final int pageSize = 1;

                try {
                    list = plugin.getAccountStorage().queryOrderByTimeDescWithPage(pageSize, pageSize * (pageNo - 1));
                } catch (Exception e) {
                    e.printStackTrace();
                    plugin.sendError(commandSender, e.toString());
                    return;
                }


                final TextComponent.Builder text = Component.text();
                text.append(Component.text("---- QQ机器人 | 第%d页 ----".formatted(pageNo)).color(NamedTextColor.GREEN));
                text.appendNewline();

                final int size = list.size();

                if (size == 0) {
                    text.append(Component.text("本页没有任何记录啦").color(NamedTextColor.GRAY));
                    text.appendNewline();
                } else {

                    final long cur = System.currentTimeMillis();

                    for (final PaperCardMiraiApi.AccountInfo info : list) {
                        String state;
                        final Bot bot = Bot.findInstance(info.qq());
                        final boolean online;
                        if (bot == null) {
                            state = "[未登录]";
                            online = false;
                        } else {
                            if (bot.isOnline()) {
                                state = "[在线]";
                                online = true;
                            } else {
                                state = "[离线]";
                                online = false;
                            }
                        }

                        text.append(Component.text("QQ: "));
                        text.append(Component.text(info.qq()));
                        text.appendNewline();

                        text.append(Component.text("昵称: "));
                        text.append(Component.text(info.nick()));
                        text.appendNewline();

                        text.append(Component.text("备注: "));
                        text.append(Component.text(info.remark()));
                        text.appendNewline();

                        text.append(Component.text("等级: "));
                        text.append(Component.text(info.level()));
                        text.appendNewline();

                        text.append(Component.text("状态: "));
                        text.append(Component.text(state)
                                .color(NamedTextColor.GRAY).decorate(TextDecoration.UNDERLINED)
                                .clickEvent(ClickEvent.runCommand("/mirai %s %d".formatted(
                                        online ? "logout" : "login", info.qq()
                                )))
                                .hoverEvent(HoverEvent.showText(Component.text(online ? "点击退出" : "点击登录")))
                        );
                        text.appendNewline();

                        text.append(Component.text("协议: "));
                        text.append(Component.text(info.protocol()));
                        text.appendNewline();

                        text.append(Component.text("自动登录: "));
                        text.append(Component.text(info.autoLogin() ? "[开启]" : "[关闭]")
                                .color(NamedTextColor.GRAY).decorate(TextDecoration.UNDERLINED)
                                .clickEvent(ClickEvent.runCommand("/mirai auto-login %s %d".formatted(
                                        info.autoLogin() ? "remove" : "add", info.qq()
                                )))
                                .hoverEvent(HoverEvent.showText(Component.text(info.autoLogin() ? "点击关闭自动登录" : "点击开启自动登录")))
                        );
                        text.appendNewline();

                        text.append(Component.text("上次登录: "));
                        text.append(Component.text(plugin.toReadableTime(cur - info.time())));
                        text.append(Component.text("前"));
                        text.appendNewline();

                        text.append(Component.text("IP: "));
                        text.append(Component.text(info.ip()));
                        text.appendNewline();
                    }

                }

                final boolean hasPre = pageNo > 1;
                final boolean noNext = size < pageSize;

                text.append(Component.text("[上一页]")
                        .color(NamedTextColor.GRAY).decorate(TextDecoration.UNDERLINED)
                        .hoverEvent(HoverEvent.showText(Component.text(hasPre ? "点击上一页" : "没有上一页啦")))
                        .clickEvent(hasPre ? ClickEvent.runCommand("/mirai list %d".formatted(pageNo - 1)) : null)
                );
                text.appendSpace();
                text.append(Component.text("[下一页]")
                        .color(NamedTextColor.GRAY).decorate(TextDecoration.UNDERLINED)
                        .hoverEvent(HoverEvent.showText(Component.text(noNext ? "没有下一页啦" : "点击下一页")))
                        .clickEvent(noNext ? null : ClickEvent.runCommand("/mirai list %d".formatted(pageNo + 1)))
                );

                plugin.sendInfo(commandSender, text.build());
            });

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            return null;
        }
    }

    class Delete extends TheMcCommand {

        private final @NotNull Permission permission;

        protected Delete() {
            super("delete");
            this.permission = plugin.addPermission(TheCommand.this.permission.getName() + "." + this.getLabel());
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            final String argQq = strings.length > 0 ? strings[0] : null;

            if (argQq == null) {
                plugin.sendError(commandSender, "你必须提供参数：QQ号码");
                return true;
            }

            final long qq;

            try {
                qq = Long.parseLong(argQq);
            } catch (NumberFormatException e) {
                plugin.sendError(commandSender, "%s 不是正确的QQ号码！".formatted(argQq));
                return true;
            }

            plugin.getTaskScheduler().runTaskAsynchronously(() -> {
                final boolean ok;
                try {
                    ok = plugin.getAccountStorage().deleteByQq(qq);
                } catch (Exception e) {
                    e.printStackTrace();
                    plugin.sendError(commandSender, e.toString());
                    return;
                }

                if (!ok) {
                    plugin.sendWarning(commandSender, "删除失败，可能该QQ没有成功登录过一次");
                    return;
                }

                plugin.sendInfo(commandSender, "已删除QQ[%d]的记录".formatted(qq));
            });

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            if (strings.length == 1) {
                final String arg = strings[0];
                final LinkedList<String> list = new LinkedList<>();
                tabCompleteAllQqs(list, arg);
                return list;
            }
            return null;
        }
    }

    class Reload extends TheMcCommand {

        private final @NotNull Permission permission;

        protected Reload() {
            super("reload");
            this.permission = plugin.addPermission(TheCommand.this.permission.getName() + "." + this.getLabel());
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            plugin.reloadConfig();
            plugin.sendInfo(commandSender, "已重载配置");
            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            return null;
        }
    }

    class Remark extends TheMcCommand {

        private final @NotNull Permission permission;


        protected Remark() {
            super("remark");
            this.permission = plugin.addPermission(TheCommand.this.permission.getName() + "." + this.getLabel());
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            // QQ remark
            final String argQq = strings.length > 0 ? strings[0] : null;
            final String argRemark = strings.length > 1 ? strings[1] : null;

            if (argQq == null) {
                plugin.sendError(commandSender, "你必须指定参数：QQ号");
                return true;
            }

            if (argRemark == null) {
                plugin.sendError(commandSender, "你必须指定参数：备注信息");
                return true;
            }

            final long qq;

            try {
                qq = Long.parseLong(argQq);
            } catch (NumberFormatException e) {
                plugin.sendError(commandSender, "%s 不是正确的QQ号码！".formatted(argQq));
                return true;
            }

            plugin.getTaskScheduler().runTaskAsynchronously(() -> {
                boolean ok;
                try {
                    ok = plugin.getAccountStorage().setRemark(qq, argRemark);
                } catch (Exception e) {
                    e.printStackTrace();
                    plugin.sendError(commandSender, e.toString());
                    return;
                }

                if (!ok) {
                    plugin.sendWarning(commandSender, "设置备注失败，可能该QQ没有成功登录过一次");
                    return;
                }

                plugin.sendInfo(commandSender, "已将QQ[%d]的备注设置为：%s".formatted(qq, argRemark));
            });

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {

            if (strings.length == 1) {
                final String arg = strings[0];
                final LinkedList<String> list = new LinkedList<>();
                tabCompleteAllQqs(list, arg);
                return list;
            }

            if (strings.length == 2) {
                final String argRemark = strings[1];
                if (argRemark.isEmpty()) {
                    final LinkedList<String> list = new LinkedList<>();
                    list.add("<备注>");
                    return list;
                }
                return null;
            }

            return null;
        }
    }

    class Ip extends TheMcCommand {

        private final @NotNull Permission permission;

        protected Ip() {
            super("ip");
            this.permission = plugin.addPermission(TheCommand.this.permission.getName() + "." + this.getLabel());
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {

            plugin.sendInfo(commandSender, "正在获取公网IP...");

            plugin.getTaskScheduler().runTaskAsynchronously(() -> {
                final String ip;
                try {
                    ip = plugin.getPublicIp();
                } catch (Exception e) {
                    e.printStackTrace();
                    plugin.sendError(commandSender, e.toString());
                    return;
                }

                plugin.sendInfo(commandSender, "公网IP为：" + ip);
            });

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            return null;
        }
    }

}
