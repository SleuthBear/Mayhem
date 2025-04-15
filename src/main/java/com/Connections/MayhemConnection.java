package com.Connections;

import com.Message;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static javax.net.ssl.SSLEngineResult.Status.CLOSED;
import static javax.net.ssl.SSLEngineResult.Status.OK;

public abstract class MayhemConnection {
    SSLContext context;
    SSLEngine engine;
    public SocketChannel socketChannel;
    ByteBuffer appIn;
    ByteBuffer appOut;
    ByteBuffer netIn;
    ByteBuffer netOut;
    int metaBytesRead;
    int bytesToRead = -1;
    int bytesRead = 0;
    SSLEngineResult.HandshakeStatus handshakeStatus;
    ExecutorService executor = Executors.newSingleThreadExecutor();

    public SendMessageResult sendMessage(Message msg) throws IOException {
        int len = msg.messageString.length();
        byte[] metaBytes = new byte[9];
        metaBytes[0] = (byte) msg.purpose.ordinal();
        metaBytes[1] = (byte) ((msg.sender >> 8) & 0xFF);
        metaBytes[2] = (byte) ((msg.sender) & 0xFF);
        metaBytes[3] = (byte) ((msg.receiver >> 8) & 0xFF);
        metaBytes[4] = (byte) ((msg.receiver) & 0xFF);
        metaBytes[5] = (byte) ((len >> 24) & 0xFF);
        metaBytes[6] = (byte) ((len >> 16) & 0xFF);
        metaBytes[7] = (byte) ((len >> 8) & 0xFF);
        metaBytes[8] = (byte) (len & 0xFF);

        appOut.clear(); // clear the buffer of old data.
        appOut.put(metaBytes);
        appOut.put(msg.messageString.getBytes());
        appOut.flip(); // Flip to writing mode.

        while (appOut.hasRemaining()) {
            netOut.clear();
            SSLEngineResult result = engine.wrap(appOut, netOut);
            switch (result.getStatus()) {
                case OK:
                    netOut.flip();
                    while(netOut.hasRemaining()) {
                        socketChannel.write(netOut);
                    }
                    break;
                case BUFFER_OVERFLOW:
                    // todo enlarge buffer
                    throw new RuntimeException();
                case BUFFER_UNDERFLOW:
                    throw new RuntimeException();
                case CLOSED:
                    closeConnection();
                    return SendMessageResult.CLOSED;
                default:
                    throw new IllegalStateException("Invalid SSL state: " + result.getStatus());
            }
        }
        return SendMessageResult.SUCCESS;
    }

    // Because we are using NIO we are now somewhat ok with blocking reads. HOWEVER! I will not fall prey
    // to the tyranny of blocking reads.
    public BufferResults bufferMessage() throws IOException {
        int readIn = socketChannel.read(netIn);
        if (readIn == -1) return BufferResults.CONNECTION_CLOSED; // connection has closed or nothing being read
        else if (readIn > 0) {
            netIn.flip();
            // We need to read in the length bytes.
            SSLEngineResult result;
            if (metaBytesRead < 9) {
                result = engine.unwrap(netIn, appIn);
                if (result.getStatus() == OK) {
                    metaBytesRead += result.bytesProduced();
                } else if (result.getStatus() == CLOSED) {
                    return BufferResults.CONNECTION_CLOSED; // we must close the connection
                } else {
                    throw new RuntimeException();
                }
                // Ascertain how large the message is
                if (metaBytesRead >= 9) {
                    // Flip the buffer into read mode, to read the message length.
                    // todo make this less inefficient
                    ByteBuffer view = appIn.duplicate();
                    view.flip();
                    // todo figure out is this is actually important.
                    bytesToRead = ((view.get(5) & 0xFF) << 24) | ((view.get(6) & 0xFF) << 16) | ((view.get(7) & 0xFF) << 8) |
                            (view.get(8) & 0xFF);
                    bytesRead = metaBytesRead - 9; // Extra
                }
            } else if (bytesRead < bytesToRead) { // If this is a subsequent read for the same message
                result = engine.unwrap(netIn, appIn);
                if (result.getStatus() == OK) {
                    bytesRead += result.bytesProduced();
                } else if (result.getStatus() == CLOSED) {
                    return BufferResults.CONNECTION_CLOSED; // we must close the connection
                } else {
                    throw new RuntimeException();
                }
            }
            netIn.compact();
        }

        // If bytesRead == BytesToRead then the entire message is sitting in the appIn buffer,
        // store that in a message holder buffer, and then keep trying to parse messages.
        if (bytesRead == bytesToRead) {
            return BufferResults.ONE_MESSAGE;
        }
        // If bytesRead > BytesToRead then we have read in the start of the next message, and we need to pivot to handling this
        if (bytesRead > bytesToRead && bytesToRead != -1) {
            return BufferResults.MULTIPLE_MESSAGES;
        }
        return BufferResults.NO_MESSAGE;
    }

    public Message pollMessage() {
        // Flip the buffer to reading mode
        appIn.flip();
        // get the metadata from the message
        byte[] metaBytes = new byte[9];
        appIn.get(metaBytes, 0, 9);
        Message.PURPOSE purpose = Message.purposeFromByte(metaBytes[0]);
        int sender = ((metaBytes[1] & 0xFF) << 8) | (metaBytes[2] & 0xFF);
        int receiver = ((metaBytes[3] & 0xFF) << 8) | (metaBytes[4] & 0xFF);
        // Allocate a byte array to hold the message
        byte[] msgBytes = new byte[bytesRead];
        // copy the message into the byte array and shift the buffer position to the end of the current message
        appIn.get(msgBytes, 0, bytesRead);
        // Compact the buffer so only unread bytes are stored.
        appIn.compact();
        // Modify length bytes read. If this is greater than 4, it will be processed.
        metaBytesRead = bytesRead - bytesToRead;
        // Modify bytes read. If this is negative, then set it to 0 instead.
        bytesRead = Math.max(0, bytesRead - bytesToRead - 9);
        // We don't know how many bytes we need to read, so signal that.
        bytesToRead = -1;
        // Get the string from the byte buffer
        String messageString = new String(msgBytes, StandardCharsets.UTF_8);
        return new Message(messageString, sender, receiver, purpose);
    }

    boolean completeHandshake() throws IOException {
        SSLEngineResult result;
        handshakeStatus = engine.getHandshakeStatus();
        while(handshakeStatus != SSLEngineResult.HandshakeStatus.FINISHED && handshakeStatus != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            switch (handshakeStatus) {
                case NEED_UNWRAP:
                    // Validate that there is actually a handshake to read.
                    if (socketChannel.read(netIn) < 0) {
                        if (engine.isInboundDone() && engine.isOutboundDone()) {
                            throw new RuntimeException();
                        }
                        try {
                            engine.closeInbound();
                        } catch (SSLException e) {
                            throw new RuntimeException(e);
                        }
                        engine.closeOutbound();
                        handshakeStatus = engine.getHandshakeStatus();
                        break;
                    }
                    // Copy the contents of netIn into appIn (unencrypted)
                    netIn.flip(); // Flip the buffer from read to write
                    try {
                        result = engine.unwrap(netIn, appIn);
                        netIn.compact();
                        handshakeStatus = result.getHandshakeStatus();
                    } catch (SSLException e) {
                        engine.closeOutbound();
                        handshakeStatus = engine.getHandshakeStatus();
                        break;
                    }
                    // Check the status of the unwrap operation and handle errors
                    switch (result.getStatus()) {
                        case OK:
                            break;
                        case BUFFER_OVERFLOW:
                            // Too much data was copied to appIn
                            // todo update the buffer size instead
                            throw new RuntimeException();
                        case BUFFER_UNDERFLOW:
                            // not enough space in netIn, or no bytes read
                            continue;

                        case CLOSED:
                            if (engine.isOutboundDone()) {
                                // outbound closed prematurely
                                throw new RuntimeException();
                            } else {
                                engine.closeOutbound();
                                handshakeStatus = engine.getHandshakeStatus();
                                break;
                            }
                        default:
                            throw new IllegalStateException("Invalid SSL status");
                    }
                    break;
                case NEED_WRAP:
                    // Need to send data BACK to the client, so wrap it again.
                    netOut.clear();
                    try {
                        result = engine.wrap(appOut, netOut);
                        handshakeStatus = result.getHandshakeStatus();
                    } catch (SSLException e) {
                        engine.closeOutbound();
                        handshakeStatus = engine.getHandshakeStatus();
                        break;
                    }
                    switch (result.getStatus()) {
                        case OK:
                            // Switch to write mode and write out.
                            netOut.flip();
                            while (netOut.hasRemaining()) {
                                socketChannel.write(netOut);
                            }
                            break;
                        case BUFFER_OVERFLOW:
                            return false;
                        case BUFFER_UNDERFLOW:
                            throw new SSLException("Underflow after a wrap");
                        case CLOSED:
                            try {
                                netOut.flip();
                                while (netOut.hasRemaining()) {
                                    socketChannel.write(netOut);
                                }
                                // Assume we need to read in more data next, so clear netIn.
                                netIn.clear();
                            } catch (Exception e) {
                                handshakeStatus = engine.getHandshakeStatus();
                            }
                            break;
                        default:
                            throw new IllegalStateException("Invalid SSL status");
                    }
                    break;
                case NEED_TASK:
                    Runnable task;
                    while ((task = engine.getDelegatedTask()) != null) {
                        executor.execute(task);
                    }
                    handshakeStatus = engine.getHandshakeStatus();
                    break;
                case FINISHED:
                    break;
                case NOT_HANDSHAKING:
                    break;
                default:
                    throw new IllegalStateException("Invalid SSL status");
            }
        }
        return true;
    }

    public void closeConnection() throws IOException {
        engine.closeOutbound();
        completeHandshake();
        socketChannel.close();
    }
}
