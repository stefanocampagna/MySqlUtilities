/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Dao.jdbc.utilities;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import system.Log;

/**
 *
 * @author Alvise
 */
public class JdbcConnector {
    static String user = "superroot";
    static String password = "root";
    static String ip="www.xoft.cloud";
    static String db="/web";
    static String url = "jdbc:mysql://"+ip+db;
    
    static Connection connection=null;

    JdbcConnector() {
    }
    
    public static Connection connect() throws SQLException{
        if(connection==null || connection.isClosed()){
            init();
            return connection;
        }
        return connection;   
    }
    
    private static boolean init() throws SQLException {
        try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException ex) {
            Log.Write("Error: unable to load driver class!");
            return false;
        }
        //Connector.url=String.format("jdbc:mysql://google/%s?cloudSqlInstance=%s&"+ "socketFactory=com.google.cloud.sql.mysql.SocketFactory",connector.db,connector.instanceConnectionName);
        connection = DriverManager.getConnection(JdbcConnector.url,JdbcConnector.user,JdbcConnector.password);
        return true;
    }
}
