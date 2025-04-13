package com.Server;

import com.Connections.BufferResults;
import com.Connections.ServerSideConnection;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.Connections.BufferResults.MULTIPLE_MESSAGES;
import static com.Connections.BufferResults.ONE_MESSAGE;

public class RoomRunner implements Runnable {
    private Selector selector;
    private final List<ServerSideConnection> connections = new ArrayList<>();

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
                for (ServerSideConnection connection : toRead) {
                    BufferResults result = null;
                    try {
                        result = connection.bufferMessage();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    if (result == ONE_MESSAGE || result == MULTIPLE_MESSAGES) {
                        String msg = connection.pollMessage();
                        System.out.println("Received Message: " + msg);
                        if (result != MULTIPLE_MESSAGES) {
                            haveRead.add(connection);
                        }
                        synchronized (connections) {
                            for (ServerSideConnection otherConnection : connections) {
                                if (otherConnection != connection) {
                                    try {
                                        otherConnection.sendMessage(msg);
                                    } catch (IOException e) {
                                        System.out.println("A message failed to send");
                                        continue;
                                    }
                                }
                            }
                        }
                    } else { // We need to read in more data from the network buffer to get the rest.
                        haveRead.add(connection);
                    }
                }
                for (ServerSideConnection connection : haveRead) {
                    toRead.remove(connection);
                }
            }
        }
    }

    public void addConnection(ServerSideConnection connection) {
        synchronized (connections) {
            connections.add(connection);
        }
        try {
            connection.socketChannel.register(selector, SelectionKey.OP_READ, connection);
        } catch (ClosedChannelException e) {
            throw new RuntimeException(e);
        }
        // unblock
        selector.wakeup();
    }
}
