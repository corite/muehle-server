import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

public class DatabaseHandler {
    private final Connection connection;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public DatabaseHandler() {
        try {
            //connecting to the database
            connection = DriverManager.getConnection("jdbc:sqlite:src/main/resources/muehle.db");

            //establishing the users table
            createUsersTableIfNecessary();
            //setting all users initially to offline
            setAllUsersOffline();
        } catch (SQLException e) {
            logger.error("failed to initialize the database",e);
            throw new RuntimeException("failed to initialize the database");
        }

    }

    private Connection getConnection() {
        return connection;
    }

    private void createUsersTableIfNecessary() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS users ("
                + "name VARCHAR(100) PRIMARY KEY,"
                + "password VARCHAR(100) NOT NULL,"
                + "online BOOLEAN NOT NULL);";
        Statement statement= getConnection().createStatement();
        statement.execute(sql);
    }

    public void acquireUserLock(String name, String password) throws SQLException {
        if (name == null || password == null) {
            throw new IllegalArgumentException();
        }

        String sql = "SELECT name FROM users WHERE name = ? AND password = ? AND online = FALSE";
        PreparedStatement preparedStatement = prepareStatement(sql, name, password);

        ResultSet resultSet = preparedStatement.executeQuery();
        if (!resultSet.next()) {
            logger.debug("acquiring lock for user '{}' failed, likely because the credentials were wrong or because the user is already logged in",name);
            throw new SQLException();
        }
        updateOnlineStatus(name, true);
        logger.debug("lock was acquired for user '{}'",name);
    }

    public void releaseUserLock(String name) throws SQLException {
        if (name == null) {
            throw new IllegalArgumentException();
        }

        String sql = "SELECT name FROM users WHERE name = ? AND online = TRUE";
        PreparedStatement preparedStatement = prepareStatement(sql, name);

        ResultSet resultSet = preparedStatement.executeQuery();
        if (!resultSet.next()) {
            logger.warn("releasing lock for user '{}' failed, likely because the user wasn't locked in the first place",name);
            throw new SQLException();
        }
        updateOnlineStatus(name, false);
        logger.debug("lock was released for user '{}'",name);

    }

    private boolean hasUser(String name) throws SQLException{
        String sql = "SELECT name FROM users WHERE name = ?";
        PreparedStatement preparedStatement = prepareStatement(sql, name);

        ResultSet resultSet = preparedStatement.executeQuery();
        return resultSet.next();
    }

    public void createUser(String name, String password) throws SQLException {
        if (!hasUser(name)) {
            String sql = "INSERT INTO users(name,password,online) VALUES(?,?,FALSE)";
            PreparedStatement preparedStatement = prepareStatement(sql, name, password);

            preparedStatement.executeUpdate();
            logger.debug("successfully created new user with name '{}'",name);
        } else {
            logger.warn("failed to create user with name '{}' because this name is already taken",name);
            throw new SQLException();
        }
    }


    private PreparedStatement prepareStatement(String sql, String ...parameters) throws SQLException {
        PreparedStatement preparedStatement = getConnection().prepareStatement(sql);
        int i =1;
        for (String parameter : parameters) {
            preparedStatement.setString(i, parameter);
            i++;
        }
        return preparedStatement;
    }


    public void printAllUsers() throws SQLException {
        String sql = "SELECT name, password, online FROM users";
        Statement statement = getConnection().createStatement();

        ResultSet resultSet = statement.executeQuery(sql);

        while (resultSet.next()) {
            System.out.println("name="+resultSet.getString("name")+", password="+resultSet.getString("password")+", online="+resultSet.getBoolean("online"));
        }
    }

    public void dropUsersTable() throws SQLException{
        String sql = "DROP TABLE IF EXISTS users";
        Statement statement = getConnection().createStatement();
        statement.execute(sql);
    }

    private void updateOnlineStatus(String name, boolean online) throws SQLException {
        String sql = "UPDATE users SET online = ? WHERE name = ?";

        PreparedStatement preparedStatement = getConnection().prepareStatement(sql);
        preparedStatement.setBoolean(1, online);
        preparedStatement.setString(2, name);

        preparedStatement.executeUpdate();
        logger.debug("user '{}' has now online status={}",name,online);
    }
    private void setAllUsersOffline() throws SQLException {
        String sql = "SELECT name FROM users";
        Statement statement = getConnection().createStatement();
        ResultSet resultSet = statement.executeQuery(sql);

        while (resultSet.next()) {
            updateOnlineStatus(resultSet.getString("name"), false);
        }

    }



}
