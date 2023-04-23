package cn.edu.sustech.cs209.chatting.common;

import java.net.URLDecoder;
import java.net.URLEncoder;

public class Message {

    private Long timestamp;

    private String sentBy;

    private String sendTo;

    private String data;

    public Message(Long timestamp, String sentBy, String sendTo, String data) {
        this.timestamp = timestamp;
        this.sentBy = sentBy;
        this.sendTo = sendTo;
        this.data = data;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public String getSentBy() {
        return sentBy;
    }

    public String getSendTo() {
        return sendTo;
    }

    public String getData() {
        return data;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(timestamp).append(",");
        sb.append(sentBy).append(",");
        sb.append(sendTo).append(",");
        sb.append(data.replaceAll(",", "\\,"));
        String original = sb.toString();
        String encoded = "";
        try {
            encoded = URLEncoder.encode(original, "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return encoded;
    }

    public static Message fromString(String str) {
        String decoded = "";
        try {
            decoded = java.net.URLDecoder.decode(str, "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
        }
        String[] parts = decoded.split(",", 4);
        Long timestamp = Long.parseLong(parts[0]);
        String sentBy = parts[1];
        String sendTo = parts[2];
        String data = parts[3].replaceAll("\\\\,", ",");
        return new Message(timestamp, sentBy, sendTo, data);
    }

}
