/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Dao.jdbc.utilities;

import Dao.GetById;
import Dao.IdOwner;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Set;
import org.reflections.Reflections;
import system.Log;

/**
 *
 * @author Alvise
 */
public class JdbcUtilities {

    protected Connection connection = null;
    private ResultSet lastRs;

    protected String camelToSql(String s) {
        String res = new String();

        for (char c : s.toCharArray()) {
            if (c >= 'A' && c <= 'Z') {
                res += "_" + c;
            } else {
                res += c;
            }
        }

        return res.toLowerCase();
    }

    protected String sqlToCamel(String s) {
        String res = new String();
        boolean cond = false;
        for (char c : s.toCharArray()) {
            if (cond) {
                res += Character.toUpperCase(c);
                cond = false;
            } else if (c == '_') {
                cond = true;
            } else {
                res += c;
            }
        }
        return res;
    }

    /**
     * check if connection is operative and try to make a new one if it isn't
     *
     * @return true if connection is locked, false otherwise
     */
    protected boolean checkConnection() {
        if (connection == null) {
            try {
                connection = JdbcConnector.connect();
            } catch (SQLException ex) {
                Log.Write(ex.toString());
                return false;
            }
        }
        return connection != null;
    }

    /**
     * It return a list of object from db
     *
     * @param c is the class of the object that must be returned, for example
     * user
     * @param map it maps the name of db to class variables, the same used for
     * insert or update if is null it uses camelToSql function to find the db
     * column's name
     * @param tableName is the table where the data are located
     * @param param maps the object that are the values in "where" condition to
     * its name on the table
     * @return a linkedList of the object indicated in C class
     * @throws Exception
     */
    protected LinkedList<Object> getObject(Class<?> c, HashMap<String, String> map, String tableName, HashMap<Object, String> param) throws Exception {
        LinkedList<Object> result = new LinkedList<Object>();
        if (!checkConnection()) {
            result.add(null);
            return result;
        }
        String query = "select * from " + tableName;
        if (param != null) {
            query += " where ";
            for (Object par : param.keySet()) {
                query += param.get(par) + " = ? and ";
            }
            query = query.substring(0, query.length() - 5);
        }

        PreparedStatement stmt = connection.prepareStatement(query);
        if (param != null) {
            int point = 1;
            for (Object par : param.keySet()) {
                if (par instanceof String) {
                    stmt.setString(point++, (String) par);
                }
                if (par instanceof Double) {
                    stmt.setDouble(point++, (Double) par);
                }
                if (par instanceof Integer) {
                    stmt.setInt(point++, (int) par);
                }

            }
        }
        ResultSet rs = stmt.executeQuery();
        setLastRs(rs);
        if (!rs.first()) {
            result.add(null);
            return result;
        }
        if(map==null)
            map=new HashMap<String,String>();
        do {
            Object o = c.newInstance();
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().contains("set")) {
                    String name = m.getName().substring(3);
                    char[] ca = name.toCharArray();
                    name = String.valueOf(ca[0]).toLowerCase() + name.substring(1);
                    try {
                        if (m.getParameterTypes()[0].equals(String.class) || m.getParameterTypes()[0].equals(Time.class)) {
                            String s = map.get(name);
                            if (s == null) {
                                m.invoke(o, rs.getString(camelToSql(name)));
                            } else {
                                m.invoke(o, rs.getString(s));
                            }
                        }
                        if (m.getParameterTypes()[0].equals(Double.class)) {
                            String s = map.get(name);
                            if (s == null) {
                                m.invoke(o, rs.getDouble(camelToSql(name)));
                            } else {
                                m.invoke(o, rs.getDouble(s));
                            }

                        }
                        if (m.getParameterTypes()[0].equals(int.class)) {
                            String s = map.get(name);
                            if (s == null) {
                                m.invoke(o, rs.getInt(camelToSql(name)));
                            } else {
                                m.invoke(o, rs.getInt(s));
                            }
                        }
                        if (IdOwner.class.isAssignableFrom(m.getParameterTypes()[0])) {//m.getParameterTypes()[0].isAssignableFrom(IdOwner.class)) {
                            String s = map.get(name);
                            Class<?> clazz = Class.forName("Dao.jdbc.Jdbc" + m.getParameterTypes()[0].getSimpleName() + "Dao");
                            Constructor<?> ctor = clazz.getConstructor();
                            GetById jdbc = (GetById) ctor.newInstance(new Object[]{});
                            if (s == null) {
                                m.invoke(o, jdbc.getById(rs.getInt(camelToSql(name))));
                            } else {
                                m.invoke(o, jdbc.getById(rs.getInt(s)));
                            }
                        }
                    } catch (SQLException e) {
                    }
                }
            }
            result.add(o);
        } while (rs.next());

        return result;
    }

    /**
     *
     * @param o is the object which must be inserted into table
     * @param map map class variable name to db column name, if a determined
     * variable is not inserted into map its name will be used into db
     * @param tableName name of table where insert the new data
     * @return id value if it has id field
     * @throws SQLException
     */
    protected int insertDao(Object o, HashMap<String, String> map, String tableName) throws SQLException {
        if (!checkConnection()) {
            return 0;
        }
        if (map == null) {
            map = new <String, String>HashMap();
        }
        String query = new String("insert into " + tableName + " (");
        String values = "values(";
        Class<?> c = o.getClass();
        try {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().contains("get")) {
                    String name = m.getName().substring(3);
                    char[] ca = name.toCharArray();
                    name = String.valueOf(ca[0]).toLowerCase() + name.substring(1);
                    if (m.getReturnType().equals(Number.class)) {
                        Number value = (Number) m.invoke(o, null);
                        if (value.doubleValue() >= 0) {
                            values += value.doubleValue() + ",";
                            if (map.containsKey(name)) {
                                query += map.get(name) + ",";
                            } else {
                                query += name + ",";
                            }

                        }
                    } else if (m.getReturnType().equals(String.class) || m.getReturnType().equals(Date.class)) {
                        if ((Object) m.invoke(o, null) != null) {
                            values += "'" + m.invoke(o, null) + "',";
                            if (map.containsKey(name)) {
                                query += map.get(name) + ",";
                            } else {
                                query += name + ",";
                            }

                        }
                    } else {
                        Object obj = m.invoke(o, null);
                        if (obj != null) {

                            if (obj instanceof IdOwner) {
                                IdOwner id = (IdOwner) obj;
                                values += "'" + id.getId() + "',";
                                if (map.containsKey(name)) {
                                    query += map.get(name) + ",";
                                } else {
                                    query += name + ",";
                                }

                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            return 0;
        }
        query = query.substring(0, query.length() - 1);
        query += ") ";
        values = values.substring(0, values.length() - 1);
        values += ") ;";
        query += values;
        PreparedStatement stmt = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
        stmt.executeUpdate();
        ResultSet rs = stmt.getGeneratedKeys();
        rs.next();
        return rs.getInt(1);
    }

    /**
     *
     * @param o is the object which must be updated into table
     * @param map map class variable name to db column name, if a determined
     * variable is not inserted into map its name will be used into db
     * @param tableName name of table where update the new data
     * @return the number of changed rows
     * @throws SQLException
     */
    protected int updateDao(Object o, HashMap<String, String> map, String tableName) throws SQLException {
        if (!checkConnection()) {
            return 0;
        }
        if (map == null) {
            map = new <String, String>HashMap();
        }
        String query = new String("update " + tableName + " set ");
        Class<?> c = o.getClass();
        int id = 0;
        try {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().contains("get")) {
                    String name = m.getName().substring(3);
                    char[] ca = name.toCharArray();
                    name = String.valueOf(ca[0]).toLowerCase() + name.substring(1);
                    if (name.equals("id")) {
                        id = (int) m.invoke(o, null);
                    } else if (m.getReturnType().equals(Number.class)) {
                        Number value = (Number) m.invoke(o, null);
                        if (value.doubleValue() >= 0) {
                            if (map.containsKey(name)) {
                                query += map.get(name);
                            } else {
                                query += name;
                            }
                            query += " = " + value.doubleValue() + ",";
                        }
                    } else if (m.getReturnType().equals(String.class) || m.getReturnType().equals(Date.class)) {
                        if ((Object) m.invoke(o, null) != null) {
                            if (map.containsKey(name)) {
                                query += map.get(name);
                            } else {
                                query += name;
                            }
                            query += " = '" + m.invoke(o, null) + "',";
                        }
                    } else {
                        Object obj = m.invoke(o, null);
                        if (obj != null) {
                            if (obj instanceof IdOwner) {
                                IdOwner idOwner = (IdOwner) obj;
                                if (map.containsKey(name)) {
                                    query += map.get(name);
                                } else {
                                    query += name;
                                }
                                query += "=" + idOwner.getId() + ",";
                            }
                        }
                    }

                }
            }
        } catch (Exception ex) {
            return 0;
        }
        query = query.substring(0, query.length() - 1);

        if (query.length() < 20) {
            return 2;
        }
        if (id > 0) {
            query += " where id =" + id;
        }
        PreparedStatement stmt = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);

        return stmt.executeUpdate();
    }

    protected int deletDao(Object o, HashMap<String, String> map, String tableName) throws SQLException {
        if (!checkConnection()) {
            return 0;
        }
        if (map == null) {
            map = new <String, String>HashMap();
        }
        String query = new String("delete from " + tableName + " where ");
        Class<?> c = o.getClass();
        if (o instanceof IdOwner) {
            query += "id = " + ((IdOwner) o).getId() + " ";
        } else {
            try {
                for (Method m : c.getDeclaredMethods()) {
                    if (m.getName().contains("get")) {
                        String name = m.getName().substring(3);
                        char[] ca = name.toCharArray();
                        name = String.valueOf(ca[0]).toLowerCase() + name.substring(1);
                        if (m.getReturnType().equals(Number.class)) {
                            Number value = (Number) m.invoke(o, null);
                            if (value.doubleValue() >= 0) {
                                if (map.containsKey(name)) {
                                    query += map.get(name);
                                } else {
                                    query += name;
                                }
                                query += " = " + value.doubleValue() + " and ";
                            }
                        } else if (m.getReturnType().equals(String.class) || m.getReturnType().equals(Date.class)) {
                            if ((Object) m.invoke(o, null) != null) {
                                if (map.containsKey(name)) {
                                    query += map.get(name);
                                } else {
                                    query += name;
                                }
                                query += " = '" + m.invoke(o, null) + "' and ";
                            }
                        } else {
                            Object obj = m.invoke(o, null);
                            if (obj != null) {
                                if (obj instanceof IdOwner) {
                                    IdOwner idOwner = (IdOwner) obj;
                                    if (map.containsKey(name)) {
                                        query += map.get(name);
                                    } else {
                                        query += name;
                                    }

                                    query += "=" + idOwner.getId() + ",";
                                }
                            }
                        }

                    }
                }
            } catch (Exception ex) {
                return 0;
            }
            query = query.substring(0, query.length() - 1);

            if (query.length() < 20) {
                return 2;
            }
            query = query.substring(0, query.length() - 4);
        }
        PreparedStatement stmt = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);

        return stmt.executeUpdate();
    }

    public ResultSet getLastRs() {
        return lastRs;
    }

    public void setLastRs(ResultSet lastRs) {
        this.lastRs = lastRs;
    }
}
