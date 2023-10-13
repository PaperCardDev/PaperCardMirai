package cn.paper_card.mirai;

import cn.paper_card.mc_command.TheMcCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;

class DeviceCommand extends TheMcCommand.HasSub {

    private final @NotNull Permission permission;

    private final @NotNull PaperCardMirai plugin;

    public DeviceCommand(@NotNull PaperCardMirai plugin) {
        super("device");
        this.plugin = plugin;
        this.permission = plugin.addPermission("paper-card-mirai.command.device");

        this.addSubCommand(new Delete());
        this.addSubCommand(new Load());
    }

    @Override
    protected boolean canNotExecute(@NotNull CommandSender commandSender) {
        return !commandSender.hasPermission(this.permission);
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
                    e.printStackTrace();
                    plugin.sendError(commandSender, e.toString());
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
                    json = plugin.readToString(file);
                } catch (IOException e) {
                    plugin.sendError(commandSender, e.toString());
                    e.printStackTrace();
                    return;
                }

                final boolean added;

                try {
                    added = plugin.getDeviceInfoStorage().insertOrUpdateByQq(qq, json);
                } catch (Exception e) {
                    e.printStackTrace();
                    plugin.sendError(commandSender, e.toString());
                    return;
                }

                plugin.sendInfo(commandSender, "%s成功，已导入QQ[%d]的设备信息"
                        .formatted(added ? "添加" : "更新", qq));
            });
            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            return null;
        }
    }
}
