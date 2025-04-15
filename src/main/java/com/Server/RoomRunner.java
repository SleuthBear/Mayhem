package com.Server;

import com.Connections.BufferResults;
import com.Connections.SendMessageResult;
import com.Connections.ServerSideConnection;
import com.Message;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.*;

import static com.Connections.BufferResults.MULTIPLE_MESSAGES;
import static com.Connections.BufferResults.ONE_MESSAGE;
import static com.Message.PURPOSE.*;

public class RoomRunner implements Runnable {
    private final Selector selector;
    private int numberOfClients = 0;
    private final HashMap<Integer, ServerSideConnection> connections = new HashMap<>();
    private final HashMap<Integer, String> publicKeys = new HashMap<>();

    public RoomRunner() throws IOException {
        selector = SelectorProvider.provider().openSelector();
    }

    @Override
    public void run() {
        List<ServerSideConnection> toRead = new ArrayList<>();
        while (true) {
            try {
                selector.select();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();
                if (!key.isValid()) continue;
                else if (key.isReadable()) {
                    toRead.add((ServerSideConnection) key.attachment());
                }
            }
            while (!toRead.isEmpty()) {
                List<ServerSideConnection> haveRead = new ArrayList<>();
                Set<Integer> toEject = new HashSet<>();
                for (ServerSideConnection connection : toRead) {
                    BufferResults result = null;
                    try {
                        result = connection.bufferMessage();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    if (result == ONE_MESSAGE || result == MULTIPLE_MESSAGES) {
                        Message msg = connection.pollMessage();
                        System.out.println("Received Message: \n" + msg.toString());
                        if (result != MULTIPLE_MESSAGES) {
                            haveRead.add(connection);
                        }
                        // Process the message that was just read in.
                        try {
                            toEject.addAll(processMessage(msg, connection));
                        } catch (IOException e) {
                            // todo error handling
                            throw new RuntimeException(e);
                        }
                    } else { // We need to read in more data from the network buffer to get the rest.
                        haveRead.add(connection);
                    }
                }
                for (ServerSideConnection connection : haveRead) {
                    toRead.remove(connection);
                }
                for (Integer i : toEject) {
                    connections.remove(i);
                }
            }
        }
    }

    public void addConnection(ServerSideConnection connection) {
        int id = ++numberOfClients;
        synchronized (connections) {
            connections.put(id, connection);
        }
        try {
            connection.socketChannel.register(selector, SelectionKey.OP_READ, connection);
            connection.sendMessage(new Message("", 0, id, ID_ASSIGNMENT));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // unblock
        selector.wakeup();
    }

    public Set<Integer> processMessage(Message msg, ServerSideConnection connection) throws IOException {
        Set<Integer> toEject = new HashSet<>();
        // Handle public key registration
        if(msg.purpose == Message.PURPOSE.PUBLIC_KEY_REGISTRATION) {
            // Echo the public keys of other users to the new client.
            connection.sendMessage(new Message(formatPublicKeys(), 0, msg.sender, Message.PURPOSE.PUBLIC_KEYS));
            synchronized (publicKeys) {
                publicKeys.put(msg.sender, msg.messageString);
            }
            // Send the new clients public key to all other users.
            for (Map.Entry<Integer, ServerSideConnection> entry : connections.entrySet()) {
                if (entry.getKey() != msg.sender) {
                    Message keyMessage = new Message(msg.messageString, msg.sender, entry.getKey(), Message.PURPOSE.PUBLIC_KEY_REGISTRATION);
                    entry.getValue().sendMessage(keyMessage);
                }
            }
        // Handle passing on sender key
        }else if(msg.purpose == JOIN_SENDER_KEY || msg.purpose == SENDER_KEY) {
            // Relay to the receiver.
            connections.get(msg.receiver).sendMessage(msg);
        /// Handle Text
        } else if(msg.purpose == Message.PURPOSE.TEXT) {
            if (msg.receiver == 0) {
                synchronized (connections) {
                    for (Map.Entry<Integer, ServerSideConnection> otherConnectionSet : connections.entrySet()) {
                        ServerSideConnection otherConnection = otherConnectionSet.getValue();
                        if (otherConnection != connection) {
                            try {
                                if (otherConnection.sendMessage(msg) == SendMessageResult.CLOSED) {
                                    toEject.add(otherConnectionSet.getKey());
                                }
                            } catch (IOException e) {
                                System.out.println("A message failed to send");
                            }
                        }
                    }
                }
            }
        }
        return toEject;
    }

    private String formatPublicKeys() {
        StringBuilder keys = new StringBuilder();
        for(Map.Entry<Integer, String> entry : publicKeys.entrySet()) {
            keys.append(String.valueOf(entry.getKey())).append(":").append(entry.getValue()).append("|");
        }
        return new String(keys);
    }
}
