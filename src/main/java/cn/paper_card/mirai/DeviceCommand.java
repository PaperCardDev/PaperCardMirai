package cn.paper_card.mirai;

import cn.paper_card.mc_command.TheMcCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

class DeviceCommand extends TheMcCommand.HasSub {

    private final @NotNull Permission permission;

    private final @NotNull PaperCardMirai plugin;

    private List<Long> allQqCache = null;

    public DeviceCommand(@NotNull PaperCardMirai plugin) {
        super("device");
        this.plugin = plugin;
        this.permission = plugin.addPermission("paper-card-mirai.command.device");

        this.addSubCommand(new Delete());
        this.addSubCommand(new Load());
        this.addSubCommand(new Remark());
        this.addSubCommand(new ListPage());
        this.addSubCommand(new Copy());
    }

    @Override
    protected boolean canNotExecute(@NotNull CommandSender commandSender) {
        return !commandSender.hasPermission(this.permission);
    }

    @NotNull List<String> tabCompleteAllDeviceQq(@NotNull CommandSender sender, @NotNull String argQq, @NotNull String tip) {
        final LinkedList<String> list = new LinkedList<>();

        if (argQq.isEmpty()) {
            list.add(tip);
            final List<Long> qqs;

            try {
                qqs = this.plugin.getDeviceInfoStorage().queryAllQqs();
            } catch (Exception e) {
                this.plugin.handleException("Tab补全，查询所有设备信息的QQ时异常", e);
                this.plugin.sendException(sender, e);
                return list;
            }

            for (final Long qq : qqs) {
                final String str = qq.toString();
                list.add(str);
            }

            this.allQqCache = qqs;
        } else {
            final List<Long> t = this.allQqCache;
            if (t != null) {
                for (Long qq : t) {
                    final String str = qq.toString();
                    if (str.startsWith(argQq)) list.add(str);
                }
            }
        }

        return list;
    }

    class Delete extends TheMcCommand {

        private final @NotNull Permission permission;

        protected Delete() {
            super("delete");
            this.permission = plugin.addPermission(DeviceCommand.this.permission.getName() + "." + this.getLabel());
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {

            final String argQq = strings.length > 0 ? strings[0] : null;

            if (argQq == null) {
                plugin.sendError(commandSender, "你必须指定参数：QQ号码");
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
                    ok = plugin.getDeviceInfoStorage().deleteByQq(qq);
                } catch (Exception e) {
                    plugin.handleException("尝试删除QQ[%d]的设备信息时异常".formatted(qq), e);
                    plugin.sendException(commandSender, e);
                    return;
                }

                if (!ok) {
                    plugin.sendWarning(commandSender, "删除失败，可能QQ[%d]的设备信息不存在！".formatted(qq));
                    return;
                }

                plugin.sendInfo(commandSender, "已删除QQ[%d]的设备信息".formatted(qq));
            });

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            if (strings.length == 1) {
                final String argQq = strings[0];
                return tabCompleteAllDeviceQq(commandSender, argQq, "<QQ>");
            }
            return null;
        }
    }

    class Load extends TheMcCommand {

        private final @NotNull Permission permission;

        protected Load() {
            super("load");
            this.permission = plugin.addPermission(DeviceCommand.this.permission.getName() + "." + this.getLabel());
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            final String argQq = strings.length > 0 ? strings[0] : null;

            if (argQq == null) {
                plugin.sendError(commandSender, "你必须指定参数：QQ号！");
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
                final File file = new File(plugin.getBotWorkingDir(qq), "device.json");

                final String json;
                try {
                    json = Tool.readToString(file);
                } catch (IOException e) {
                    plugin.handleException("读取文件%s时异常".formatted(file.getAbsolutePath()), e);
                    plugin.sendException(commandSender, e);
                    return;
                }

                final boolean added;

                try {
                    added = plugin.getDeviceInfoStorage().insertOrUpdateByQq(qq, json);
                } catch (Exception e) {
                    plugin.handleException("insertOrUpdateByQq", e);
                    plugin.sendException(commandSender, e);
                    return;
                }

                plugin.sendInfo(commandSender, "%s成功，已导入QQ[%d]的设备信息".formatted(added ? "添加" : "更新", qq));
            });
            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            if (strings.length == 1) {
                final String arg = strings[0];
                final LinkedList<String> list = new LinkedList<>();
                if (arg.isEmpty()) list.add("<bots下的文件夹>");
                plugin.listBotDirs(list, arg);
                return list;
            }
            return null;
        }
    }

    class Remark extends TheMcCommand {

        private final @NotNull Permission permission;

        protected Remark() {
            super("remark");
            this.permission = plugin.addPermission(DeviceCommand.this.permission.getName() + "." + this.getLabel());
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            final String argQq = strings.length > 0 ? strings[0] : null;
            final String argRemark = strings.length > 1 ? strings[1] : null;

            if (argQq == null) {
                plugin.sendError(commandSender, "你必须指定参数：QQ号码");
                return true;
            }

            final long qq;

            try {
                qq = Long.parseLong(argQq);
            } catch (NumberFormatException e) {
                plugin.sendError(commandSender, "%s 不是正确的QQ号".formatted(argQq));
                return true;
            }

            plugin.getTaskScheduler().runTaskAsynchronously(() -> {
                boolean ok;
                try {
                    ok = plugin.getDeviceInfoStorage().setRemark(qq, argRemark);
                } catch (Exception e) {
                    plugin.handleException("设置设备信息备注时异常", e);
                    plugin.sendException(commandSender, e);
                    return;
                }

                if (!ok) {
                    plugin.sendWarning(commandSender, "设置备注失败，可能以%d为QQ的设备信息不存在".formatted(qq));
                    return;
                }
                plugin.sendInfo(commandSender, "已将QQ[%d]的设备信息的备注设置为：%s".formatted(qq, argRemark));
            });

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {

            if (strings.length == 1) {
                final String argQq = strings[0];
                return tabCompleteAllDeviceQq(commandSender, argQq, "<QQ>");
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

    class ListPage extends TheMcCommand {

        private final @NotNull Permission permission;

        protected ListPage() {
            super("list");
            this.permission = plugin.addPermission(DeviceCommand.this.permission.getName() + "." + this.getLabel());
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
                    plugin.sendError(commandSender, "%s 不是正确的页码".formatted(argPage));
                    return true;
                }
            }

            plugin.getTaskScheduler().runTaskAsynchronously(() -> {
                final int pageSize = 1;
                final List<PaperCardMiraiApi.DeviceInfo> list;

                try {
                    list = plugin.getDeviceInfoStorage().queryAllWithPage(pageSize, (pageNo - 1) * pageSize);
                } catch (Exception e) {
                    plugin.handleException("分页查询设备信息时异常", e);
                    plugin.sendException(commandSender, e);
                    return;
                }

                final int size = list.size();

                final TextComponent.Builder text = Component.text();
                text.append(Component.text("---- 设备信息 | 第%d页 ----".formatted(pageNo)));
                text.appendNewline();

                if (size == 0) {
                    text.append(Component.text("本页没有任何记录啦").color(NamedTextColor.GRAY));
                    text.appendNewline();
                } else {
                    for (PaperCardMiraiApi.DeviceInfo info : list) {

                        text.append(Component.text("JSON: "));
                        text.append(Component.text(info.json()));
                        text.appendNewline();

                        text.append(Component.text("QQ: "));
                        text.append(Component.text(info.qq()));
                        text.appendNewline();

                        text.append(Component.text("备注: "));
                        final String remark = info.remark();
                        text.append(Component.text(remark == null ? "NULL" : remark));
                        text.appendNewline();
                    }
                }

                final boolean noNext = size < pageSize;
                final boolean hasPre = pageNo > 1;

                text.append(Component.text("[上一页]")
                        .color(NamedTextColor.GRAY).decorate(TextDecoration.UNDERLINED)
                        .hoverEvent(HoverEvent.showText(Component.text(hasPre ? "点击上一页" : "没有上一页啦")))
                        .clickEvent(hasPre ? ClickEvent.runCommand("/mirai device list %d".formatted(pageNo - 1)) : null)
                );
                text.appendSpace();
                text.append(Component.text("[下一页]")
                        .color(NamedTextColor.GRAY).decorate(TextDecoration.UNDERLINED)
                        .hoverEvent(HoverEvent.showText(Component.text(noNext ? "没有下一页啦" : "点击下一页")))
                        .clickEvent(noNext ? null : ClickEvent.runCommand("/mirai device list %d".formatted(pageNo + 1)))
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

    class Copy extends TheMcCommand {


        private final @NotNull Permission permission;

        protected Copy() {
            super("copy");
            this.permission = plugin.addPermission(DeviceCommand.this.permission.getName() + "." + this.getLabel());
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            final String argSrcQq = strings.length > 0 ? strings[0] : null;
            final String argDestQq = strings.length > 1 ? strings[1] : null;

            if (argSrcQq == null) {
                plugin.sendError(commandSender, "你必须提供参数：源QQ号码");
                return true;
            }

            if (argDestQq == null) {
                plugin.sendError(commandSender, "你必须提供参数：目标QQ号码");
                return true;
            }

            final long srcQq;
            final long destQq;

            try {
                srcQq = Long.parseLong(argSrcQq);
            } catch (NumberFormatException e) {
                plugin.sendError(commandSender, "%s 不是正确的QQ号码！".formatted(argSrcQq));
                return true;
            }

            try {
                destQq = Long.parseLong(argDestQq);
            } catch (NumberFormatException e) {
                plugin.sendError(commandSender, "%s 不是正确的QQ号码！".formatted(argDestQq));
                return true;
            }

            plugin.getTaskScheduler().runTaskAsynchronously(() -> {
                boolean ok;
                try {
                    ok = plugin.getDeviceInfoStorage().copyInfo(srcQq, destQq);
                } catch (Exception e) {
                    plugin.handleException("复制设备信息时异常", e);
                    plugin.sendException(commandSender, e);
                    return;
                }

                if (!ok) {
                    plugin.sendWarning(commandSender, "复制失败，可能的原因：以%d为QQ的设备信息不存在！".formatted(srcQq));
                    return;
                }

                plugin.sendInfo(commandSender, "复制设备信息成功，%d -> %d".formatted(srcQq, destQq));
            });

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            if (strings.length == 1) {
                final String argQq = strings[0];
                return tabCompleteAllDeviceQq(commandSender, argQq, "<源QQ>");
            }

            if (strings.length == 2) {
                final String argDestQq = strings[1];
                if (argDestQq.isEmpty()) {
                    final LinkedList<String> list = new LinkedList<>();
                    list.add("<目标QQ>");
                    return list;
                }
                return null;
            }
            return null;
        }
    }
}
