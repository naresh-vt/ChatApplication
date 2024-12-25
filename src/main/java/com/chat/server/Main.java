package com.chat.server;

import org.glassfish.tyrus.server.Server;

public class Main {
    public static void main(String[] args) {
        Server server = new Server("localhost", 8080, "/", null, ChatServer.class);
        try {
            server.start();
            System.out.println("Chat server started on ws://localhost:8080/chat");
            Thread.currentThread().join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
