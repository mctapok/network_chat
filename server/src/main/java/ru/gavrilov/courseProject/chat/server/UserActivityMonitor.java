package ru.gavrilov.courseProject.chat.server;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class UserActivityMonitor {
    private Timer timer;
    private Timer warnTimer;
    private Socket socket;
    private Map<Socket, Timer> clientTimers;
    private DataOutputStream out;
    private long delay = 2 * 60 * 1000;
    private long warningDelay = 1 * 60 * 1000;

    public UserActivityMonitor(Socket socket, Map<Socket, Timer> clientTimers) throws IOException {
        this.socket = socket;
        this.clientTimers = clientTimers;
        this.out = new DataOutputStream(socket.getOutputStream());

        new Thread(() -> {
            warning();
        }).start();
        startTimer();
    }

    public void resetTimer(Socket socket) {
        Timer resetTimer = clientTimers.get(socket);
        if (resetTimer != null) {
            resetTimer.cancel();
            warning();
            startTimer();
        }
    }
    public void startTimer(){
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
        }, delay);
        clientTimers.put(socket, timer);
    }


    public void warning() {
        warnTimer = new Timer();
        warnTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    out.writeUTF("WARNING: after 5 minutes you'll be disconnected");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, warningDelay);
    }

    public void stopTimer(){
        Timer stopedTimer = clientTimers.get(socket);
        if(stopedTimer != null){
            stopedTimer.cancel();
            warnTimer.cancel();
        }
    }
}
