package com.modnmetl.modnvote.storage;

import java.sql.*;
import java.util.*;

public class VoteDao {
    private final Database db;

    public VoteDao(Database db) { this.db = db; }

    public void init() throws SQLException {
        try (Connection c = db.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("PRAGMA journal_mode=WAL");
            st.executeUpdate("PRAGMA synchronous=NORMAL");

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS votes_participation (
                  uuid TEXT NOT NULL,
                  ip TEXT NOT NULL,
                  bypass INTEGER NOT NULL DEFAULT 0,
                  created_at INTEGER NOT NULL,
                  round_id INTEGER NOT NULL DEFAULT 1,
                  UNIQUE (uuid, round_id)
                )
            """);
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_participation_ip_round ON votes_participation(ip, round_id)");

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS votes_tally (
                  round_id INTEGER PRIMARY KEY,
                  yes INTEGER NOT NULL,
                  no  INTEGER NOT NULL,
                  hmac TEXT NOT NULL
                )
            """);

            try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO votes_tally(round_id, yes, no, hmac) VALUES(1,0,0,'')")) {
                ps.executeUpdate();
            }
        }
    }

    public boolean hasUuidVoted(UUID uuid, int roundId) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM votes_participation WHERE uuid=? AND round_id=? LIMIT 1")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, roundId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public boolean hasIpVoted(String ip, int roundId) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM votes_participation WHERE ip=? AND round_id=? LIMIT 1")) {
            ps.setString(1, ip);
            ps.setInt(2, roundId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public void insertParticipation(UUID uuid, String ip, boolean bypass, int roundId) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO votes_participation(uuid, ip, bypass, created_at, round_id) VALUES(?,?,?,?,?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, ip);
            ps.setInt(3, bypass ? 1 : 0);
            ps.setLong(4, System.currentTimeMillis());
            ps.setInt(5, roundId);
            ps.executeUpdate();
        }
    }

    public int[] getTally(int roundId) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT yes,no FROM votes_tally WHERE round_id=?")) {
            ps.setInt(1, roundId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new int[]{rs.getInt(1), rs.getInt(2)};
                return new int[]{0,0};
            }
        }
    }

    public void updateTally(int roundId, int yes, int no, String hmacHex) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE votes_tally SET yes=?, no=?, hmac=? WHERE round_id=?")) {
            ps.setInt(1, yes);
            ps.setInt(2, no);
            ps.setString(3, hmacHex);
            ps.setInt(4, roundId);
            ps.executeUpdate();
        }
    }

    public String getStoredHmac(int roundId) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT hmac FROM votes_tally WHERE round_id=?")) {
            ps.setInt(1, roundId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString(1) : ""; }
        }
    }

    public List<UUID> fetchAllUuids(int roundId) throws SQLException {
        List<UUID> out = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT uuid FROM votes_participation WHERE round_id=?")) {
            ps.setInt(1, roundId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(UUID.fromString(rs.getString(1)));
            }
        }
        return out;
    }

    public int countParticipants(int roundId) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM votes_participation WHERE round_id=?")) {
            ps.setInt(1, roundId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        }
    }

    public int countBypass(int roundId) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM votes_participation WHERE round_id=? AND bypass=1")) {
            ps.setInt(1, roundId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        }
    }

    public static class VoteRow {
        public final UUID uuid; public final String ip; public final boolean bypass; public final long createdAt;
        public VoteRow(UUID uuid, String ip, boolean bypass, long createdAt) {
            this.uuid = uuid; this.ip = ip; this.bypass = bypass; this.createdAt = createdAt;
        }
    }

    public List<VoteRow> fetchAllForAudit(int roundId) throws SQLException {
        List<VoteRow> list = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT uuid, ip, bypass, created_at FROM votes_participation WHERE round_id=? ORDER BY ip, created_at")) {
            ps.setInt(1, roundId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new VoteRow(
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("ip"),
                        rs.getInt("bypass") == 1,
                        rs.getLong("created_at")));
                }
            }
        }
        return list;
    }

    public void resetAll(int roundId) throws SQLException {
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM votes_participation WHERE round_id=?")) {
                ps.setInt(1, roundId); ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE votes_tally SET yes=0,no=0,hmac='' WHERE round_id=?")) {
                ps.setInt(1, roundId); ps.executeUpdate();
            }
        }
    }
}
