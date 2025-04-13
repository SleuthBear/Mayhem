package com.Client;

import com.Client.UI.WindowManager;
import com.Connections.BufferResults;
import com.Connections.ClientSideConnection;
import com.Connections.ServerSideConnection;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Iterator;

import static com.Connections.BufferResults.*;

public class Client {
    public ClientSideConnection connection;
    private static final String TRUSTSTORE_PATH = "client.truststore";
    private static final String TRUSTSTORE_PASSWORD = "arCoaWQXNGEIQfZfKzQkCp8kxFkmekjdt7Wkg9TTqyG5w";
    public String username;
    private WindowManager windowManager;

    Client(String host, int port) throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException, IOException {
        KeyStore trustStore = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(TRUSTSTORE_PATH)) {
            trustStore.load(fis, TRUSTSTORE_PASSWORD.toCharArray());
        } catch (CertificateException | NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException(e);
        }
        // Create trust manager factory
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        // Create and initialize the SSL context
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, tmf.getTrustManagers(), new SecureRandom());

        windowManager = new WindowManager(this);
        username = windowManager.getUsername();
        connection = new ClientSideConnection(context, host, port);
        monitorMessages();
    }

    private void monitorMessages() {
        Thread messageThread = new Thread(() -> {
            Selector selector = null;
            try {
                selector = Selector.open();
                connection.socketChannel.register(selector, SelectionKey.OP_READ, connection);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            while (true) {
                BufferResults result = NO_MESSAGE;
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
                        try {
                            result = ((ClientSideConnection) key.attachment()).bufferMessage();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
                // empty out all messages in the buffer. If a partial message is caught, we ignore it until the selector
                // tells use we can read again.
                while (result == ONE_MESSAGE || result == MULTIPLE_MESSAGES) {
                    String fullMessage = connection.pollMessage();
                    String user = fullMessage.split("%:%")[0];
                    String message = fullMessage.split("%:%")[1];
                    windowManager.addOtherMessage(user, message);
                    try {
                        result = connection.bufferMessage();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });
        messageThread.setDaemon(true);
        messageThread.start();
    }

    public static void main(String args[]) throws KeyStoreException, NoSuchAlgorithmException, IOException, KeyManagementException {
        Client client = new Client("127.0.0.1", 3744);
        String roomNum = client.windowManager.getRoom();
        while(!"123".contains(roomNum)) {
            roomNum = client.windowManager.getRoom();
        }
        client.windowManager.window.setTitle("Room " + roomNum);
        client.connection.sendMessage(String.valueOf(Integer.parseInt(roomNum)-1));    }

}
