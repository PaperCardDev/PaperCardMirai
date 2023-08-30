package cn.paper_card.mirai;

import cn.paper_card.database.DatabaseApi;
import cn.paper_card.database.DatabaseConnection;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

class QqAccountStorageService {

    private DatabaseConnection connection = null;
    private Table table = null;
    private final @NotNull JavaPlugin plugin;

    QqAccountStorageService(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private @NotNull DatabaseConnection getConnection() throws Exception {
        if (this.connection == null) {
            final Plugin database = this.plugin.getServer().getPluginManager().getPlugin("Database");
            if (database instanceof final DatabaseApi api) {
                this.connection = api.connectImportant();
            } else throw new Exception("Database插件未安装！");
        }
        return this.connection;
    }

    private @NotNull Table getTable() throws Exception {
        if (this.table == null) {
            this.table = new Table(this.getConnection().getConnection());
        }
        return this.table;
    }

    record AccountInfo(
            long qq, // QQ号码
            String passwordMd5, // 密码哈希
            String protocol, // 协议
            long time, // 最近登录的时间

            boolean autoLogin // 是否自动登录
    ) {
    }

    boolean addOrUpdateByQq(@NotNull AccountInfo info) throws Exception {
        synchronized (this) {
            final Table t = this.getTable();

            final int updated = t.updateByQq(info);

            if (updated == 0) {
                final int inserted = t.insert(info);
                if (inserted != 1) throw new Exception("插入了%d条数据！".formatted(inserted));
                return true;
            }

            if (updated == 1) return false;

            throw new Exception("根据一个QQ号更新了%d条数据！".formatted(updated));
        }
    }

    @NotNull List<AccountInfo> queryAutoLoginAccounts() throws Exception {
        synchronized (this) {
            final Table t = this.getTable();
            return t.queryAutoLoginAccounts();
        }
    }

    @NotNull List<Long> queryAllAccountsQq() throws Exception {
        synchronized (this) {
            final Table t = this.getTable();
            return t.queryAllQq();
        }
    }

    @Nullable AccountInfo queryByQq(long qq) throws Exception {
        synchronized (this) {
            final Table t = this.getTable();
            final List<AccountInfo> list = t.queryByQq(qq);
            final int size = list.size();

            if (size == 0) return null;

            if (size == 1) return list.get(0);

            throw new Exception("根据一个QQ号查询到了%d条数据！".formatted(size));
        }
    }

    boolean setAutoLogin(long qq, boolean autoLogin) throws Exception {
        synchronized (this) {
            final Table t = this.getTable();
            final int updated = t.updateAutoLogin(qq, autoLogin);
            if (updated == 0) return false;
            if (updated == 1) return true;
            throw new Exception("根据一个QQ号更新了%d条数据！".formatted(updated));
        }
    }

    void destroy() {
        synchronized (this) {
            if (this.table != null) {
                try {
                    this.table.close();
                } catch (SQLException e) {
                    this.plugin.getLogger().severe(e.toString());
                    e.printStackTrace();
                }
                this.table = null;
            }

            if (this.connection != null) {
                try {
                    this.connection.close();
                } catch (SQLException e) {
                    this.plugin.getLogger().severe(e.toString());
                    e.printStackTrace();
                }
                this.connection = null;
            }
        }
    }

    static class Table {
        private static final String NAME = "qq_account";

        private final PreparedStatement statementInsert;
        private final PreparedStatement statementUpdate;

        private final PreparedStatement statementQueryAutoLogin;

        private final PreparedStatement statementUpdateAutoLogin;

        private final PreparedStatement statementQueryAllQq;

        private final PreparedStatement statementQueryByQq;

        Table(@NotNull Connection connection) throws SQLException {
            this.create(connection);

            try {
                this.statementInsert = connection.prepareStatement
                        ("INSERT INTO %s (qq, password_md5, protocol, time, auto_login) VALUES (?, ?, ?, ?, ?)".formatted(NAME));

                this.statementUpdate = connection.prepareStatement
                        ("UPDATE %s SET password_md5=?, protocol=?, time=? WHERE qq=?".formatted(NAME));

                this.statementQueryAutoLogin = connection.prepareStatement
                        ("SELECT qq, password_md5, protocol, time, auto_login FROM %s WHERE auto_login=?".formatted(NAME));

                this.statementQueryByQq = connection.prepareStatement
                        ("SELECT qq, password_md5, protocol, time, auto_login FROM %s WHERE qq=?".formatted(NAME));

                this.statementQueryAllQq = connection.prepareStatement
                        ("SELECT qq FROM %s".formatted(NAME));

                this.statementUpdateAutoLogin = connection.prepareStatement
                        ("UPDATE %s SET auto_login=? WHERE qq=?".formatted(NAME));

            } catch (SQLException e) {
                try {
                    this.close();
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }

        private void create(@NotNull Connection connection) throws SQLException {
            final String sql = """
                    CREATE TABLE IF NOT EXISTS %s (
                        qq              INTEGER NOT NULL,
                        password_md5    CHAR(36) NOT NULL,
                        protocol        CHAR(24) NOT NULL,
                        time            INTEGER NOT NULL,
                        auto_login      INTEGER NOT NULL
                    )""".formatted(NAME);

            DatabaseConnection.createTable(connection, sql);
        }

        void close() throws SQLException {
            DatabaseConnection.closeAllStatements(this.getClass(), this);
        }

        int insert(@NotNull AccountInfo accountInfo) throws SQLException {
            final PreparedStatement ps = this.statementInsert;
            ps.setLong(1, accountInfo.qq());
            ps.setString(2, accountInfo.passwordMd5());
            ps.setString(3, accountInfo.protocol());
            ps.setLong(4, accountInfo.time());
            ps.setInt(5, accountInfo.autoLogin() ? 1 : 0);
            return ps.executeUpdate();
        }

        int updateByQq(@NotNull AccountInfo accountInfo) throws SQLException {
            // ("UPDATE %s SET password_md5=?, protocol=?, time=? WHERE qq=?".formatted(NAME));
            final PreparedStatement ps = this.statementUpdate;
            ps.setString(1, accountInfo.passwordMd5());
            ps.setString(2, accountInfo.protocol());
            ps.setLong(3, accountInfo.time());
            ps.setLong(4, accountInfo.qq());

            return ps.executeUpdate();
        }

        private @NotNull List<Long> parseQq(@NotNull ResultSet resultSet) throws SQLException {

            final LinkedList<Long> qqs = new LinkedList<>();

            try {
                while (resultSet.next()) {
                    final long qq = resultSet.getLong(1);
                    qqs.add(qq);
                }
            } catch (SQLException e) {
                try {
                    resultSet.close();
                } catch (SQLException ignored) {
                }

                throw e;
            }

            resultSet.close();

            return qqs;
        }

        private @NotNull List<AccountInfo> parseAll(@NotNull ResultSet resultSet) throws SQLException {
            // ("SELECT qq, password_md5, protocol, time, auto_login FROM %s WHERE auto_login=?".formatted(NAME));

            final LinkedList<AccountInfo> list = new LinkedList<>();

            try {
                while (resultSet.next()) {
                    final long qq = resultSet.getLong(1);
                    final String password_md5 = resultSet.getString(2);
                    final String protocol = resultSet.getString(3);
                    final long time = resultSet.getLong(4);
                    final boolean autoLogin = resultSet.getInt(5) != 0;

                    final AccountInfo info = new AccountInfo(
                            qq,
                            password_md5,
                            protocol,
                            time,
                            autoLogin
                    );

                    list.add(info);
                }

            } catch (SQLException e) {
                try {
                    resultSet.close();
                } catch (SQLException ignored) {
                }

                throw e;
            }

            resultSet.close();

            return list;
        }

        @NotNull List<AccountInfo> queryAutoLoginAccounts() throws SQLException {
            final PreparedStatement ps = this.statementQueryAutoLogin;
            ps.setInt(1, 1);
            final ResultSet resultSet = ps.executeQuery();
            return this.parseAll(resultSet);
        }

        @NotNull List<AccountInfo> queryByQq(long qq) throws SQLException {
            final PreparedStatement ps = this.statementQueryByQq;
            ps.setLong(1, qq);
            final ResultSet resultSet = ps.executeQuery();

            return this.parseAll(resultSet);
        }

        @NotNull List<Long> queryAllQq() throws SQLException {
            final ResultSet resultSet = this.statementQueryAllQq.executeQuery();
            return this.parseQq(resultSet);
        }

        int updateAutoLogin(long qq, boolean autoLogin) throws SQLException {
            final PreparedStatement ps = this.statementUpdateAutoLogin;
            ps.setInt(1, autoLogin ? 1 : 0);
            ps.setLong(2, qq);
            return ps.executeUpdate();
        }

    }
}
