package cn.paper_card.mirai;

import cn.paper_card.database.api.Parser;
import cn.paper_card.paper_card_mirai.api.AccountInfo;
import org.jetbrains.annotations.NotNull;

import java.sql.ResultSet;
import java.sql.SQLException;

class ParserAccountInfo extends Parser<AccountInfo> {
    @Override
    public @NotNull AccountInfo parseRow(@NotNull ResultSet resultSet) throws SQLException {
        final long qq = resultSet.getLong(1);
        final String nick = resultSet.getString(2);
        final int level = resultSet.getInt(3);
        final String passwordMd5 = resultSet.getString(4);
        final String protocol = resultSet.getString(5);
        final long time = resultSet.getLong(6);
        final String ip = resultSet.getString(7);
        final int autoLogin = resultSet.getInt(8);
        final String remark = resultSet.getString(9);

        return new AccountInfo(
                qq, nick, level, passwordMd5, protocol, time, ip, autoLogin != 0, remark
        );
    }
}