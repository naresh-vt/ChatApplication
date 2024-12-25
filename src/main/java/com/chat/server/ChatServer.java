package com.chat.server;

import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import com.google.gson.Gson;
import java.io.*;
import java.util.*;

@ServerEndpoint("/chat")
public class ChatServer {
    // Store active user sessions and their usernames
    private static Map<Session, String> users = new HashMap<>();

    // Store groups and their members (groupName -> set of usernames)
    private static Map<String, Set<String>> groups = new HashMap<>();

    // Store which groups each user belongs to (username -> set of groupNames)
    private static Map<String, Set<String>> userGroups = new HashMap<>();

    // Gson instance for JSON handling
    private static Gson gson = new Gson();

    @OnOpen
    public void onOpen(Session session) {
        // When a new WebSocket connection is established, store the session with a null username
        users.put(session, null);
        System.out.println("New WebSocket connection established");
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        // Parse incoming message using Gson
        MessageData data = gson.fromJson(message, MessageData.class);
        System.out.println("Received message of type: " + data.type);

        // Handle different types of messages
        switch (data.type) {
            case "login":
                handleLogin(session, data.content);
                break;
            case "createGroup":
                System.out.println("Creating group: " + data.content + " with members: " + data.groupMembers);
                handleCreateGroup(data.content, data.groupMembers);
                break;
            case "message":
                handleMessage(session, data);
                break;
        }
    }

    private void handleLogin(Session session, String username) {
        // Store the username for this session
        users.put(session, username);

        // Initialize empty set of groups for new user
        userGroups.put(username, new HashSet<>());

        // Broadcast updated user list to all connected clients
        broadcastUserList();
        System.out.println("User logged in: " + username);
    }

    private void handleCreateGroup(String groupName, List<String> members) {
        System.out.println("Creating group: " + groupName + " with members: " + members);

        if (!groups.containsKey(groupName)) {
            // Create new group with provided members
            Set<String> memberSet = new HashSet<>(members);
            groups.put(groupName, memberSet);

            // Add group to each member's group list
            for (String member : members) {
                if (!userGroups.containsKey(member)) {
                    userGroups.put(member, new HashSet<>());
                }
                userGroups.get(member).add(groupName);
            }

            // Update group lists for all members
            broadcastGroupList(members);

            // Send notification about group creation
            String notification = "Group '" + groupName + "' has been created with members: " +
                    String.join(", ", members);
            sendGroupMessage(groupName, "System", notification);

            System.out.println("Group created successfully: " + groupName);
            System.out.println("Current groups: " + groups);
            System.out.println("User groups: " + userGroups);
        } else {
            System.out.println("Group already exists: " + groupName);
        }
    }

    private void handleMessage(Session session, MessageData data) {
        String sender = users.get(session);

        if (data.recipient.startsWith("group:")) {
            // Handle group message
            String groupName = data.recipient.substring(6); // Remove "group:" prefix
            if (groups.containsKey(groupName) && groups.get(groupName).contains(sender)) {
                sendGroupMessage(groupName, sender, data.content);
            }
        } else {
            // Handle private message
            sendPrivateMessage(sender, data.recipient, data.content);
        }
    }

    private void sendGroupMessage(String groupName, String sender, String message) {
        MessageData msgData = new MessageData("group", sender, groupName, message);
        String groupMsg = gson.toJson(msgData);

        // Send message to all group members
        for (Map.Entry<Session, String> entry : users.entrySet()) {
            String username = entry.getValue();
            if (groups.get(groupName).contains(username)) {
                try {
                    entry.getKey().getBasicRemote().sendText(groupMsg);
                } catch (IOException e) {
                    System.err.println("Error sending group message: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    private void sendPrivateMessage(String sender, String recipient, String message) {
        MessageData msgData = new MessageData("private", sender, recipient, message);
        String privateMsg = gson.toJson(msgData);

        // Send to recipient
        sendToUser(recipient, privateMsg);
        // Send to sender (so they can see their own message)
        sendToUser(sender, privateMsg);
    }

    private void sendToUser(String username, String message) {
        for (Map.Entry<Session, String> entry : users.entrySet()) {
            if (username.equals(entry.getValue())) {
                try {
                    entry.getKey().getBasicRemote().sendText(message);
                } catch (IOException e) {
                    System.err.println("Error sending private message: " + e.getMessage());
                    e.printStackTrace();
                }
                break;
            }
        }
    }

    private void broadcastUserList() {
        List<String> userList = new ArrayList<>(users.values());
        userList.removeIf(Objects::isNull);
        MessageData msgData = new MessageData("userList", "", "", String.join(",", userList));
        broadcast(gson.toJson(msgData));
    }

    private void broadcastGroupList(List<String> members) {
        for (String member : members) {
            Session userSession = null;
            // Find session for this member
            for (Map.Entry<Session, String> entry : users.entrySet()) {
                if (member.equals(entry.getValue())) {
                    userSession = entry.getKey();
                    break;
                }
            }

            if (userSession != null) {
                Set<String> userGroupSet = userGroups.get(member);
                MessageData msgData = new MessageData("groupList", "", "", String.join(",", userGroupSet));
                try {
                    userSession.getBasicRemote().sendText(gson.toJson(msgData));
                } catch (IOException e) {
                    System.err.println("Error broadcasting group list: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    private void broadcast(String message) {
        for (Session session : users.keySet()) {
            if (users.get(session) != null) {
                try {
                    session.getBasicRemote().sendText(message);
                } catch (IOException e) {
                    System.err.println("Error broadcasting message: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    @OnClose
    public void onClose(Session session) {
        String username = users.get(session);
        if (username != null) {
            // Remove user from their groups
            userGroups.remove(username);
            // Remove user from all groups they were a member of
            for (Set<String> groupMembers : groups.values()) {
                groupMembers.remove(username);
            }
        }
        // Remove user session
        users.remove(session);
        // Update user list for remaining users
        broadcastUserList();
        System.out.println("User disconnected: " + username);
    }
}

class MessageData {
    String type;            // Type of message (login, createGroup, message, etc.)
    String sender;          // Username of sender
    String recipient;       // Username of recipient or group name
    String content;         // Message content
    List<String> groupMembers;  // List of members when creating a group

    // Constructor for regular messages
    public MessageData(String type, String sender, String recipient, String content) {
        this.type = type;
        this.sender = sender;
        this.recipient = recipient;
        this.content = content;
        this.groupMembers = new ArrayList<>();
    }

    // Constructor for group creation
    public MessageData(String type, String content, List<String> groupMembers) {
        this.type = type;
        this.content = content;
        this.groupMembers = groupMembers;
        this.sender = "";
        this.recipient = "";
    }
}

