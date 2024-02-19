package ru.gavrilov.courseProject.chat.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

public class ClientApp {
    static String username;
    private static final Logger logger = LogManager.getLogger(ClientApp.class);

    public static void main(String[] args) {
        try (
                Socket socket = new Socket("localhost", 8189);
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream())
        ) {
            logger.info("Подключились к серверу");
            Scanner scanner = new Scanner(System.in);
            new Thread(() -> {
                try {
                    while (true) {
                        String message = in.readUTF();
                        if (message.contains("CLOSE_CONNECTION")) {
                            try {
                                in.close();
                                out.close();
                                socket.close();
                                System.exit(0);
                            } catch (IOException e) {
                                logger.error(e);
                                e.printStackTrace();
                            }
                        }
                        if (message.contains("/authok ")) {
                            username = message.split(" ")[1];
                            break;
                        }
                        if (message.contains("disconnected")) {
                            if (message.startsWith("WARNING")) {
                                System.out.println(message);
                                continue;
                            }
                            System.out.println(message);
                            System.exit(0);
                        }
                        System.out.println(message);
                    }
                    while (true) {
                        String message = in.readUTF();
                        if (message.contains("CLOSE_CONNECTION")) {
                            try {
                                in.close();
                                out.close();
                                socket.close();
                                System.exit(0);
                            } catch (IOException e) {
                                logger.error(e);
                                e.printStackTrace();
                            }
                        }
                        if (message.startsWith("-kicked ")) {
                            System.out.println(message);
                            System.exit(0);
                        }
                        if (message.contains("disconnected")) {
                            if (message.startsWith("WARNING")) {
                                System.out.println(message);
                                continue;
                            }
                            System.exit(0);
                            continue;
                        }
                        if (message.contains("/changeok")) {
                            username = message.split(" ")[1];
                            continue;
                        }
                        System.out.println(message);
                    }
                } catch (IOException e) {
                    logger.error(e);
                    e.printStackTrace();
                }
            }).start();
            while (true) {
                String message = scanner.nextLine();
                out.writeUTF(message);
                if (message.equals("/exit")) {
                    System.exit(0);
                }
            }
        } catch (IOException e) {
            logger.error(e);
            e.printStackTrace();
        }
    }
}
