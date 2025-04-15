package com;

public class Message {
    public enum PURPOSE {
        TEXT,
        PUBLIC_KEYS,
        PUBLIC_KEY_REGISTRATION,
        SENDER_KEY,
        JOIN_SENDER_KEY,
        ROOM_ASSIGNMENT,
        ID_ASSIGNMENT
    }
    public String messageString;
    public int sender;
    public int receiver;
    public PURPOSE purpose;

    public Message(String messageString, int sender, int receiver, PURPOSE purpose) {
        this.messageString = messageString;
        this.sender = sender;
        this.receiver = receiver;
        this.purpose = purpose;
    }

    public static PURPOSE purposeFromByte(byte b) {
        return PURPOSE.values()[b];
    }

    public String toString() {
        return "Purpose: " + purpose +
                "\nSender: " + String.valueOf(sender) +
                "\nReceiver: " + String.valueOf(receiver) +
                "\nText: " + String.valueOf(messageString);
    }
}
