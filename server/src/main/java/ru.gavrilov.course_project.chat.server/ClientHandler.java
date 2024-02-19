package ru.gavrilov.course_project.chat.server;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class ClientHandler {
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
                sendMessage("Чтобы зарегистрироваться введите: /register login password username");
                authentication();
                listenUserChatMessages();
            } catch (IOException e) {
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
            userActivityMonitor.resetTimer(socket);
            if (message.contains("disconnected")) {
                System.out.println("client " + socket.getPort() + " was disconnected ");
            }
            if (message.startsWith("/")) {
                if (message.equals("/exit")) {
                    break;
                }
                if (message.startsWith("/w")) {
                    String[] commandParts = message.split(" ", 3);
                    server.sendPrivateMessage(this, commandParts[1], commandParts[2]);
                    continue;
                }
                if (message.startsWith("/kick")) {
                    String[] s = message.split(" ", 2);
                    ClientHandler c = server.kickUser(this, user.isAdmin(), s[1]);
                    if (c == null) {
                        continue;
                    }
                    c.sendMessage("-kicked by ADMIN");
                    server.broadcastMessage(c.user.getUserName() + " was kicked by ADMIN");
                    System.out.println(c.user.getUserName() + " was kicked by ADMIN");
                    continue;
                }
                if (message.startsWith("/changenick")) {
                    String[] commandParts = message.split(" ", 2);
                    server.dataBase.dbConnection();
                    if (server.dataBase.changeUsername(user.getLogin(), commandParts[1])) {
                        sendMessage("/changeok " + commandParts[1]);
                        server.broadcastMessage(user.getUserName() + " change nick to " + commandParts[1]);
                        user.setUserName(commandParts[1]);
                        continue;
                    }
                    this.sendMessage("ERROR: can't change nick");
                    continue;
                }
                if (message.startsWith("/activelist")) {
                    server.activeClients(this);
                    continue;
                }
                if (message.startsWith("/ban")) {
                    String[] commandParts = message.split(" ", 3);
                    if (commandParts.length < 2 || commandParts[1].isEmpty()) {
                        sendMessage("ERROR: wrong command");
                        continue;
                    }
                    if (commandParts.length < 3) {
                        server.banUser(this, user.isAdmin(), commandParts[1]);
                    } else {
                        if (Character.isDigit(commandParts[2].charAt(0))) {
                            long time = Long.parseLong(commandParts[2]);
                            server.banUser(this, user.isAdmin(), commandParts[1], time);
                            continue;
                        }
                        sendMessage("ERROR: time is not correct");
                        continue;
                    }
                    continue;
                }
                if (message.startsWith("/unban")) {
                    String[] commandParts = message.split(" ", 2);
                    if (commandParts.length < 2 || commandParts[1].isEmpty()) {
                        sendMessage("ERROR: wrong command");
                        continue;
                    }
                    server.unBanUser(this, user.isAdmin(), commandParts[1]);
                    continue;
                }
                if (message.startsWith("/commands")) {
                    sendMessage("commands list:\n/login {login} {password}\n/register {login} {password} {username}\n/activelist\n/w\n/exit\n/changenick {nick}");
                    if (user.isAdmin()) {
                        sendMessage("admin commands:\n/kick {username}\n/ban {username}(permanent ban)/ + {minutes}\n/unban {username}");
                    }
                    continue;
                }
            }
            server.broadcastMessage(user.getUserName() + ": " + message);
        }
    }

    public void sendMessage(String message) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        String currentTime = LocalDateTime.now().format(formatter);
        try {
            out.writeUTF(currentTime + ": " + message);
        } catch (IOException e) {
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
            e.printStackTrace();
        }
        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
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
            System.out.println("DB not answering");
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
        server.subscribe(this);
        sendMessage("/authok " + user.getUserName());
        sendMessage("СЕРВЕР: " + user.getUserName() + ", добро пожаловать в чат!");
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
            System.out.println("DB not answering");
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
        return true;
    }

    private void authentication() throws IOException {
        while (true) {
            String message = in.readUTF();
            userActivityMonitor.resetTimer(socket);
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

