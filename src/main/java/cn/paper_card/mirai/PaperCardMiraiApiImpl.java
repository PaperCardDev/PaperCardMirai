package cn.paper_card.mirai;

import cn.paper_card.database.api.DatabaseApi;
import cn.paper_card.paper_card_mirai.api.PaperCardMiraiApi;
import org.jetbrains.annotations.NotNull;

class PaperCardMiraiApiImpl implements PaperCardMiraiApi {

    private final @NotNull QqAccountServiceImpl qqAccountService;
    private final @NotNull DeviceInfoServiceImpl deviceInfoService;

    PaperCardMiraiApiImpl(@NotNull DatabaseApi.MySqlConnection connection) {
        this.deviceInfoService = new DeviceInfoServiceImpl(connection);
        this.qqAccountService = new QqAccountServiceImpl(connection);
    }

    @Override
    public @NotNull QqAccountServiceImpl getQqAccountService() {
        return this.qqAccountService;
    }

    @Override
    public @NotNull DeviceInfoServiceImpl getDeviceInfoService() {
        return this.deviceInfoService;
    }
}
