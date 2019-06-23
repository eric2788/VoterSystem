package com.ericlam.mc.votesystem.counter;

import com.ericlam.mc.bungee.hnmc.main.HyperNiteMC;
import com.ericlam.mc.votesystem.mysql.VoteTable;

import javax.annotation.Nonnull;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

public class VoteMySQLManager {
    private static VoteMySQLManager voteMySQLManager;
    private VoteStatsManager voteStatsManager;
    private RedisCommitManager redisCommitManager;
    private VoteTable voteTable;

    public static VoteMySQLManager getInstance() {
        if (voteMySQLManager == null) voteMySQLManager = new VoteMySQLManager();
        return voteMySQLManager;
    }

    private VoteMySQLManager() {
        this.voteTable = new VoteTable();
        this.voteStatsManager = VoteStatsManager.getInstance();
        this.redisCommitManager = RedisCommitManager.getInstance();
    }

    @Nonnull
    public VoteStats getPlayerVote(UUID playerUniqueId){
        if (voteStatsManager.containVote(playerUniqueId)) return voteStatsManager.getVote(playerUniqueId);
        String stmt = voteTable.selectStatment(playerUniqueId);
        try(Connection connection = HyperNiteMC.getAPI().getSQLDataSource().getConnection();
            PreparedStatement statement = connection.prepareStatement(stmt)){
            ResultSet resultSet = statement.executeQuery();
            VoteStats stats;
            if (resultSet.next()){
                int vote = resultSet.getInt("Votes");
                long time = resultSet.getLong("TimeStamp");
                int queueVote = resultSet.getInt("Queued");
                stats = new VoteStats(vote, time, queueVote);
            }else{
                stats =  new VoteStats(0,0,0);
            }
            voteStatsManager.putVote(playerUniqueId,stats);
            redisCommitManager.commitStats(playerUniqueId, stats);
            return stats;
        } catch (SQLException e) {
            e.printStackTrace();
            return new VoteStats(0,0,0);
        }
    }

    public void saveVote(){
        Map<UUID, VoteStats> voteStatsMap = voteStatsManager.getStats();
        try(Connection connection = HyperNiteMC.getAPI().getSQLDataSource().getConnection()){
            for (UUID uuid : voteStatsMap.keySet()) {
                VoteStats stats = voteStatsMap.get(uuid);
                if (stats.isNotChanged()) continue;
                this.saveVote(connection, uuid, stats);
                stats.setChanged(false);
            }
            redisCommitManager.removeAll(voteStatsMap);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void saveVote(UUID playerUniqueId){
        VoteStats stats = voteStatsManager.getVote(playerUniqueId);
        try(Connection connection = HyperNiteMC.getAPI().getSQLDataSource().getConnection()){
            this.saveVote(connection, playerUniqueId, stats);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void saveVote(Connection connection, UUID playerUniqueId, VoteStats stats) throws SQLException {
        String insertPrepare = voteTable.prepareInsert();
        try(PreparedStatement statement = connection.prepareStatement(insertPrepare)){
            if (stats.isNotChanged()) return;
            voteTable.setPrepareStatment(statement,playerUniqueId,stats);
            statement.execute();
        }
    }

    public void createDatabase(){
        String createTable = voteTable.getCreateTableStatment();
        try(Connection connection = HyperNiteMC.getAPI().getSQLDataSource().getConnection();
        PreparedStatement statement = connection.prepareStatement(createTable)){
            statement.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateRedis(){
        redisCommitManager.commitStats(voteStatsManager.getStats());
    }

}