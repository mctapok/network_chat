package ru.gavrilov.course_project.chat.server;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class UserActivityMonitor {
    private Timer timer;
    private Socket socket;
    private Map<Socket, Timer> clientTimers;
    private DataOutputStream out;

    public UserActivityMonitor(Socket socket, Map<Socket, Timer> clientTimers) throws IOException {
        this.socket = socket;
        this.timer = new Timer();
        this.clientTimers = clientTimers;
        this.out = new DataOutputStream(socket.getOutputStream());

        new Thread(() -> {
            warning(out);
        }).start();

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    System.out.println(socket + " disconnected");
                    out.writeUTF("SERVER: disconnected from server");
                    socket.close();
                    clientTimers.remove(socket);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, 20 * 60 * 1000);
        clientTimers.put(socket, timer);
    }

    public void resetTimer(Socket socket) {
        Timer timer = clientTimers.get(socket);
        if (timer != null) {
            timer.cancel();
            clientTimers.remove(socket);
            timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        System.out.println(socket + " disconnected");
                        out.writeUTF("SERVER: disconnected from server");
                        socket.close();
                        clientTimers.remove(socket);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, 20 * 60 * 1000);
            clientTimers.put(socket, timer);
        }
    }

    public void warning(DataOutputStream out) {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    out.writeUTF("WARNING: after 5 minutes you'll be disconnected");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, 15 * 60 * 1000);
    }
}
