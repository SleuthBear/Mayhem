package com.Client.UI;

import com.Client.Client;
import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

public class WindowManager {
    public JFrame window;
    JPanel messagePanel;
    JTextField newMessage = new JTextField("");
    JButton sendButton = new JButton("Send");
    Client client;
    Color pink = new Color(240, 100, 210);
    Color purple = new Color(150, 80, 220);

    public WindowManager(Client _client) throws IOException {
        System.setProperty( "apple.awt.application.appearance", "system" );
        client = _client;
        FlatDarkLaf.setup();
        JFrame.setDefaultLookAndFeelDecorated(true);
        createWindow();
    }

    public void createWindow() {
        window = new JFrame();
        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    client.connection.closeConnection();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
        window.setVisible(true);
        window.setTitle("Chat");
        window.setPreferredSize(new Dimension(800, 600));
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setLocationRelativeTo(null);
        Container content = window.getContentPane();
        content.setLayout(new BorderLayout());

        messagePanel = new JPanel();
        messagePanel.setLayout(new BoxLayout(messagePanel, BoxLayout.Y_AXIS));

        JScrollPane scroll = new JScrollPane(messagePanel);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        content.add(scroll, BorderLayout.CENTER);

        JPanel newMessagePanel = new JPanel();
        newMessagePanel.setLayout(new BorderLayout());
        newMessage.setPreferredSize(new Dimension(500, 30));
        newMessage.setBackground(new Color(50, 50, 50));
        newMessage.setForeground(Color.WHITE);
        newMessage.setCaretColor(Color.WHITE);
        newMessagePanel.add(newMessage, BorderLayout.CENTER);
        sendButton.setOpaque(true);
        sendButton.setBackground(pink);
        sendButton.setFont(new Font("Arial", Font.PLAIN, 15));
        sendButton.setForeground(Color.BLACK);
        sendButton.addActionListener(e -> {
            try {
                if(!newMessage.getText().isBlank()) {
                    client.connection.sendMessage(client.username + "%:%" + newMessage.getText());
                    addUserMessage(newMessage.getText());
                    newMessage.setText("");
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
        newMessagePanel.add(sendButton, BorderLayout.EAST);
        content.add(newMessagePanel, BorderLayout.SOUTH);
        window.pack();
        window.setLocationRelativeTo(null);

    }

    public String getUsername() throws IOException {
        String username = JOptionPane.showInputDialog(window,
                "What is your name?", null);
        return username;
    }

    public String getRoom() throws IOException {
        // Options are inverted for some reason...
        String[] rooms = {"3", "2", "1"};
        JComboBox<String> combo = new JComboBox<>(rooms);
        return String.valueOf(3 - JOptionPane.showOptionDialog(window, "Select a room", "", JOptionPane.DEFAULT_OPTION,
        JOptionPane.QUESTION_MESSAGE, null, rooms, 0));
    }

    public void addOtherMessage(String username, String message) {
        JPanel container = new JPanel();
        container.setLayout(new FlowLayout(FlowLayout.LEFT));
        JPanel panel = new RoundedPanel(15);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panel.setBackground(pink);
        panel.setForeground(Color.WHITE);

        JLabel usernameLabel = new JLabel(username);
        usernameLabel.setFont(new Font("Arial", Font.PLAIN, 15));
        usernameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(usernameLabel);

        panel.add(Box.createRigidArea(new Dimension(10, 10)));
        JLabel messageLabel = new JLabel(message);
        messageLabel.setFont(new Font("Arial", Font.PLAIN, 20));
        messageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(messageLabel);

        container.add(panel);
        messagePanel.add(container);
        messagePanel.revalidate();
        messagePanel.repaint();

        window.pack();

        // Auto-scroll to bottom
        SwingUtilities.invokeLater(() -> {
            JScrollPane scrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, messagePanel);
            if (scrollPane != null) {
                JScrollBar scrollBar = scrollPane.getVerticalScrollBar();
                scrollBar.setValue(scrollBar.getMaximum());
            }
        });
    }

    private void addUserMessage(String message) {
        JPanel container = new JPanel();
        container.setLayout(new FlowLayout(FlowLayout.RIGHT));
        JPanel panel = new RoundedPanel(15);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panel.setBackground(purple);

        panel.add(Box.createRigidArea(new Dimension(10, 10)));
        JLabel messageLabel = new JLabel(message);
        messageLabel.setFont(new Font("Arial", Font.PLAIN, 20));
        messageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(messageLabel);

        container.add(panel);
        messagePanel.add(container);
        messagePanel.revalidate();
        messagePanel.repaint();

        window.pack();

        // Auto-scroll to bottom
        SwingUtilities.invokeLater(() -> {
            JScrollPane scrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, messagePanel);
            if (scrollPane != null) {
                JScrollBar scrollBar = scrollPane.getVerticalScrollBar();
                scrollBar.setValue(scrollBar.getMaximum());
            }
        });
    }
}


