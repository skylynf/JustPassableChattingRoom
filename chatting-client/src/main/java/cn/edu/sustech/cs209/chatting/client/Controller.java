package cn.edu.sustech.cs209.chatting.client;

import cn.edu.sustech.cs209.chatting.common.Message;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.stage.Window;

import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;


public class Controller implements Initializable {

    @FXML
    ListView<Message> chatContentList;
    String nowShowing;

    @FXML
    ListView<String> chatList;
    Set<String> newMsgList = new HashSet<>();

    Map<String, List<Message>> chatContent = new ConcurrentHashMap<>();

    @FXML
    private Label currentUserName;
    @FXML
    private Label currentOnlineCnt;

    String username;
    private int loginState;

    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 8081;

    Socket socket;
    Set<String> clientNames = new HashSet<>();
    boolean clientNamesValid;
    BufferedReader input;
    PrintWriter output;

    private ScheduledExecutorService heartbeatExecutor;
    private long lastHeartbeatTime = 0;
    private final long HEARTBEAT_INTERVAL = 2000; // 2 seconds

    private void connectServer() {
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            output = new PrintWriter(socket.getOutputStream(), true);

            // start a new thread to read messages from the server

            new Thread(() -> {
                while (true) {
                    try {
                        String message = input.readLine();
                        if (message == null) {
                            // connection has been closed, break out of the loop
                            break;
                        }
                        System.out.println("Received message from server: " + message);
                        if (message.startsWith("[heartbeat]")) {
                            lastHeartbeatTime = System.currentTimeMillis();
                            continue;
                        }

                        // process the incoming message from the server
                        String[] names;
                        if (message.startsWith("[clients] ")) {
                            if (!message.contains(",")) {
                                names = null;
                            } else {
                                if (message.endsWith(",")) {
                                    message = message.substring(0, message.length() - 1);
                                }
                                names = message.substring(10).split(",");
                            }
                            if (names != null) {
                                clientNames.clear();
                                clientNames.addAll(Arrays.asList(names));
                            }
                            for (String clientName : clientNames) {
                                System.out.println(clientName);
                            }
                            clientNamesValid = true;
                            Platform.runLater(new Runnable() {
                                @Override
                                public void run() {
                                    currentOnlineCnt.setText(String.valueOf("Online: " + clientNames.size()));
                                }
                            });
                        }

                        if (message.startsWith("[message] ")) {


                            Message msg = Message.fromString(message.substring(10));
                            String sender = msg.getSentBy();
                            String target = msg.getSendTo();

                            System.out.println(msg.getData());

                            if (target.startsWith("GROUP[")) {
                                if (chatContent.containsKey(target)) {
                                    chatContent.get(target).add(msg);
                                    if (!Objects.equals(nowShowing, target)) {
                                        newMsgList.add(target);
                                    }
                                } else {
                                    List<Message> messages = new ArrayList<>();
                                    messages.add(msg);
                                    chatContent.put(target, messages);
                                    newMsgList.add(target);
                                }
                            } else if (sender.equals(username)) {
                                if (chatContent.containsKey(target)) {
                                    chatContent.get(target).add(msg);
                                } else {
                                    List<Message> messages = new ArrayList<>();
                                    messages.add(msg);
                                    chatContent.put(target, messages);
                                }
                            } else if (chatContent.containsKey(sender)) {
                                chatContent.get(sender).add(msg);
                                if (!Objects.equals(nowShowing, sender)) {
                                    newMsgList.add(sender);
                                }
                            } else {
                                List<Message> messages = new ArrayList<>();
                                messages.add(msg);
                                chatContent.put(sender, messages);
                                newMsgList.add(sender);
                            }

                            updateChatList();
                            // chatContentList.scrollTo(chatContentList.getItems().size() - 1);
                            Platform.runLater(this::updateChatWindow);
                        }

                        if (message.startsWith("[loginResult]")) {
                            if (message.endsWith("success")) {
                                loginState = 1;
                            } else if (message.endsWith("fail")) {
                                loginState = 2;
                            } else if (message.endsWith("new")) {
                                loginState = 3;
                            }
                        }

                    } catch (IOException e) {
                        System.err.println("Error reading message from server: " + e.getMessage());
                        break;
                    }
                }
            }).start();

        } catch (IOException e) {
            System.err.println("Error communicating with server: " + e.getMessage());
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {

        Dialog<String> dialog = new TextInputDialog();
        dialog.setTitle("Login");
        dialog.setHeaderText(null);
        dialog.setContentText("Username:");

        Optional<String> input = dialog.showAndWait();

        clientNamesValid = false;
        connectServer();

        if (input.isPresent() && !input.get().isEmpty()) {
            /*
               Check if there is a user with the same name among the currently logged-in users,
                     if so, ask the user to change the username
             */
            while (!clientNamesValid) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            while (clientNames.contains(input.get())) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText(null);
                alert.setContentText("Username already exists, please change your username");
                alert.showAndWait();
                input = dialog.showAndWait();
            }
            username = input.get();

            if (login(username)) {
                System.out.println("Login success");
            } else {
                System.out.println("Login failed");
                Platform.exit();
                System.exit(0);
            }
            currentUserName.setText(username);

        } else {
            System.out.println("Invalid username " + input + ", exiting");
            Platform.exit();
            System.exit(0);
        }

        chatContentList.setCellFactory(new MessageCellFactory());
        chatList.setCellFactory(new Callback<ListView<String>, ListCell<String>>() {
            @Override
            public ListCell<String> call(ListView<String> stringListView) {
                return new ListCell<String>() {
                    @Override
                    protected void updateItem(String s, boolean b) {
                        super.updateItem(s, b);
                        if (b) {
                            setText(null);
                            setGraphic(null);
                        } else {
                            if (s.startsWith("GROUP[")) {
                                setText(getGroupName(s));
                            } else {
                                setText(s);
                            }
                            assert newMsgList.contains(nowShowing);
                            if (newMsgList.contains(s)) {
                                setTextFill(Color.BLUE);
                            } else if (Objects.equals(s, nowShowing)) {
                                setTextFill(Color.RED);
                            } else {
                                setTextFill(Color.BLACK);
                            }
                        }
                    }
                };
            }
        });

        chatList.setOnMouseClicked(event -> {
                String selected = chatList.getSelectionModel().getSelectedItem();
                newMsgList.remove(selected);
                System.out.println("selected:" + selected);
                nowShowing = selected;
                Platform.runLater(() -> {
                    updateChatList();
                    updateChatWindow();
                });

        });

        lastHeartbeatTime = System.currentTimeMillis();
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        heartbeatExecutor.scheduleAtFixedRate(this::heartbeat, 0, 2, TimeUnit.SECONDS);

        //pop window for 1 second telling recovering history
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        if (loginState == 1) {
            alert.setTitle("Recovering history");
            alert.setHeaderText(null);
            alert.setContentText("Recovering history...");
            alert.show();
        } else if (loginState == 3) {
            alert.setTitle("New user");
            alert.setHeaderText(null);
            alert.setContentText("New user, welcome");
            alert.show();
        }
        CountDownLatch latch = new CountDownLatch(1);
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                latch.countDown();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        alert.close();
        updateChatList();

    }

    public String getGroupName(String name) {
        assert !name.startsWith("GROUP[");
        String[] names = name.split("\\[")[1].split("]")[0].split("/");
        String returnName;
        returnName = names[0] + ", " + names[1];
        if (names.length > 2) {
            returnName += ", " + names[2];
        }
        if (names.length > 3) {
            returnName += "...";
        }
        returnName += " (" + names.length + ")";
        return returnName;
    }

    @FXML
    public void showInformation() {
        //pop out a dialog show nowShowing information
        if (nowShowing.startsWith("GROUP[")) {
            String[] names = nowShowing.split("\\[")[1].split("]")[0].split("/");
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Group Information");
            alert.setHeaderText(null);
            alert.setContentText("Group Users: " + Arrays.toString(names));
            alert.show();
        } else {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("User Information");
            alert.setHeaderText(null);
            alert.setContentText("Username: " + nowShowing);
            alert.show();
        }

    }

    public boolean login(String username) {
        //login a user to the server
        //get password from user
        Dialog<String> dialog = new TextInputDialog();
        dialog.setTitle("Login");
        dialog.setHeaderText(null);
        dialog.setContentText("If registered, input password, otherwise press enter."+username+":");

        Optional<String> input = dialog.showAndWait();
        loginState=0;
        output.println("[login] "+username+ " " +input.get());
        //wait for server
        long loginStartTime = System.currentTimeMillis();
        while (loginState == 0 && loginStartTime + 5000 > System.currentTimeMillis()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println(loginState);
        if (loginState == 1 || loginState == 3) {
            return true;
        }else if (loginState == 2) {
            //tell wrong password
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText("Wrong password");
            alert.showAndWait();
            return false;
        } else {
            //tell server error
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText("Server error");
            alert.showAndWait();
            return false;
        }
    }

    @FXML
    public void changePwd() {
        //register a user to the server
        Dialog<String> dialog = new TextInputDialog();
        dialog.setTitle("ChangePwd");
        dialog.setHeaderText(null);
        dialog.setContentText("Password:");

        Optional<String> input = dialog.showAndWait();
        if (input.isPresent() && !input.get().isEmpty()) {
            output.println("[register] "+input.get());
        }
        //pop a window to tell success
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("ChangePwd");
        alert.setHeaderText(null);
        alert.setContentText("Changed successfully");
        alert.showAndWait();
    }

    @FXML
    public void register() {
        //register a user to the server
        Dialog<String> dialog = new TextInputDialog();
        dialog.setTitle("Register");
        dialog.setHeaderText(null);
        dialog.setContentText("Password:");

        Optional<String> input = dialog.showAndWait();
        if (input.isPresent() && !input.get().isEmpty()) {
            output.println("[register] "+input.get());
        }
        //pop a window to tell success
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Register");
        alert.setHeaderText(null);
        alert.setContentText("Register successfully");
        alert.showAndWait();
    }

    private boolean serverState=true;
    private void heartbeat() {
        output.println("[heartbeat]");
        // detect server state
        if (System.currentTimeMillis() - lastHeartbeatTime > HEARTBEAT_INTERVAL * 2) {
            System.out.println("Server is down");
            if (!serverState) {
                return;
            }
            serverState =false;
            Platform.runLater(() -> {
                currentOnlineCnt.setText("Server Down");
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText(null);
                alert.setContentText("Server is down");
                alert.showAndWait();
            });
        }
    }

    public void updateChatList() {
        //make chatlist sort by timestamp
        List<String> chatListItems = new ArrayList<>(chatContent.keySet());
        chatListItems.sort((o1, o2) -> {
            if (chatContent.get(o1).size() == 0) {
                return 1;
            }
            if (chatContent.get(o2).size() == 0) {
                return -1;
            }
            return chatContent.get(o2)
                    .get(chatContent.get(o2).size() - 1)
                    .getTimestamp()
                    .compareTo(chatContent.get(o1)
                            .get(chatContent.get(o1).size() - 1)
                            .getTimestamp());
        });
        ObservableList<String> newItems =
                FXCollections.observableArrayList(chatListItems);

        Platform.runLater(() -> {
            chatList.getItems().setAll(newItems);
        });
    }

    public void updateChatWindow() {
        chatContentList.getItems().clear();
        if (nowShowing != null)
            chatContentList.getItems().addAll(chatContent.get(nowShowing));
    }

    //send file to nowShowing user
    @FXML
    public void sendFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Resource File");
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            try {
                byte[] fileContent = Files.readAllBytes(file.toPath());
                String data = "/file " + file.getName() + " " + Arrays.toString(fileContent);
                Message msg = new Message(new Date().getTime(),
                        username,
                        nowShowing,
                        data);

                output.println("[send] " +msg.toString());
                chatContent.get(nowShowing).add(msg);
                updateChatWindow();
                updateChatList();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @FXML
    public void createPrivateChat() {
        AtomicReference<String> user = new AtomicReference<>();

        Stage stage = new Stage();
        ComboBox<String> userSel = new ComboBox<>();

        // FIXME: get the user list from server, the current user's name should be filtered out
        List<String> candidates = new ArrayList<>(clientNames);
        candidates.remove(username);
        userSel.getItems().addAll(candidates);

        Button okBtn = new Button("OK");
        okBtn.setOnAction(e -> {
            user.set(userSel.getSelectionModel().getSelectedItem());
            stage.close();
        });

        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(20, 20, 20, 20));
        box.getChildren().addAll(userSel, okBtn);
        stage.setScene(new Scene(box));
        stage.showAndWait();

        //create a new chat item in the left panel

        if (user.get() != null) {
            if (!chatContent.containsKey(user.get())) {
                chatContent.put(user.get(), new ArrayList<>());
                chatContent.get(user.get()).add(
                        new Message(
                                new Date().getTime(),
                                username,
                                user.get(),
                                "Start chatting with " + user.get())
                );
                nowShowing = user.get();
                chatList.getItems().add(user.get());

                updateChatList();
                updateChatWindow();
            } else {
                nowShowing = user.get();
                updateChatWindow();
            }

        }


        // TODO: if the current user already chatted with the selected user, just open the chat with that user
        // TODO: otherwise, create a new chat item in the left panel, the title should be the selected user's name
    }

    /**
     * A new dialog should contain a multi-select list, showing all user's name.
     * You can select several users that will be joined in the group chat, including yourself.
     * <p>
     * The naming rule for group chats is similar to WeChat:
     * If there are > 3 users: display the first three usernames, sorted in lexicographic order, then use ellipsis with the number of users, for example:
     * UserA, UserB, UserC... (10)
     * If there are <= 3 users: do not display the ellipsis, for example:
     * UserA, UserB (2)
     */
    @FXML
    public void createGroupChat() {
        // Retrieve list of user names from your data source
        List<String> userNames = new ArrayList<>();
        for (String name: clientNames) {
            if (!name.startsWith("GROUP[") && !name.equals(username))
                userNames.add(name);
        }

        // Create a new dialog for group chat creation
        Dialog<List<String>> dialog = new Dialog<>();
        dialog.setTitle("Create Group Chat");

        // Set the dialog content
        dialog.getDialogPane().setContent(createContent(userNames));

        // Add "Create" and "Cancel" buttons to the dialog
        ButtonType createButtonType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

        // Set result converter to handle user selections
        dialog.setResultConverter(buttonType -> {
            if (buttonType == createButtonType) {
                // Return selected user names
                ListView<String> listView = (ListView<String>) dialog.getDialogPane().lookup(".list-view");
                return new ArrayList<>(listView.getSelectionModel().getSelectedItems());
            }
            return null;
        });

        // Show the dialog and wait for user to close it
        Optional<List<String>> result = dialog.showAndWait();

        // If user clicked "Create", handle the selected user names
        result.ifPresent(users -> {
            // Handle creation of the group chat with the selected users
            handleGroupChatCreation(users);
        });
    }

    private VBox createContent(List<String> userNames) {
        // Create a multi-select list of user names
        ListView<String> listView = new ListView<>();
        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listView.getItems().addAll(userNames);

        // Add the list to a VBox container
        VBox vbox = new VBox();
        vbox.setSpacing(10);
        vbox.setPadding(new Insets(10));
        vbox.getChildren().addAll(new Label("Select users:"), listView);

        return vbox;
    }

    private void handleGroupChatCreation(List<String> users) {
        users.add(username);
        // Handle creation of the group chat with the selected users
        System.out.println("Creating group chat with users: " + users);

        //sort the users
        Collections.sort(users);
        //create a new chat item in the left panel
        StringBuilder chatName = new StringBuilder("GROUP[");
        for (String user: users) {
            chatName.append(user).append("/");
        }
        chatName = new StringBuilder(chatName.substring(0, chatName.length() - 1) + "]");
        if (!chatContent.containsKey(chatName.toString())) {
            chatContent.put(chatName.toString(), new ArrayList<>());
            chatContent.get(chatName.toString()).add(
                    new Message(new Date().getTime(), username, chatName.toString(), "Hello, " + chatName)
            );
            chatList.getItems().add(chatName.toString());
            nowShowing = chatName.toString();

            updateChatList();
            updateChatWindow();
        } else {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Group chat already exists");
            alert.setContentText("You have already created a group chat with the selected users");
            alert.showAndWait();
            nowShowing = chatName.toString();
            updateChatWindow();
        }

    }

    @FXML
    TextArea inputArea;

    /**
     * Sends the message to the <b>currently selected</b> chat.
     * <p>
     * Blank messages are not allowed.
     * After sending the message, you should clear the text input field.
     */
    @FXML
    public void doSendMessage() {
        String msg = inputArea.getText();
        if (msg.isEmpty()) {
            return;
        }
        if (nowShowing == null) {
            return;
        }
        Message message = new Message(new Date().getTime(), username, nowShowing, msg);
        output.println("[send] " + message.toString());
        chatContent.get(nowShowing).add(message);
        updateChatWindow();
        updateChatList();
        inputArea.clear();
    }

    private void saveToFile(byte[] content, File file) {


        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(content);
            fos.close();
            System.out.println("File saved successfully!");
        } catch (IOException ex) {
            System.err.println("Error: " + ex.getMessage());
        }
    }

    /**
     * You may change the cell factory if you changed the design of {@code Message} model.
     * Hint: you may also define a cell factory for the chats displayed in the left panel, or simply override the toString method.
     */
    private class MessageCellFactory implements Callback<ListView<Message>, ListCell<Message>> {
        @Override
        public ListCell<Message> call(ListView<Message> param) {
            return new ListCell<Message>() {

                @Override
                public void updateItem(Message msg, boolean empty) {
                    super.updateItem(msg, empty);
                    if (empty || Objects.isNull(msg)) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }

                    HBox wrapper = new HBox();
                    Label nameLabel = new Label(msg.getSentBy());

                    Label msgLabel;
                    //deal with file
                    if (msg.getData().startsWith("/file ")) {
                        msgLabel = new Label("File");
                        msgLabel.setTextFill(Color.BLUE);
                        msgLabel.setOnMouseClicked(new EventHandler<MouseEvent>() {
                            @Override
                            public void handle(MouseEvent event) {
                                System.out.println(msg.getData());
                                String fileName = msg.getData().substring(6).split(" ")[0];
                                String fileData = msg.getData().substring(6).split(" ",2)[1];
                                System.out.println(fileData);
                                String[] byteStrings = fileData
                                        .substring(1, fileData.length() - 1) // Remove '[' and ']'
                                        .split(", "); // Split by ", "

                                System.out.println(Arrays.toString(byteStrings));
                                byte[] restoredBytes = new byte[byteStrings.length];
                                for (int i = 0; i < byteStrings.length; i++) {
                                    restoredBytes[i] = Byte.parseByte(byteStrings[i]);
                                }
                                System.out.println(Arrays.toString(restoredBytes));
                                FileChooser fileChooser = new FileChooser();
                                fileChooser.setTitle("Save File");
                                fileChooser.setInitialFileName(fileName);

                                // Use the Window of the saveButton as the Owner of the Save Dialog
                                Window owner = msgLabel.getScene().getWindow();
                                File file = fileChooser.showSaveDialog(owner);

                                if (file != null) {
                                    saveToFile(restoredBytes, file);
                                }
                            }
                        });
                    } else {
                            msgLabel = new Label(msg.getData());
                        }

                    nameLabel.setPrefSize(50, 20);
                    nameLabel.setWrapText(true);
                    nameLabel.setStyle("-fx-border-color: black; -fx-border-width: 1px;");

                    if (username.equals(msg.getSentBy())) {
                        wrapper.setAlignment(Pos.TOP_RIGHT);
                        wrapper.getChildren().addAll(msgLabel, nameLabel);
                        msgLabel.setPadding(new Insets(0, 20, 0, 0));
                    } else {
                        wrapper.setAlignment(Pos.TOP_LEFT);
                        wrapper.getChildren().addAll(nameLabel, msgLabel);
                        msgLabel.setPadding(new Insets(0, 0, 0, 20));
                    }

                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    setGraphic(wrapper);
                }
            };
        }
    }
}
