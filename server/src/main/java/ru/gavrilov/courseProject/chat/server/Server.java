package ru.gavrilov.courseProject.chat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class Server {
    DataBase dataBase;
    private int port;
    private List<ClientHandler> clients;
    private Map<Socket, Timer> clientTimers;
    private Map<ClientHandler, Timer> banClients;
    private ServerSocket serverSocket;
    private boolean isRunning;
    private List<ClientHandler> rawClients;
    private final String DENIED = "SERVER: admin command, permission denied";

    public Server(int port) {
        this.port = port;
        this.clients = new ArrayList<>();
        this.rawClients = new ArrayList<>();
        this.dataBase = new DataBase();
        this.clientTimers = new HashMap<>();
        this.banClients = new HashMap<>();
    }

    public void start() {
        isRunning = true;
        new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (isRunning) {
                String command = scanner.nextLine();
                if (command.equals("/shutdown")) {
                    shutdown();
                    break;
                }
            }
        }).start();

        try {
            serverSocket = new ServerSocket(port);
            System.out.printf("Сервер запущен на порту %d. Ожидание подключения клиентов\n", port);
            System.out.println("Запущен сервис для работы с пользователями");
            while (isRunning) {
                Socket socket = serverSocket.accept();
                try {
                    if (!isRunning) {
                        break;
                    }
                    rawClients.add(new ClientHandler(this, socket, new UserActivityMonitor(socket, clientTimers)));
                } catch (IOException e) {
                    System.out.println("не удалось подключить клиента");
                }
            }
        } catch (IOException e) {
            if (!isRunning) {
                System.out.println("server stop");
                System.exit(0);
            } else {
                e.printStackTrace();
            }
        }
    }

    public void broadcastMessage(String message) {
        for (ClientHandler clientHandler : clients) {
            clientHandler.sendMessage(message);
        }
    }

    public void subscribe(ClientHandler clientHandler) {
        broadcastMessage("Подключился новый клиент " + clientHandler.user.getUserName());
        clients.add(clientHandler);
    }

    public void unsubscribe(ClientHandler clientHandler) {
        for (ClientHandler c : clients) {
            if (c.user.getUserName().equals(clientHandler.user.getUserName())) {
                clients.remove(c);
                broadcastMessage("Отключился клиент " + c.user.getUserName());
                return;
            }
        }
    }

    public boolean isUserBusy(String username) {
        for (ClientHandler c : clients) {
            if (c.user.getUserName().equals(username)) {
                return true;
            }
        }
        return false;
    }

    public void sendPrivateMessage(ClientHandler sender, String receiverUsername, String message) {
        for (ClientHandler receiver : clients) {
            if (receiver.user.getUserName().equals(receiverUsername)) {
                sender.sendMessage("whisper to " + receiverUsername + ": " + message);
                receiver.sendMessage("whisper from " + sender.user.getUserName() + ": " + message);
                return;
            }
        }
        sender.sendMessage("SERVER: user not found");
    }

    public synchronized ClientHandler kickUser(ClientHandler client, boolean isAdmin, String username) {
        if (isAdmin) {
            for (ClientHandler c : clients) {
                if (c.user.getUserName().equals(username)) {
                    return c;
                }
            }
            client.sendMessage("SERVER: user not found");
            return null;
        }
        client.sendMessage(DENIED);
        return null;
    }

    public void activeClients(ClientHandler clientHandler) {
        if (clients.isEmpty()) {
            clientHandler.sendMessage("SERVER: no active users");
            return;
        }
        clientHandler.sendMessage("Active users");
        for (ClientHandler c : clients) {
            clientHandler.sendMessage(c.user.getUserName());
        }
    }

    public synchronized void banUser(ClientHandler clientHandler, boolean isAdmin, String username) {
        if (isAdmin) {
            for (ClientHandler c : clients) {
                if (c.user.getUserName().equals(username)) {
                    unsubscribe(c);
                    banClients.put(c, null);
                    broadcastMessage("ADMIN ban " + c.user.getUserName());
                    return;
                }
            }
            clientHandler.sendMessage("ERROR:user not found");
            return;
        }
        clientHandler.sendMessage(DENIED);
    }

    public synchronized void banUser(ClientHandler clientHandler, boolean isAdmin, String username, long time) {
        if (isAdmin) {
            for (ClientHandler c : clients) {
                if (c.user.getUserName().equals(username)) {
                    unsubscribe(c);
                    Timer timer = new Timer();
                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            subscribe(c);
                            banClients.remove(c);
                        }
                    }, time * 60 * 1000);
                    broadcastMessage("ADMIN ban " + c.user.getUserName() + " for " + time + " minutes");
                    banClients.put(c, timer);
                    return;
                }
            }
            clientHandler.sendMessage("ERROR:user not found");
            return;
        }
        clientHandler.sendMessage(DENIED);
    }

    public boolean checkBan(ClientHandler clientHandler) {
        return banClients.containsKey(clientHandler);
    }

    public synchronized void unBanUser(ClientHandler clientHandler, boolean isAdmin, String username) {
        if (isAdmin) {
            for (Map.Entry<ClientHandler, Timer> entry : banClients.entrySet()) {
                ClientHandler client = entry.getKey();
                String clientName = client.user.getUserName();
                if (clientName.equals(username)) {
                    banClients.remove(client);
                    subscribe(client);
                    return;
                }
            }
            clientHandler.sendMessage("ERROR: user not found in ban list");
            return;
        }
        clientHandler.sendMessage(DENIED);
    }


    public void shutdown() {
        isRunning = false;
        for (ClientHandler c : rawClients) {
            c.sendMessage("CLOSE_CONNECTION");
            c.disconnect();
        }
        if (!clients.isEmpty()) {
            for (ClientHandler c : clients) {
                c.sendMessage("CLOSE_CONNECTION");
                c.disconnect();
            }
        }
        try {
            serverSocket.close();
        } catch (IOException e) {
            //
        }
    }
}

