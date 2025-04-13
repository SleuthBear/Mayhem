package com.Server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.*;
import com.Connections.BufferResults;
import com.Connections.ServerSideConnection;

import javax.net.ssl.SSLContext;

import static com.Connections.BufferResults.MULTIPLE_MESSAGES;
import static com.Connections.BufferResults.ONE_MESSAGE;

public class RoomAllocator implements Runnable {

    private final Selector selector = SelectorProvider.provider().openSelector();
    private final ServerSocketChannel serverSocketChannel;
    private final List<RoomRunner> rooms;
    private final SSLContext context;
    RoomAllocator(SSLContext context, List<RoomRunner> rooms, String host, int port) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.socket().bind(new InetSocketAddress(host, port));
        this.rooms = rooms;
        this.context = context;
    }

    @Override
    public void run() {
    // Register the channel with the selector to accept new connections.
   try {
      serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
    } catch(IOException e) {
      throw new RuntimeException(e.getMessage());
    }
    Set<ServerSideConnection> toRead = new HashSet<>();
    while(true) {
        try {
            selector.select();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
        while(keys.hasNext()) {
            SelectionKey key = keys.next();
            // We can accept once, and read once.
            keys.remove();
            if(!key.isValid()) continue;
            if(key.isAcceptable()) acceptKey(key);
            else if(key.isReadable()) {
                toRead.add((ServerSideConnection) key.attachment());
                // Since we only want to read one message, we will de-register this channel.
                key.cancel();
            }
        }
        while(!toRead.isEmpty()) {
            List<ServerSideConnection> haveRead = new ArrayList<>();
            for(ServerSideConnection connection : toRead) {
                BufferResults result  = null;
                try {
                    result = connection.bufferMessage();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                // todo validate if this can cause a message skipping bug if the user is very fast
                if(result == ONE_MESSAGE || result == MULTIPLE_MESSAGES ) {
                    String msg = connection.pollMessage();
                    int roomNumber = Integer.parseInt(msg);
                    rooms.get(roomNumber).addConnection(connection);
                    haveRead.add(connection);
                }
            }
            for(ServerSideConnection connection : haveRead) {
                toRead.remove(connection);
            }
        }
    }
  }

    public void acceptKey(SelectionKey key) {
        try {
            ServerSideConnection connection = new ServerSideConnection(context, key);
            connection.socketChannel.register(selector, SelectionKey.OP_READ, connection);
        } catch (IOException e) {
            return;
        }
    }
}
