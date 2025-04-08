package com.Connections;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import static java.lang.Thread.sleep;

public class ClientSideConnection extends MayhemConnection {

    public ClientSideConnection(SSLContext context, String host, int port) throws IOException {
        this.context = context;
        this.engine = context.createSSLEngine();
        SSLSession session = engine.getSession();
        appIn = ByteBuffer.allocate(10240);
        appOut = ByteBuffer.allocate(10240);
        netIn = ByteBuffer.allocate(session.getPacketBufferSize());
        netOut = ByteBuffer.allocate(session.getPacketBufferSize());
        socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);
        socketChannel.connect(new InetSocketAddress(host, port));
        while(!socketChannel.finishConnect()) {
            try {
                sleep(10);
            } catch (InterruptedException e) {
                continue;
            }
        }
        engine.setUseClientMode(true);
        engine.beginHandshake();
        // todo error handling
        completeHandshake();
        netIn.clear();
    }



}
