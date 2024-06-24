package ru.gavrilov.courseProject.chat.server;

import java.sql.*;

public class DataBase {
    private final String DATABASE_URL = DbConfig.getDatabaseURL();
    private Connection connection;
    private Statement statement;


    public void closeDbConnection() {
        try {
            if (statement != null) {
                statement.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean dbConnection() {
        try {
            connection = DriverManager.getConnection(DATABASE_URL, DbConfig.getUsername(), DbConfig.getPassword());
            statement = connection.createStatement();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean dbRegistration(String login, String password, String registrationUsername) {
        String insertQuery = "insert into users (login, password, username, isAdmin) VALUES (?, ?, ?, ?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(insertQuery)) {
            preparedStatement.setString(1, login);
            preparedStatement.setString(2, password);
            preparedStatement.setString(3, registrationUsername);
            preparedStatement.setBoolean(4, false);
            preparedStatement.executeUpdate();
            closeDbConnection();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public User dbAuthorization(String login, String password) {
        String selectQuery = "select login, username, password, isAdmin from users where login=? and password=?";
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(selectQuery);
            preparedStatement.setString(1, login);
            preparedStatement.setString(2, password);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    String dbLogin = resultSet.getString("login");
                    String dbUsername = resultSet.getString("username");
                    String dbPassword = resultSet.getString("password");
                    boolean dbUserIsAdmin = resultSet.getBoolean("isAdmin");
                    closeDbConnection();
                    return new User(dbLogin, dbPassword, dbUsername, dbUserIsAdmin);
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
        return null;
    }

    public boolean isLoginAlreadyExists(String login) {
        String selectQuery = "select exists(select 1 from users where login=?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(selectQuery)) {
            preparedStatement.setString(1, login);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getBoolean("exists");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean isUsernameAlreadyExists(String username) {
        String selectQuery = "select exists(select 1 from users where username=?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(selectQuery)) {
            preparedStatement.setString(1, username);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getBoolean("exists");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean changeUsername(String login, String newUsername) {
        String updateQuery = "update users set username = ? where login = ? ";
        try (PreparedStatement preparedStatement = connection.prepareStatement(updateQuery)) {
            preparedStatement.setString(1, newUsername);
            preparedStatement.setString(2, login);
            preparedStatement.executeUpdate();
            closeDbConnection();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
