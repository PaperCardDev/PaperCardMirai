package cn.paper_card.mirai;

import cn.paper_card.database.DatabaseApi;
import cn.paper_card.database.DatabaseConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

class DeviceInfoStorageImpl implements PaperCardMiraiApi.DeviceInfoStorage {

    private final @NotNull DatabaseApi.MySqlConnection mySqlConnection;

    private Connection connection = null;

    private Table table = null;

    DeviceInfoStorageImpl(@NotNull PaperCardMirai plugin) {
        this.mySqlConnection = plugin.getMySqlConnection();
    }

    @NotNull Table getTable() throws SQLException {
        final Connection newCon = this.mySqlConnection.getRowConnection();
        if (this.connection == null) {
            this.connection = newCon;
            if (this.table != null) this.table.close();
            this.table = new Table(this.connection);
            return this.table;
        } else if (this.connection == newCon) {
            // 连接没有变化
            return this.table;
        } else {
            // 连接变化
            this.connection = newCon;
            if (this.table != null) this.table.close();
            this.table = new Table(this.connection);
            return this.table;
        }
    }

    @Override
    public @Nullable String queryByQq(long qq) throws Exception {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();
                final String device = t.queryDevice(qq);
                this.mySqlConnection.setLastUseTime();
                return device;
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
    public boolean insertOrUpdateByQq(long qq, @NotNull String json) throws Exception {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();
                final int update = t.update(qq, json);
                this.mySqlConnection.setLastUseTime();

                if (update == 1) return false;
                if (update == 0) {
                    final int inserted = t.insert(qq, json);
                    this.mySqlConnection.setLastUseTime();
                    if (inserted != 1) throw new Exception("插入了%d条数据！".formatted(inserted));
                    return true;
                }

                throw new Exception("根据一个QQ更新了%d条数据！".formatted(update));
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
                final int deleted = t.delete(qq);
                this.mySqlConnection.setLastUseTime();
                if (deleted == 1) return true;
                if (deleted == 0) return false;
                throw new Exception("根据一个QQ[%d]删除了%d条数据！".formatted(qq, deleted));
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.checkClosedException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    static class Table {

        private final static String NAME = "device";

        private final @NotNull Connection connection;

        private PreparedStatement statementInsert = null;
        private PreparedStatement statementUpdate = null;

        private PreparedStatement statementQueryDevice = null;

        private PreparedStatement statementDelete = null;

        Table(@NotNull Connection connection) throws SQLException {
            this.connection = connection;
            this.create();
        }

        private void create() throws SQLException {
            DatabaseConnection.createTable(this.connection, """
                    CREATE TABLE IF NOT EXISTS %s (
                        qq BIGINT PRIMARY KEY NOT NULL,
                        device VARCHAR(2048) NOT NULL
                    )""".formatted(NAME));
        }

        void close() throws SQLException {
            DatabaseConnection.closeAllStatements(this.getClass(), this);
        }

        private @NotNull PreparedStatement getStatementInsert() throws SQLException {
            if (this.statementInsert == null) {
                this.statementInsert = this.connection.prepareStatement
                        ("INSERT INTO %s (qq, device) VALUES (?, ?)".formatted(NAME));
            }
            return this.statementInsert;
        }

        private @NotNull PreparedStatement getStatementUpdate() throws SQLException {
            if (this.statementUpdate == null) {
                this.statementUpdate = connection.prepareStatement
                        ("UPDATE %s SET device=? WHERE qq=?".formatted(NAME));
            }
            return this.statementUpdate;
        }

        private @NotNull PreparedStatement getStatementQueryDevice() throws SQLException {
            if (this.statementQueryDevice == null) {
                this.statementQueryDevice = this.connection.prepareStatement
                        ("SELECT device FROM %s WHERE qq=?".formatted(NAME));
            }
            return this.statementQueryDevice;
        }

        private @NotNull PreparedStatement getStatementDelete() throws SQLException {
            if (this.statementDelete == null) {
                this.statementDelete = this.connection.prepareStatement
                        ("DELETE FROM %s WHERE qq=?".formatted(NAME));
            }
            return this.statementDelete;
        }

        int insert(long qq, @NotNull String device) throws SQLException {
            final PreparedStatement ps = this.getStatementInsert();
            ps.setLong(1, qq);
            ps.setString(2, device);
            return ps.executeUpdate();
        }

        int update(long qq, @NotNull String device) throws SQLException {
            final PreparedStatement ps = this.getStatementUpdate();
            ps.setString(1, device);
            ps.setLong(2, qq);
            return ps.executeUpdate();
        }

        int delete(long qq) throws SQLException {
            final PreparedStatement ps = this.getStatementDelete();
            ps.setLong(1, qq);
            return ps.executeUpdate();
        }

        @Nullable String queryDevice(long qq) throws SQLException {
            final PreparedStatement ps = this.getStatementQueryDevice();

            ps.setLong(1, qq);

            final ResultSet resultSet = ps.executeQuery();

            final String device;

            try {
                if (resultSet.next()) {
                    device = resultSet.getString(1);
                } else device = null;

                if (resultSet.next()) throw new SQLException("不应该还有更多数据！");

            } catch (SQLException e) {
                try {
                    resultSet.close();
                } catch (SQLException ignored) {
                }
                throw e;
            }

            resultSet.close();

            return device;
        }
    }
}
