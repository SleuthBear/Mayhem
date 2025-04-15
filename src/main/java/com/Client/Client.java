package com.Client;

import com.Client.UI.WindowManager;
import com.Connections.BufferResults;
import com.Connections.ClientSideConnection;
import com.Connections.ServerSideConnection;
import com.Message;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

import static com.Connections.BufferResults.*;
import static com.Message.PURPOSE.*;

public class Client {
    public ClientSideConnection connection;
    private static final String TRUSTSTORE_PATH = "client.truststore";
    private static final String TRUSTSTORE_PASSWORD = "arCoaWQXNGEIQfZfKzQkCp8kxFkmekjdt7Wkg9TTqyG5w";
    public int id;
    public String username;
    private WindowManager windowManager;
    PublicKey publicKey;
    PrivateKey privateKey;
    public SecretKey senderKey;
    Map<Integer, SecretKey> senderKeys = new HashMap<>();
    private final Map<Integer, PublicKey> publicKeys = new HashMap<>();
    Cipher aesCipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
    Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");

    Client(String host, int port) throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException, IOException, NoSuchPaddingException {
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

        // Set up the UI and the client username
        windowManager = new WindowManager(this);
        username = windowManager.getUsername();

        // Get the public and private key to use for sending the sender key.
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048, new SecureRandom());
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        publicKey = keyPair.getPublic();
        privateKey = keyPair.getPrivate();

        // Generate the symmetric sender key for messages.
        KeyGenerator aesKeyGenerator = KeyGenerator.getInstance("AES");
        aesKeyGenerator.init(256);
        senderKey = aesKeyGenerator.generateKey();


        connection = new ClientSideConnection(context, host, port);
    }

    public void monitorMessages() {
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
                    try {
                        processMessage(connection.pollMessage());
                    } catch (NoSuchAlgorithmException | InvalidKeySpecException | NoSuchPaddingException |
                             IllegalBlockSizeException | BadPaddingException | IOException | InvalidKeyException e) {
                        throw new RuntimeException(e);
                    }

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

    private void processMessage(Message msg) throws NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException, IOException {
        System.out.println("---------------------------\nMessage Received:\n" + msg.toString());
        if(msg.purpose == TEXT) {
            String content = decryptText(senderKeys.get(msg.sender), msg.messageString);
            String user = content.split("%:%")[0];
            String message = content.split("%:%")[1];
            windowManager.addOtherMessage(user, message);
        } else if(msg.purpose == JOIN_SENDER_KEY || msg.purpose == SENDER_KEY) {
            String senderKeyString = decryptText(privateKey, msg.messageString);
            byte[] keyBytes = Base64.getDecoder().decode(senderKeyString);
            senderKeys.put(msg.sender, new SecretKeySpec(keyBytes, "AES"));
            if (msg.purpose == JOIN_SENDER_KEY) { // Need to echo back our key to the new user
                String key = Base64.getEncoder().encodeToString(senderKey.getEncoded());
                String encryptedKey = encryptText(publicKeys.get(msg.sender), key);
                connection.sendMessage(new Message(encryptedKey, id, msg.sender, SENDER_KEY));
            }
        } else if(msg.purpose == PUBLIC_KEY_REGISTRATION) {
            byte[] keyBytes = Base64.getDecoder().decode(msg.messageString);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey pubKey = keyFactory.generatePublic(keySpec);
            publicKeys.put(msg.sender, pubKey);
        }
    }

    public void establishConnection() throws IOException, NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {

        // Send the room number to the roomAllocator
        String roomNum = windowManager.getRoom();
        while(!"123".contains(roomNum)) {
            roomNum = windowManager.getRoom();
        }
        windowManager.window.setTitle("Room " + roomNum);
        connection.sendMessage(new Message(String.valueOf(Integer.parseInt(roomNum)-1),
                0,
                0,
                Message.PURPOSE.ROOM_ASSIGNMENT));

        // This might cause the first message to get buffered and not read until the second is sent
        // Get the public keys of all other participants

        System.out.println("Getting ID");
        // Get the client ID
        // todo think if this should be handled server side or not.
        BufferResults result = NO_MESSAGE;
        while(result != ONE_MESSAGE && result != MULTIPLE_MESSAGES) {
            result = connection.bufferMessage();
        }
        Message idMessage = connection.pollMessage();
        assert idMessage.purpose == ID_ASSIGNMENT;
        id = idMessage.receiver;
        System.out.println("Sending public key");
        connection.sendMessage(new Message(Base64.getEncoder().encodeToString(publicKey.getEncoded()), id, 0, PUBLIC_KEY_REGISTRATION));

        System.out.println("Getting Other public keys");
        // Get all other public keys from the server.
        result = connection.bufferMessage();
        while(result != ONE_MESSAGE && result != MULTIPLE_MESSAGES) {
            result = connection.bufferMessage();
        }
        Message publicKeyMessage = connection.pollMessage();
        assert publicKeyMessage.purpose == Message.PURPOSE.PUBLIC_KEYS;
        getPublicKeys(publicKeyMessage);

        System.out.println("Sending Sender key");
        // Send Sender key to the other users.
        String key = Base64.getEncoder().encodeToString(senderKey.getEncoded());
        for(Map.Entry<Integer, PublicKey> entry : publicKeys.entrySet()) {
            String encodedKey = encryptText(entry.getValue(), key);
            connection.sendMessage(new Message(encodedKey, id, entry.getKey(), JOIN_SENDER_KEY));
        }

    }

    // RSA Encrypt
    public String encryptText(PublicKey key, String text) throws InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException {
        rsaCipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encryptedBytes = rsaCipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    // AES EncryptText
    public String encryptText(SecretKey key, String text) {
        try {
            aesCipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encryptedBytes = aesCipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // AES DecryptText 
    public String decryptText(SecretKey key, String text) throws InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        aesCipher.init(Cipher.DECRYPT_MODE, key);
        byte[] encryptedBytes = Base64.getDecoder().decode(text);
        byte[] decryptedBytes = aesCipher.doFinal(encryptedBytes);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    // RSA Decrypt
    public String decryptText(PrivateKey key, String text) throws InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        rsaCipher.init(Cipher.DECRYPT_MODE, key);
        byte[] encryptedBytes = Base64.getDecoder().decode(text);
        byte[] decryptedBytes = rsaCipher.doFinal(encryptedBytes);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }


    public void getPublicKeys(Message msg) {
        System.out.println("Public Keys: \n" + msg.messageString);
        for(String event : msg.messageString.split("\\|")) {
            try {
                if (event.isEmpty()) continue;
                String[] parts = event.split(":");
                byte[] keyBytes = Base64.getDecoder().decode(parts[1]);
                X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                PublicKey pubKey = keyFactory.generatePublic(keySpec);
                publicKeys.put(Integer.parseInt(parts[0]), pubKey);
            // todo real error handling
            } catch(Exception e) {
                throw new RuntimeException(e.getMessage());
            }
        }
    }

    public static void main(String args[]) throws KeyStoreException, NoSuchAlgorithmException, IOException, KeyManagementException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
        Client client = new Client("127.0.0.1", 3744);
        client.establishConnection();
        client.monitorMessages();

    }

}
