package com.aergonat.forgeslack.slack;

import allbegray.slack.SlackClientFactory;
import allbegray.slack.rtm.Event;
import allbegray.slack.rtm.SlackRealTimeMessagingClient;
import allbegray.slack.type.Authentication;
import allbegray.slack.type.Channel;
import allbegray.slack.type.User;
import allbegray.slack.webapi.SlackWebApiClient;
import allbegray.slack.webapi.method.chats.ChatPostMessageMethod;
import com.aergonat.forgeslack.ForgeSlack;
import com.aergonat.forgeslack.handler.SlackMessageHandler;
import net.minecraft.entity.player.EntityPlayer;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;

public class SlackRelay {
    private SlackWebApiClient api;
    private SlackRealTimeMessagingClient rtmapi;
    private String channel;
    private String slackToken;
    private String channelId;
    private Authentication auth;
    private HashMap<String, User> users;

    public SlackRelay(String channel, String slackToken) {
        this.channel = channel;
        this.slackToken = slackToken;
        users = new HashMap<>();
    }

    /**
     * Fetches and caches a usermap
     */
    private void fetchUsers() {
        List<User> userList = api.getUserList();
        for (User u : userList) {
            users.put(u.getId(), u);
        }
    }

    /**
     * Fetches and caches the channel id
     */
    private void fetchChannel() {
        List<Channel> channels = api.getChannelList();
        for (Channel c : channels) {
            String fullName = "#" + c.getName();
            if (fullName.equals(channel)) {
                channelId = c.getId();
                return;
            }
        }
    }

    /**
     * Connects the relay
     */
    public void startup() {
        api = SlackClientFactory.createWebApiClient(slackToken);
        rtmapi = SlackClientFactory.createSlackRealTimeMessagingClient(slackToken);
        rtmapi.addListener(Event.MESSAGE, new SlackMessageHandler());

        (new Thread(() -> {
            fetchChannel();
            fetchUsers();
            auth = api.auth();
            rtmapi.connect();
            sendMessage("Server is online");
        }, "ForgeSlack-Startup")).start();
    }

    /**
     * Shutdown the Relay
     */
    public void shutdown() {
        (new Thread(() -> {
            sendMessage("Server is now offline");

            if (rtmapi != null) {
                rtmapi.close();
            }

            if (api != null) {
                api.shutdown();
            }
        }, "ForgeSlack-Shutdown")).start();

    }

    /**
     * Sends a Slack Message
     *
     * @param txt - Message to be sent
     */
    public void sendMessage(String txt) {
        ChatPostMessageMethod message = new ChatPostMessageMethod(channel, txt);
        sendMessage(message);
    }


    /**
     * Sends a Slack Message from a EntityPlayer
     *
     * @param txt    - Message to be sent
     * @param player - Minecraft User
     */
    public void sendMessage(String txt, EntityPlayer player) {
        ChatPostMessageMethod message = new ChatPostMessageMethod(channel, txt);
        message.setUsername(player.getDisplayName().getUnformattedText());
        String uuid = player.getCachedUniqueIdString().replaceAll("-", "");
        message.setIcon_url(MessageFormat.format("https://crafatar.com/avatars/{0}", uuid));
        sendMessage(message);
    }

    /**
     * Sends a Slack Message in a new Thread
     *
     * @param message - Message to be sent
     */
    public void sendMessage(ChatPostMessageMethod message) {
        (new Thread(() -> {
            try {
                if (api != null) {
                    api.postMessage(message);
                } else {
                    ForgeSlack.logger.error(String.format("Tried to send slack message: '%s'. Slack Web API Client is not started.", message));
                }
            } catch (Exception e) {
                ForgeSlack.logger.error(e.getMessage());
            }
        }, "ForgeSlack-message")).start();
    }

    /**
     * @return Bot ID of the auth.
     */
    public String getBotId() {
        return auth.getUser_id();
    }

    /**
     * @return The channel id we are relaying.
     */
    public String getChannelId() {
        return channelId;
    }

    /**
     * @param userId - Slack userid of the user.
     * @return User from a userid.
     */
    public User getUsernameFromId(String userId) {
        User user = users.get(userId);

        if (user != null) {
            return user;
        }

        // Try one more time if we didn't find it by reloading the cache.
        fetchUsers();
        return users.get(userId);
    }
}
