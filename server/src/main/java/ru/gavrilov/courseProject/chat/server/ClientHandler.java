package ru.gavrilov.courseProject.chat.server;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class ClientHandler {
    private static final Logger logger = LogManager.getLogger(ClientHandler.class);
    private Server server;
    private Socket socket;
    User user;
    private DataOutputStream out;
    private DataInputStream in;
    private UserActivityMonitor userActivityMonitor;


    public ClientHandler(Server server, Socket socket, UserActivityMonitor userActivityMonitor) throws IOException {
        this.server = server;
        this.socket = socket;
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
        this.userActivityMonitor = userActivityMonitor;

        new Thread(() -> {
            try {
                sendMessage("Войдите или зарегистрируйтесь");
                sendMessage("Для входа используйте команду /login");
                sendMessage("Чтобы зарегистрироваться введите: /register login password username");
                authentication();
                listenUserChatMessages();
            } catch (IOException e) {
                logger.error(e);
                e.printStackTrace();
            } finally {
                disconnect();
            }
        }).start();
    }

    private void listenUserChatMessages() throws IOException {
        while (true) {
            String message = in.readUTF();
            if (server.checkBan(this)) {
                sendMessage("WARNING: you were banned by ADMIN");
                continue;
            }
            if(!user.isAdmin()){
                userActivityMonitor.resetTimer(socket);
            }
            if (message.contains("disconnected")) {
                logger.info("client " + socket.getPort() + " was disconnected ");
            }
            if (handleCommand(message)) {
                continue;
            }
            server.broadcastMessage(user.getUserName() + ": " + message);
        }
    }

    public boolean handleCommand(String message) throws IOException {
        if (message.startsWith("/")) {
            String[] commandParts = message.split(" ", 3);
            switch (commandParts[0]) {
                case "/exit":
                    break;
                case "/w":
                    if (commandParts.length >= 3) {
                        server.sendPrivateMessage(this, commandParts[1], commandParts[2]);
                    }
                    return true;
                case "/kick":
                    if (commandParts.length >= 2) {
                        handleKick(commandParts[1]);
                    }
                    return true;
                case "/changenick":
                    if (commandParts.length >= 2) {
                        handleChangeNick(commandParts[1]);
                    }
                    return true;
                case "/activelist":
                    server.activeClients(this);
                    return true;
                case "/ban":
                    if (commandParts.length >= 2) {
                        handleBan(commandParts);
                    }
                    return true;
                case "/unban":
                    if (commandParts.length >= 2) {
                        server.unBanUser(this, user.isAdmin(), commandParts[1]);
                    }
                    return true;
                case "/commands":
                    showCommands();
                default:
                    return false;
            }
        }
        return false;
    }

    public void handleKick(String username) throws IOException {
        ClientHandler c = server.kickUser(this, user.isAdmin(), username);
        if (c != null) {
            c.sendMessage("-kicked by ADMIN");
            server.broadcastMessage(c.user.getUserName() + " was kicked by ADMIN");
            System.out.println(c.user.getUserName() + " was kicked by ADMIN");
        }
    }

    public void handleChangeNick(String newUsername) throws IOException {
        server.dataBase.dbConnection();
        if (server.dataBase.changeUsername(user.getLogin(), newUsername)) {
            sendMessage("/changeok " + newUsername);
            server.broadcastMessage(user.getUserName() + " change nick to " + newUsername);
            user.setUserName(newUsername);
            return;
        }
        this.sendMessage("ERROR: can't change nick");
    }

    public void handleBan(String[] commandParts) throws IOException {
        if (commandParts.length < 2 || commandParts[1].isEmpty()) {
            sendMessage("ERROR: wrong command");
            return;
        }
        if (commandParts.length < 3) {
            server.banUser(this, user.isAdmin(), commandParts[1]);
        } else {
            if (Character.isDigit(commandParts[2].charAt(0))) {
                long time = Long.parseLong(commandParts[2]);
                server.banUser(this, user.isAdmin(), commandParts[1], time);
                return;
            }
            sendMessage("ERROR: time is not correct");
        }
    }
    public void showCommands(){
        sendMessage("СЕРВЕР: комманды в чате:\n/login {login} {password}\n/register {login} {password} {username}\n/activelist\n/w {username}\n/exit\n/changenick {nick}");
        if (user.isAdmin()) {
            sendMessage("admin commands:\n/kick {username}\n/ban {username}(permanent ban)/ + {minutes}\n/unban {username}");
        }
    }

    public void sendMessage(String message) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        String currentTime = LocalDateTime.now().format(formatter);
        try {
            out.writeUTF(currentTime + ": " + message);
        } catch (IOException e) {
            logger.error(e);
            e.printStackTrace();
        }
    }

    public void disconnect() {
        server.unsubscribe(this);
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException e) {
            logger.error(e);
            e.printStackTrace();
        }
        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException e) {
            logger.error(e);
            e.printStackTrace();
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            logger.error(e);
            e.printStackTrace();
        }
    }

    private boolean tryToAuthenticate(String message) {
        String[] elements = message.split(" ");
        if (elements.length != 3) {
            sendMessage("СЕРВЕР: некорректная команда аутентификации");
            return false;
        }
        String login = elements[1];
        String password = elements[2];
        if (!server.dataBase.dbConnection()) {
            logger.warn("DB not answering");
            return false;
        }
        user = server.dataBase.dbAuthorization(login, password);
        if (user == null) {
            sendMessage("СЕРВЕР: пользователя с указанным логин/паролем не существует");
            return false;
        }
        if (server.isUserBusy(user.getUserName())) {
            sendMessage("СЕРВЕР: учетная запись уже занята");
            return false;
        }
        if (user.isAdmin()) {
            userActivityMonitor.stopTimer();
        }
        server.subscribe(this);
        sendMessage("/authok " + user.getUserName());
        sendMessage("СЕРВЕР: " + user.getUserName() + ", добро пожаловать в чат!");
        showCommands();
        return true;
    }


    private boolean register(String message) {
        String[] elements = message.split(" ");
        if (elements.length != 4) {
            sendMessage("СЕРВЕР: некорректная команда аутентификации");
            return false;
        }
        String login = elements[1];
        String password = elements[2];
        String registrationUsername = elements[3];

        if (!server.dataBase.dbConnection()) {
            logger.warn("DB not answering");
            return false;
        }
        if (server.dataBase.isLoginAlreadyExists(login)) {
            sendMessage("СЕРВЕР: указанный login уже занят");
            return false;
        }
        if (server.dataBase.isUsernameAlreadyExists(registrationUsername)) {
            sendMessage("СЕРВЕР: указанное имя пользователя уже занято");
            return false;
        }
        server.dataBase.dbRegistration(login, password, registrationUsername);
        sendMessage("/authok " + user.getUserName());
        sendMessage("СЕРВЕР: " + user.getUserName() + ", вы успешно прошли регистрацию, добро пожаловать в чат!");
        server.subscribe(this);
        showCommands();
        return true;
    }

    private void authentication() throws IOException {
        while (true) {
            String message = in.readUTF();
            boolean isSucceed = false;
            if (message.startsWith("/login ")) {
                isSucceed = tryToAuthenticate(message);
            } else if (message.startsWith("/register ")) {
                isSucceed = register(message);
            } else {
                sendMessage("СЕРВЕР: требуется войти в учетную запись или зарегистрироваться");
            }
            if (isSucceed) {
                break;
            }
        }
    }
}

