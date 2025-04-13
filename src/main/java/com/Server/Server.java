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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.Connections.BufferResults.MULTIPLE_MESSAGES;
import static com.Connections.BufferResults.ONE_MESSAGE;

public class Server {
  public static final String[] protocols = new String[] { "TLSv1.3" };
  public static final String[] cipherSuites = new String[] { "TLS_AES_128_GCM_SHA256" };
  private static final String KEYSTORE_PATH = "server.keystore";
  private static final String KEYSTORE_PASSWORD = "xMiB3xmW0ShFdCovohQ6zNINGCpILo7tq1a1HSpNDu44F";// System.getenv("KEYSTORE_PASSWORD");
  private final Selector selector = SelectorProvider.provider().openSelector();
  private final SSLContext context;
  private List<RoomRunner> rooms = new ArrayList<>();
  int nRooms = 3;
  private ExecutorService pool = Executors.newFixedThreadPool(nRooms+1);

  Server(String host, int port) throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException,
      KeyManagementException, IOException {
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

    RoomRunner room1 = new RoomRunner();
    pool.submit(room1);
    RoomRunner room2 = new RoomRunner();
    pool.submit(room2);
    RoomRunner room3 = new RoomRunner();
    pool.submit(room3);
    rooms.add(room1);
    rooms.add(room2);
    rooms.add(room3);

    RoomAllocator roomAllocator = new RoomAllocator(context, rooms, host, port);
    pool.submit(roomAllocator);
  }

  public static void main(String args[]) throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, IOException, KeyManagementException {
    Server server = new Server("127.0.0.1", 3744);
  }

}
