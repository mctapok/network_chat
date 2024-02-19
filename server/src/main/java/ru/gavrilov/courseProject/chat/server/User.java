package ru.gavrilov.courseProject.chat.server;

public class User {
    private String login;
    private String password;
    private String userName;
    private boolean isAdmin;

    public String getUserName() {
        return userName;
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }


    public String getLogin() {
        return login;
    }

    public User(String login, String password, String userName, boolean isAdmin) {
        this.login = login;
        this.password = password;
        this.userName = userName;
        this.isAdmin = isAdmin;
    }
}
