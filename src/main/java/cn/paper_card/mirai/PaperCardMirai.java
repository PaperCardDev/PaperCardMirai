package cn.paper_card.mirai;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.event.GlobalEventChannel;
import net.mamoe.mirai.event.events.FriendMessageEvent;
import org.bukkit.command.PluginCommand;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public final class PaperCardMirai extends JavaPlugin {

    private final @NotNull TheLoginSolver theLoginSolver;

    private final @NotNull TaskScheduler taskScheduler;

    public PaperCardMirai() {
        this.theLoginSolver = new TheLoginSolver(this);
        this.taskScheduler = UniversalScheduler.getScheduler(this);
    }


    @Override
    public void onEnable() {

        GlobalEventChannel.INSTANCE.subscribeAlways(FriendMessageEvent.class, friendMessageEvent -> friendMessageEvent.getSender().sendMessage("Hello"));

        final PluginCommand command = this.getCommand("paper-card-mirai");
        assert command != null;
        final TheCommand theCommand = new TheCommand(this);
        command.setExecutor(theCommand);
        command.setTabCompleter(theCommand);
    }

    @Override
    public void onDisable() {
        for (Bot instance : Bot.getInstances()) {
            instance.closeAndJoin(null);
        }
    }

    @NotNull Permission addPermission(@NotNull String name) {
        final Permission permission = new Permission(name);
        this.getServer().getPluginManager().addPermission(permission);
        return permission;
    }

    @NotNull TheLoginSolver getTheLoginSolver() {
        return this.theLoginSolver;
    }

    @NotNull TaskScheduler getTaskScheduler() {
        return this.taskScheduler;
    }
}
