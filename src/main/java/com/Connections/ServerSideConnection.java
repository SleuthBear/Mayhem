package com.Connections;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;


public class ServerSideConnection extends MayhemConnection {

    public ServerSideConnection(SSLContext context, SelectionKey key) throws IOException {
        this.context = context;
        engine = context.createSSLEngine();
        SSLSession session = engine.getSession();
        socketChannel = ((ServerSocketChannel) key.channel()).accept();
        socketChannel.configureBlocking(false);
        appIn = ByteBuffer.allocate(1024);
        appOut = ByteBuffer.allocate(1024);
        netIn = ByteBuffer.allocate(session.getPacketBufferSize());
        netOut = ByteBuffer.allocate(session.getPacketBufferSize());
        engine.setUseClientMode(false);
        engine.beginHandshake();
        // todo error handling
        completeHandshake();
    }
}
