package com.Server;

import com.Connections.BufferResults;
import com.Connections.ServerSideConnection;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.*;

import static com.Connections.BufferResults.MULTIPLE_MESSAGES;
import static com.Connections.BufferResults.ONE_MESSAGE;

public class Server {
    public static final String[] protocols = new String[]{"TLSv1.3"};
    public static final String[] cipherSuites = new String[]{"TLS_AES_128_GCM_SHA256"};
    private static final String KEYSTORE_PATH = "server.keystore";
    private static final String KEYSTORE_PASSWORD = "xMiB3xmW0ShFdCovohQ6zNINGCpILo7tq1a1HSpNDu44F";//System.getenv("KEYSTORE_PASSWORD");
    private Selector selector = SelectorProvider.provider().openSelector();
    private SSLContext context;
    private List<ServerSideConnection> connections = new ArrayList<>();

    Server(String host, int port) throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyManagementException, IOException {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(KEYSTORE_PATH)) {
            keyStore.load(fis, KEYSTORE_PASSWORD.toCharArray());
        } catch (CertificateException | NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException(e);
        }

        // Create key manager factory
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, KEYSTORE_PASSWORD.toCharArray());

        // Create and initialize the SSL context
        context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), null, new SecureRandom());

        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.socket().bind(new InetSocketAddress(host, port));
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

    }

    public void start() throws IOException {
        Set<ServerSideConnection> toRead = new HashSet<>();
        while(true) {
            selector.select();
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
            while(keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();
                if(!key.isValid()) continue;
                if(key.isAcceptable()) acceptKey(key);
                else if(key.isReadable()) toRead.add((ServerSideConnection) key.attachment());
            }
            // Process all active reads before looking for new ones.
            // Todo: place accepting and read registration on a separate thread.
            // todo handle client disconnection
            while(!toRead.isEmpty()) {
                List<ServerSideConnection> haveRead = new ArrayList<>();
                for(ServerSideConnection connection : toRead) {
                    BufferResults result  = connection.bufferMessage();
                    // todo align this with the client loop.
                    if(result == ONE_MESSAGE || result == MULTIPLE_MESSAGES ) {
                        String msg = connection.pollMessage();
                        System.out.println("Recieved Message: " + msg);
                        if(result != MULTIPLE_MESSAGES) {
                            haveRead.add(connection);
                        }
                        for(ServerSideConnection otherConnection : connections) {
                            if(otherConnection != connection) {
                                otherConnection.sendMessage(msg);
                            }
                        }
                    } else { //We need to read in more data from the network buffer to get the rest.
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
            connections.add(connection);
        } catch (IOException e) {
            return;
        }
    }

    public static void main(String args[]) throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, IOException, KeyManagementException {
        Server server = new Server("127.0.0.1", 3744);
        server.start();
    }

}
