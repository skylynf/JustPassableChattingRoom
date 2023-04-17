package cn.edu.sustech.cs209.chatting.server;

import cn.edu.sustech.cs209.chatting.common.Message;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ChatClientHandler implements Runnable {

    private Socket clientSocket;
    private Server chatServer;
    private String clientName;
    private BufferedReader in;
    private PrintWriter out;

    private ScheduledExecutorService heartbeatExecutor;
    private long lastHeartbeatTime;
    private final long HEARTBEAT_INTERVAL = 2000; // 2 seconds

    public ChatClientHandler(Socket clientSocket, Server chatServer) {
        this.clientSocket = clientSocket;
        this.chatServer = chatServer;
    }

    public String getClientName() {
        return clientName;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);

            // Send the list of connected clients to the new client
            StringBuilder clientsList = new StringBuilder("[clients] ");
            for (String name : chatServer.getClients().keySet()) {
                clientsList.append(name).append(",");
            }
            System.out.println(clientsList);
            out.println(clientsList);

            String loginMessage = in.readLine();
            if (loginMessage.startsWith("[login]")){
               String[] split = loginMessage.split(" ",3);
               String username = split[1];
               String pwd = split[2];
               //check pwd
               int checkResult = chatServer.checkAccount(username, pwd);
               if(checkResult==1){
                   out.println("[loginResult] success");
                   clientName = username;
                   //recovery history
                   List<Message> historyMessage = chatServer.getHistoryMessage(username);
                   for(Message msg : historyMessage){
                       out.println("[message] "+ msg.toString());
                   }
               }else if(checkResult==0){
                   out.println("[loginResult] fail");
                   chatServer.removeClient(this);
                   clientSocket.close();
               }else if(checkResult==2){
                   out.println("[loginResult] new");
                   clientName = username;
               }
            } else{
                chatServer.removeClient(this);
                clientSocket.close();
            }
            chatServer.setClientName(this);

            // Notify all other clients that this client has joined
            clientsList.append(clientName).append(",");
            chatServer.getClients().forEach((name, handler) -> {
                handler.sendClient(clientsList.toString());
            });

            lastHeartbeatTime = System.currentTimeMillis();
            heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
            heartbeatExecutor.scheduleAtFixedRate(this::heartbeat, 0, 2, TimeUnit.SECONDS);

            try {
                // Start handling incoming messages
                String message;
                while ((message = in.readLine()) != null) {
                    System.out.println("Received message: " + message);
                    if (message.equals("exit")) {
                        break;
                    }
                    handleMessage(message);
                }
            } catch (SocketException e){
                System.out.println("Possibly Client Disconnected");
            }

            // Notify all other clients that this client has left
            chatServer.removeClient(this);
            chatServer.getClients().forEach((name, handler) -> {
                //handler.sendMessage(clientName + " has left the chat");
            });

            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void heartbeat() {
        out.println("[heartbeat]");
        if(System.currentTimeMillis() - lastHeartbeatTime > HEARTBEAT_INTERVAL * 2) {
            System.out.println("Client is down");
            chatServer.removeClient(this);
            StringBuilder clientsList = new StringBuilder("[clients] ");
            for (String client : chatServer.getClients().keySet()) {
                clientsList.append(client).append(",");
            }
            System.out.println(clientsList);
            chatServer.getClients().forEach((name, handler) -> {
                handler.sendClient(clientsList.toString());
            });
            heartbeatExecutor.shutdown();
        }
    }

    public void sendClient(String message){
        out.println(message);
    }

    public void sendMessage(Message msg) {
        //send message with client name
        out.println("[message] "+ msg.toString());
    }

    private void handleMessage(String message) {
        // Handle incoming messages from the client
        if(Objects.equals(message, "getUsers")){
            StringBuilder clientsList = new StringBuilder("[clients] ");
            for (String name : chatServer.getClients().keySet()) {
                clientsList.append(name).append(",");
            }
            System.out.println(clientsList);
            out.println(clientsList);
        } else if(message.startsWith("[send]")){
            String[] split = message.split(" ",2);
            Message msg = Message.fromString(split[1]);

            chatServer.saveHistoryMessage(msg);

            String target = msg.getSendTo();
            String data = msg.getData();

            if(target.startsWith("GROUP[")){
                String[] targets = target.split("\\[")[1].split("]")[0].split("/");
                System.out.println(Arrays.toString(targets));
                for(String t : targets){
                    if(!Objects.equals(t, clientName) && chatServer.getClients().containsKey(t))
                        chatServer.getClients().get(t).sendMessage(msg);
                }
            }else {
                chatServer.getClients().get(target).sendMessage(msg);
            }
        } else if (message.startsWith("[heartbeat]")){
            lastHeartbeatTime = System.currentTimeMillis();
        } else if (message.startsWith("[register]")){
            String[] split = message.split(" ",2);
            String pwd = split[1];
            //save pwd to local file
            chatServer.saveAccount(clientName, pwd);
        }
    }


}
