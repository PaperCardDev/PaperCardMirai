package cn.paper_card.mirai;


import cn.paper_card.database.api.DatabaseApi;
import cn.paper_card.database.api.Parser;
import cn.paper_card.database.api.Util;
import cn.paper_card.paper_card_mirai.api.DeviceInfo;
import cn.paper_card.paper_card_mirai.api.DeviceInfoService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

class DeviceInfoServiceImpl implements DeviceInfoService {

    private final @NotNull DatabaseApi.MySqlConnection mySqlConnection;

    private Connection connection = null;

    private Table table = null;

    DeviceInfoServiceImpl(@NotNull DatabaseApi.MySqlConnection connection) {
        this.mySqlConnection = connection;
    }

    @NotNull Table getTable() throws SQLException {
        final Connection newCon = this.mySqlConnection.getRawConnection();

        if (this.connection != null && this.connection == newCon) return this.table;

        this.connection = newCon;
        if (this.table != null) this.table.close();
        this.table = new Table(newCon);
        return this.table;
    }

    void close() throws SQLException {
        synchronized (this.mySqlConnection) {
            final Table t = this.table;

            if (t == null) {
                this.connection = null;
                return;
            }

            this.connection = null;
            this.table = null;

            t.close();
        }
    }

    @Override
    public @Nullable DeviceInfo queryByQq(long qq) throws SQLException {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();
                final DeviceInfo info = t.queryByQq(qq);
                this.mySqlConnection.setLastUseTime();
                return info;
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Override
    public boolean insertOrUpdateByQq(@NotNull DeviceInfo info) throws SQLException {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();
                final int update = t.update(info);
                this.mySqlConnection.setLastUseTime();

                if (update == 1) return false;
                if (update == 0) {
                    final int inserted = t.insert(info);
                    this.mySqlConnection.setLastUseTime();
                    if (inserted != 1) throw new RuntimeException("插入了%d条数据！".formatted(inserted));
                    return true;
                }

                throw new RuntimeException("根据一个QQ更新了%d条数据！".formatted(update));
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }


    @Override
    public boolean deleteByQq(long qq) throws SQLException {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();
                final int deleted = t.delete(qq);
                this.mySqlConnection.setLastUseTime();
                if (deleted == 1) return true;
                if (deleted == 0) return false;
                throw new RuntimeException("根据一个QQ[%d]删除了%d条数据！".formatted(qq, deleted));
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Override
    public boolean setRemark(long qq, @Nullable String remark) throws SQLException {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();
                final int updated = t.setRemark(qq, remark);
                this.mySqlConnection.setLastUseTime();
                if (updated == 1) return true;
                if (updated == 0) return false;

                throw new RuntimeException("根据一个QQ[%d]更新了%d条数据！".formatted(qq, updated));
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Override
    public @NotNull List<Long> queryAllQqs() throws SQLException {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();
                final List<Long> list = t.queryAllQqs();
                this.mySqlConnection.setLastUseTime();
                return list;
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Override
    public @NotNull List<DeviceInfo> queryAllWithPage(int limit, int offset) throws SQLException {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();
                final List<DeviceInfo> list = t.queryWithPage(limit, offset);
                this.mySqlConnection.setLastUseTime();
                return list;
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Override
    public boolean copyInfo(long src, long dest) throws SQLException {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();
                final int inserted = t.copyInfo(src, dest);
                this.mySqlConnection.setLastUseTime();
                if (inserted == 1) return true;
                if (inserted == 0) return false;
                throw new RuntimeException("插入了%d条数据！".formatted(inserted));
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    static class Table extends Parser<DeviceInfo> {

        private final static String NAME = "device";

        private final @NotNull Connection connection;

        private PreparedStatement statementInsert = null;
        private PreparedStatement statementUpdate = null;

        private PreparedStatement statementQueryDevice = null;

        private PreparedStatement statementDelete = null;

        private PreparedStatement statementSetRemark = null;

        private PreparedStatement statementQueryAllQqs = null;

        private PreparedStatement statementQueryAllWithPage = null;

        private PreparedStatement statementCopyInfo = null;

        Table(@NotNull Connection connection) throws SQLException {
            this.connection = connection;
            this.create();
        }

        private void create() throws SQLException {
            Util.executeSQL(this.connection, """
                    CREATE TABLE IF NOT EXISTS %s (
                        qq BIGINT PRIMARY KEY NOT NULL,
                        device VARCHAR(2048) NOT NULL,
                        remark VARCHAR(24)
                    )""".formatted(NAME));
        }

        void close() throws SQLException {
            Util.closeAllStatements(this.getClass(), this);
        }

        private @NotNull PreparedStatement getStatementInsert() throws SQLException {
            if (this.statementInsert == null) {
                this.statementInsert = this.connection.prepareStatement
                        ("INSERT INTO %s (qq, device, remark) VALUES (?, ?, ?)".formatted(NAME));
            }
            return this.statementInsert;
        }

        private @NotNull PreparedStatement getStatementUpdate() throws SQLException {
            if (this.statementUpdate == null) {
                this.statementUpdate = connection.prepareStatement
                        ("UPDATE %s SET device=?, remark=? WHERE qq=? LIMIT 1".formatted(NAME));
            }
            return this.statementUpdate;
        }

        private @NotNull PreparedStatement getStatementQueryDevice() throws SQLException {
            if (this.statementQueryDevice == null) {
                this.statementQueryDevice = this.connection.prepareStatement
                        ("SELECT qq, device, remark FROM %s WHERE qq=? LIMIT 1".formatted(NAME));
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

        private @NotNull PreparedStatement getStatementSetRemark() throws SQLException {
            if (this.statementSetRemark == null) {
                this.statementSetRemark = this.connection.prepareStatement
                        ("UPDATE %s SET remark=? WHERE qq=?".formatted(NAME));
            }
            return this.statementSetRemark;
        }

        private @NotNull PreparedStatement getStatementQueryAllQqs() throws SQLException {
            if (this.statementQueryAllQqs == null) {
                this.statementQueryAllQqs = this.connection.prepareStatement
                        ("SELECT qq FROM %s".formatted(NAME));
            }
            return this.statementQueryAllQqs;
        }

        private @NotNull PreparedStatement getStatementQueryAllWithPage() throws SQLException {
            if (this.statementQueryAllWithPage == null) {
                this.statementQueryAllWithPage = this.connection.prepareStatement
                        ("SELECT qq,device,remark FROM %s LIMIT ? OFFSET ?".formatted(NAME));
            }
            return this.statementQueryAllWithPage;
        }

        private @NotNull PreparedStatement getStatementCopyInfo() throws SQLException {
            if (this.statementCopyInfo == null) {
                this.statementCopyInfo = this.connection.prepareStatement
                        ("INSERT INTO %s (qq,device,remark) (SELECT ?,device,remark FROM %s WHERE qq=?)"
                                .formatted(NAME, NAME));
            }
            return this.statementCopyInfo;
        }

        int insert(@NotNull DeviceInfo info) throws SQLException {
            final PreparedStatement ps = this.getStatementInsert();
            ps.setLong(1, info.qq());
            ps.setString(2, info.json());
            ps.setString(3, info.remark());
            return ps.executeUpdate();
        }

        int update(@NotNull DeviceInfo info) throws SQLException {
            final PreparedStatement ps = this.getStatementUpdate();
            ps.setString(1, info.json());
            ps.setString(2, info.remark());
            ps.setLong(3, info.qq());
            return ps.executeUpdate();
        }

        int delete(long qq) throws SQLException {
            final PreparedStatement ps = this.getStatementDelete();
            ps.setLong(1, qq);
            return ps.executeUpdate();
        }

        int setRemark(long qq, @Nullable String remark) throws SQLException {
            final PreparedStatement ps = this.getStatementSetRemark();
            ps.setString(1, remark);
            ps.setLong(2, qq);
            return ps.executeUpdate();
        }

        @Nullable DeviceInfo queryByQq(long qq) throws SQLException {
            final PreparedStatement ps = this.getStatementQueryDevice();

            ps.setLong(1, qq);

            final ResultSet resultSet = ps.executeQuery();

            return this.parseOne(resultSet);
        }

        @NotNull List<Long> queryAllQqs() throws SQLException {
            final PreparedStatement ps = this.getStatementQueryAllQqs();
            final ResultSet resultSet = ps.executeQuery();


            return new Parser<Long>() {
                @Override
                public @NotNull Long parseRow(@NotNull ResultSet resultSet) throws SQLException {
                    return resultSet.getLong(1);
                }
            }.parseAll(resultSet);
        }

        @Override
        public @NotNull DeviceInfo parseRow(@NotNull ResultSet resultSet) throws SQLException {
            final long qq = resultSet.getLong(1);
            final String json = resultSet.getString(2);
            final String remark = resultSet.getString(3);
            return new DeviceInfo(qq, json, remark);
        }

        @NotNull List<DeviceInfo> queryWithPage(int limit, int offset) throws SQLException {
            final PreparedStatement ps = this.getStatementQueryAllWithPage();
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            final ResultSet resultSet = ps.executeQuery();
            return this.parseAll(resultSet);
        }

        int copyInfo(long src, long dest) throws SQLException {
            final PreparedStatement ps = this.getStatementCopyInfo();
            ps.setLong(1, dest);
            ps.setLong(2, src);
            return ps.executeUpdate();
        }
    }
}
