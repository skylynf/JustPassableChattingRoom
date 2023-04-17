package cn.edu.sustech.cs209.chatting.server;

import cn.edu.sustech.cs209.chatting.common.Message;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class Server {

    private final int PORT = 8081;
    private ServerSocket serverSocket;
    private ConcurrentHashMap<String, ChatClientHandler> clients;
    private List<Message> historyMessage;

    private ConcurrentHashMap<String, String> accounts;

    public Server() {
        clients = new ConcurrentHashMap<>();
        accounts = new ConcurrentHashMap<>();
        historyMessage = new ArrayList<>();
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Server started on port " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress().getHostAddress());
                ChatClientHandler clientHandler = new ChatClientHandler(clientSocket, this);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setClientName(ChatClientHandler clientHandler) {
        clients.put(clientHandler.getClientName(), clientHandler);
    }

    public void removeClient(ChatClientHandler clientHandler) {
        clients.remove(clientHandler.getClientName());
    }

    public ConcurrentHashMap<String, ChatClientHandler> getClients() {
        return clients;
    }

    public void saveAccount(String name, String pwd){
        accounts.remove(name);
        accounts.put(name, pwd);
    }
    public void saveHistoryMessage(Message message){
        historyMessage.add(message);
    }
    public int checkAccount(String Name, String pwd){
        if (accounts.containsKey(Name)){
            if (accounts.get(Name).equals(pwd)){
                return 1;
            }
            else {
                return 0;
            }
        }
        else {
            return 2;
        }
    }
    public List<Message> getHistoryMessage(String username){
        List<Message> historyMessage = new ArrayList<>();
        for (Message message : this.historyMessage){
            if (message.getSendTo().equals(username) || message.getSentBy().equals(username)){
                historyMessage.add(message);
            }else if(message.getSendTo().startsWith("GROUP[")){
                String[] targets = message.getSendTo().split("\\[")[1].split("]")[0].split("/");
                for(String t : targets){
                    if(Objects.equals(t, username))
                        historyMessage.add(message);
                }
            }
        }
        return historyMessage;
    }
}
