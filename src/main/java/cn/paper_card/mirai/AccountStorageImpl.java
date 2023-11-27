package cn.paper_card.mirai;

import cn.paper_card.database.DatabaseApi;
import cn.paper_card.database.DatabaseConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

class AccountStorageImpl implements PaperCardMiraiApi.AccountStorage {

    private Table table = null;

    private Connection connection = null;

    private final @NotNull DatabaseApi.MySqlConnection mySqlConnection;

    AccountStorageImpl(@NotNull PaperCardMirai plugin) {
        this.mySqlConnection = plugin.getMySqlConnection();
    }

    @NotNull Table getTable() throws SQLException {
        final Connection newCon = this.mySqlConnection.getRowConnection();

        if (this.connection != null && this.connection == newCon) return this.table;

        this.connection = newCon;
        if (this.table != null) this.table.close();
        this.table = new Table(newCon);
        return this.table;
    }

    void closeTable() throws SQLException {
        synchronized (this.mySqlConnection) {
            final Table t = this.table;

            if (t == null) {
                this.connection = null;
                return;
            }

            this.table = null;
            this.connection = null;
            t.close();
        }
    }

    @Override
    public boolean addOrUpdateByQq(@NotNull PaperCardMiraiApi.AccountInfo info) throws Exception {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();
                final int updated = t.updateByQq(info);
                this.mySqlConnection.setLastUseTime();

                if (updated == 1) return false;
                if (updated == 0) {
                    final int inserted = t.insert(info);
                    this.mySqlConnection.setLastUseTime();
                    if (inserted != 1) throw new Exception("插入了%d条数据！".formatted(inserted));
                    return true;
                }

                throw new Exception("根据一个QQ[%d]更新了%d条数据！".formatted(info.qq(), updated));

            } catch (SQLException e) {
                try {
                    this.mySqlConnection.checkClosedException(e);
                } catch (SQLException ignored) {
                }

                throw e;
            }
        }
    }

    @Override
    public boolean deleteByQq(long qq) throws Exception {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();
                final int updated = t.deleteByQq(qq);
                this.mySqlConnection.setLastUseTime();
                if (updated == 1) return true;
                if (updated == 0) return false;
                throw new Exception("根据一个QQ[%d]更新了%d条数据！".formatted(qq, updated));
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.checkClosedException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Override
    public boolean setAutoLogin(long qq, boolean autoLogin) throws Exception {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();
                final int updated = t.setAutoLogin(qq, autoLogin);
                this.mySqlConnection.setLastUseTime();

                if (updated == 1) return true;
                if (updated == 0) return false;
                throw new Exception("根据一个QQ[%d]更新了%d条数据！".formatted(qq, updated));
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.checkClosedException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Override
    public boolean setRemark(long qq, String remark) throws Exception {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();
                final int updated = t.setRemark(qq, remark);
                this.mySqlConnection.setLastUseTime();

                if (updated == 1) return true;
                if (updated == 0) return false;
                throw new Exception("根据一个QQ[%d]更新了%d条数据！".formatted(qq, updated));
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.checkClosedException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Override
    public @Nullable PaperCardMiraiApi.AccountInfo queryByQq(long qq) throws Exception {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();
                final List<PaperCardMiraiApi.AccountInfo> list = t.queryByQq(qq);
                this.mySqlConnection.setLastUseTime();

                final int size = list.size();
                if (size == 1) return list.get(0);
                if (size == 0) return null;

                throw new Exception("根据一个QQ[%d]查询到了%d条数据！".formatted(qq, size));

            } catch (SQLException e) {
                try {
                    this.mySqlConnection.checkClosedException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Override
    public @NotNull List<Long> queryAllQqs() throws Exception {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();
                final List<Long> list = t.queryAllQqs();
                this.mySqlConnection.setLastUseTime();
                return list;
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.checkClosedException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Override
    public @NotNull List<PaperCardMiraiApi.AccountInfo> queryAutoLoginAccounts() throws Exception {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();
                final List<PaperCardMiraiApi.AccountInfo> list = t.queryAutoLogins();
                this.mySqlConnection.setLastUseTime();
                return list;
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.checkClosedException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Override
    public @NotNull List<PaperCardMiraiApi.AccountInfo> queryOrderByTimeDescWithPage(int limit, int offset) throws Exception {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();
                final List<PaperCardMiraiApi.AccountInfo> list = t.queryAllOrderByTimeDescWithPage(limit, offset);
                this.mySqlConnection.setLastUseTime();
                return list;
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.checkClosedException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    private static class Table {
        private final static String NAME = "qq_bot";

        private PreparedStatement statementInsert = null;
        private PreparedStatement statementUpdateByQq = null;

        private PreparedStatement statementDeleteByQq = null;

        private PreparedStatement statementSetAutoLogin = null;

        private PreparedStatement statementSetRemark = null;

        private PreparedStatement statementQueryByQq = null;

        private PreparedStatement statementQueryAllQqs = null;

        private PreparedStatement statementQueryAutoLogins = null;

        private PreparedStatement statementQueryAllOrderByTimeDescWithPage = null;

        private final @NotNull Connection connection;

        private final static String ALL_COLS = "qq,nick,level,pwd_md5,protocol,time,ip,auto_login,remark";

        Table(@NotNull Connection connection) throws SQLException {
            this.create(connection);
            this.connection = connection;
        }

        private void create(@NotNull Connection connection) throws SQLException {
            DatabaseConnection.createTable(connection, """
                    CREATE TABLE IF NOT EXISTS %s (
                        qq BIGINT PRIMARY KEY NOT NULL,
                        nick VARCHAR(64) NOT NULL,
                        level INT NOT NULL,
                        pwd_md5 CHAR(48) NOT NULL,
                        protocol CHAR(24) NOT NULL,
                        time BIGINT NOT NULL,
                        ip CHAR(24) NOT NULL,
                        auto_login INT NOT NULL,
                        remark VARCHAR(24) NOT NULL
                    )""".formatted(NAME));
        }

        void close() throws SQLException {
            DatabaseConnection.closeAllStatements(this.getClass(), this);
        }

        private @NotNull PreparedStatement getStatementInsert() throws SQLException {
            if (this.statementInsert == null) {
                this.statementInsert = this.connection.prepareStatement
                        ("INSERT INTO %s (%s) VALUES (?,?,?,?,?,?,?,?,?)".formatted(NAME, ALL_COLS));
            }
            return this.statementInsert;
        }

        private @NotNull PreparedStatement getStatementUpdateByQq() throws SQLException {
            if (this.statementUpdateByQq == null) {
                this.statementUpdateByQq = this.connection.prepareStatement
                        ("UPDATE %s SET nick=?,level=?,pwd_md5=?,protocol=?,time=?,ip=?,auto_login=?,remark=? WHERE qq=?".formatted(NAME));
            }
            return this.statementUpdateByQq;
        }

        private @NotNull PreparedStatement getStatementDeleteByQq() throws SQLException {
            if (this.statementDeleteByQq == null) {
                this.statementDeleteByQq = this.connection.prepareStatement
                        ("DELETE FROM %s WHERE qq=?".formatted(NAME));
            }
            return this.statementDeleteByQq;
        }

        private @NotNull PreparedStatement getStatementSetAutoLogin() throws SQLException {
            if (this.statementSetAutoLogin == null) {
                this.statementSetAutoLogin = this.connection.prepareStatement
                        ("UPDATE %s SET auto_login=? WHERE qq=?".formatted(NAME));
            }
            return this.statementSetAutoLogin;
        }

        private @NotNull PreparedStatement getStatementSetRemark() throws SQLException {
            if (this.statementSetRemark == null) {
                this.statementSetRemark = this.connection.prepareStatement
                        ("UPDATE %s SET remark=? WHERE qq=?".formatted(NAME));
            }
            return this.statementSetRemark;
        }

        private @NotNull PreparedStatement getStatementQueryByQq() throws SQLException {
            if (this.statementQueryByQq == null) {
                this.statementQueryByQq = this.connection.prepareStatement
                        ("SELECT %s FROM %s WHERE qq=?".formatted(ALL_COLS, NAME));
            }
            return this.statementQueryByQq;
        }

        private @NotNull PreparedStatement getStatementQueryAllQqs() throws SQLException {
            if (this.statementQueryAllQqs == null) {
                this.statementQueryAllQqs = connection.prepareStatement
                        ("SELECT qq FROM %s".formatted(NAME));
            }
            return this.statementQueryAllQqs;
        }


        private @NotNull PreparedStatement getStatementQueryAutoLogins() throws SQLException {
            if (this.statementQueryAutoLogins == null) {
                this.statementQueryAutoLogins = this.connection.prepareStatement
                        ("SELECT %s FROM %s WHERE auto_login=?".formatted(ALL_COLS, NAME));
            }
            return this.statementQueryAutoLogins;
        }

        private @NotNull PreparedStatement getStatementQueryAllOrderByTimeDescWithPage() throws SQLException {
            if (this.statementQueryAllOrderByTimeDescWithPage == null) {
                this.statementQueryAllOrderByTimeDescWithPage = this.connection.prepareStatement
                        ("SELECT %s FROM %s ORDER BY time DESC LIMIT ? OFFSET ?".formatted(ALL_COLS, NAME));
            }
            return this.statementQueryAllOrderByTimeDescWithPage;
        }

        int insert(@NotNull PaperCardMiraiApi.AccountInfo info) throws SQLException {
            final PreparedStatement ps = this.getStatementInsert();

            ps.setLong(1, info.qq());
            ps.setString(2, info.nick());
            ps.setInt(3, info.level());
            ps.setString(4, info.passwordMd5());
            ps.setString(5, info.protocol());
            ps.setLong(6, info.time());
            ps.setString(7, info.ip());
            ps.setInt(8, info.autoLogin() ? 1 : 0);
            ps.setString(9, info.remark());

            return ps.executeUpdate();
        }

        int updateByQq(@NotNull PaperCardMiraiApi.AccountInfo info) throws SQLException {
            final PreparedStatement ps = this.getStatementUpdateByQq();
//            ("UPDATE %s SET nick=?,level=?,pwd_md5=?,protocol=?,time=?,auto_login=? WHERE qq=?".formatted(NAME));
            ps.setString(1, info.nick());
            ps.setInt(2, info.level());
            ps.setString(3, info.passwordMd5());
            ps.setString(4, info.protocol());
            ps.setLong(5, info.time());
            ps.setString(6, info.ip());
            ps.setInt(7, info.autoLogin() ? 1 : 0);
            ps.setString(8, info.remark());

            ps.setLong(9, info.qq());

            return ps.executeUpdate();
        }

        int deleteByQq(long qq) throws SQLException {
            final PreparedStatement ps = this.getStatementDeleteByQq();
            ps.setLong(1, qq);
            return ps.executeUpdate();
        }

        int setAutoLogin(long qq, boolean autoLogin) throws SQLException {
            final PreparedStatement ps = this.getStatementSetAutoLogin();
            ps.setInt(1, autoLogin ? 1 : 0);
            ps.setLong(2, qq);
            return ps.executeUpdate();
        }

        int setRemark(long qq, @NotNull String remark) throws SQLException {
            final PreparedStatement ps = this.getStatementSetRemark();
            ps.setString(1, remark);
            ps.setLong(2, qq);
            return ps.executeUpdate();
        }

        @NotNull List<PaperCardMiraiApi.AccountInfo> parseAll(@NotNull ResultSet resultSet) throws SQLException {

            final List<PaperCardMiraiApi.AccountInfo> list = new LinkedList<>();

            try {
                while (resultSet.next()) {
                    final long qq = resultSet.getLong(1);
                    final String nick = resultSet.getString(2);
                    final int level = resultSet.getInt(3);
                    final String passwordMd5 = resultSet.getString(4);
                    final String protocol = resultSet.getString(5);
                    final long time = resultSet.getLong(6);
                    final String ip = resultSet.getString(7);
                    final int autoLogin = resultSet.getInt(8);
                    final String remark = resultSet.getString(9);

                    final PaperCardMiraiApi.AccountInfo info = new PaperCardMiraiApi.AccountInfo(
                            qq, nick, level, passwordMd5, protocol, time, ip, autoLogin != 0, remark
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

        @NotNull List<PaperCardMiraiApi.AccountInfo> queryByQq(long qq) throws SQLException {
            final PreparedStatement ps = this.getStatementQueryByQq();
            ps.setLong(1, qq);
            final ResultSet resultSet = ps.executeQuery();
            return this.parseAll(resultSet);
        }

        @NotNull List<Long> queryAllQqs() throws SQLException {
            final PreparedStatement ps = this.getStatementQueryAllQqs();

            final ResultSet resultSet = ps.executeQuery();

            final List<Long> list = new LinkedList<>();

            try {
                while (resultSet.next()) {
                    final long qq = resultSet.getLong(1);
                    list.add(qq);
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

        @NotNull List<PaperCardMiraiApi.AccountInfo> queryAutoLogins() throws SQLException {
            final PreparedStatement ps = this.getStatementQueryAutoLogins();
            ps.setInt(1, 1);
            final ResultSet resultSet = ps.executeQuery();
            return this.parseAll(resultSet);
        }

        @NotNull List<PaperCardMiraiApi.AccountInfo> queryAllOrderByTimeDescWithPage(int limit, int offset) throws SQLException {
            final PreparedStatement ps = this.getStatementQueryAllOrderByTimeDescWithPage();
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            final ResultSet resultSet = ps.executeQuery();
            return this.parseAll(resultSet);
        }
    }
}
