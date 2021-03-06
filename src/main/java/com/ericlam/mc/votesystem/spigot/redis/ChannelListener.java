package com.ericlam.mc.votesystem.spigot.redis;

import com.ericlam.mc.votesystem.spigot.VoteDataManager;
import com.ericlam.mc.votesystem.spigot.VoteHandler;
import com.ericlam.mc.votesystem.spigot.main.VoterSystemSpigot;
import org.bukkit.Bukkit;
import redis.clients.jedis.JedisPubSub;

import java.util.Arrays;
import java.util.UUID;

public class ChannelListener extends JedisPubSub {

    private final VoteDataManager voteDataManager;
    private final VoteHandler voteHandler;

    public ChannelListener(VoteDataManager dataManager) {
        this.voteDataManager = dataManager;
        this.voteHandler = new VoteHandler();
    }

    @Override
    public void onSubscribe(String channel, int subscribedChannels) {
        VoterSystemSpigot.debug("Subscribed Channel " + channel);
    }

    @Override
    public void onMessage(String channel, String message) {
        VoterSystemSpigot.debug("received redis messages: " + message);
        Bukkit.getScheduler().runTaskAsynchronously(VoterSystemSpigot.INSTANCE, () -> {
            String[] params = message.split("_");
            String method = params[0].toLowerCase();
            String[] args = Arrays.copyOfRange(params, 1, params.length);
            switch (method) {
                case "update":
                    if (args.length < 1) {
                        voteDataManager.getVoteDataFromRedis();
                    } else {
                        UUID uuid = UUID.fromString(args[0]);
                        voteDataManager.getVoteDataFromRedis(uuid);
                    }
                    break;
                case "reward":
                    if (args.length < 2) break;
                    UUID player = UUID.fromString(args[0]);
                    int votes;
                    try {
                        votes = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        break;
                    }
                    for (int i = 0; i < votes; i++) {
                        voteHandler.reward(player, voteDataManager.getRewardCommands());
                    }
                    break;
                default:
                    break;
            }
        });
    }
}
