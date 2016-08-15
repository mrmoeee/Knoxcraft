// Copyright (c) 2012 - 2015, CanaryMod Team
// Under the management of PlayBlack and Visual Illusions Entertainment
// All rights reserved.
// 
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//     * Redistributions of source code must retain the above copyright
//       notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above copyright
//       notice, this list of conditions and the following disclaimer in the
//       documentation and/or other materials provided with the distribution.
//     * Neither the name of the CanaryMod Team nor the
//       names of its contributors may be used to endorse or promote products
//       derived from this software without specific prior written permission.
// 
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
// ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
// WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL CANARYMOD TEAM OR ITS CONTRIBUTORS BE LIABLE FOR ANY
// DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
// ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
// 
// Any source code from the Minecraft Server is not owned by CanaryMod Team, PlayBlack,
// Visual Illusions Entertainment, or its contributors and is not covered by above license.
// Usage of source code from the Minecraft Server is subject to the Minecraft End User License Agreement as set forth by Mojang AB.
// The Minecraft EULA can be viewed at https://account.mojang.com/documents/minecraft_eula
// CanaryMod Team, PlayBlack, Visual Illusions Entertainment, CanaryLib, CanaryMod, and its contributors
// are NOT affiliated with, endorsed, or sponsored by Mojang AB, makers of Minecraft.
// "Minecraft" is a trademark of Notch Development AB
// "CanaryMod" name is used with permission from FallenMoonNetwork.

package org.knoxcraft.database.mysql;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.knoxcraft.database.Column;
import org.knoxcraft.database.DataAccess;
import org.knoxcraft.database.Database;
import org.knoxcraft.database.JdbcConnectionManager;
import org.knoxcraft.database.SQLType;
import org.knoxcraft.database.StringUtil;
import org.knoxcraft.database.exceptions.DatabaseAccessException;
import org.knoxcraft.database.exceptions.DatabaseReadException;
import org.knoxcraft.database.exceptions.DatabaseTableInconsistencyException;
import org.knoxcraft.database.exceptions.DatabaseWriteException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents access to a MySQL database
 *
 * @author Aaron (somners)
 * @author Chris Ksoll (damagefilter)
 * @author Jason Jones (darkdiplomat)
 */
public class MySQLDatabase extends Database {

    private Logger log=LoggerFactory.getLogger(MySQLDatabase.class);

    private static MySQLDatabase instance;
    private final String LIST_REGEX = "\u00B6";
    private final String NULL_STRING = "NULL";

    private MySQLDatabase() {
        // one does not simply instantiate MySQLDatabase!
    }

    public static MySQLDatabase getInstance() {
        if (instance == null) {
            SQLType.registerSQLDriver("mysql", "com.mysql.cj.jdbc.Driver");
            instance = new MySQLDatabase();
        }
        return instance;
    }

    @Override
    public void insert(DataAccess data) throws DatabaseWriteException {
        if (this.doesEntryExist(data)) {
            return;
        }
        Connection conn = JdbcConnectionManager.getConnection();
        PreparedStatement ps = null;

        try {
            StringBuilder fields = new StringBuilder();
            StringBuilder values = new StringBuilder();
            HashMap<Column, Object> columns = data.toDatabaseEntryList();
            Iterator<Column> it = columns.keySet().iterator();

            Column column;
            while (it.hasNext()) {
                column = it.next();
                if (!column.autoIncrement()) {
                    fields.append("`").append(column.columnName()).append("`").append(",");
                    values.append("?").append(",");
                }
            }
            if (fields.length() > 0) {
                fields.deleteCharAt(fields.length() - 1);
            }
            if (values.length() > 0) {
                values.deleteCharAt(values.length() - 1);
            }
            ps = conn.prepareStatement("INSERT INTO `" + data.getName() + "` (" + fields.toString() + ") VALUES(" + values.toString() + ")");

            int i = 1;
            for (Column c : columns.keySet()) {
                if (!c.autoIncrement()) {
                    setToStatement(i, columns.get(c), ps, c);
                    i++;
                }
            }

            if (ps.executeUpdate() == 0) {
                throw new DatabaseWriteException("Error inserting MySQL: no rows updated!");
            }
        }
        catch (SQLException ex) {
            log.error(ex.getMessage(), ex);
        }
        catch (DatabaseTableInconsistencyException dtie) {
            log.error(dtie.getMessage(), dtie);
        }
        finally {
            close(conn, ps, null);
        }
    }

    @Override
    public void insertAll(List<DataAccess> data) throws DatabaseWriteException {
        for (DataAccess da : data) {
            insert(da);
        }
    }

    @Override
    public void update(DataAccess data, Map<String, Object> filters) throws DatabaseWriteException {
        if (!this.doesEntryExist(data)) {
            return;
        }
        Connection conn = JdbcConnectionManager.getConnection();
        ResultSet rs = null;

        try {
            rs = this.getResultSet(conn, data, filters, true);
            if (rs != null) {
                if (rs.next()) {
                    HashMap<Column, Object> columns = data.toDatabaseEntryList();
                    Iterator<Column> it = columns.keySet().iterator();
                    Column column;
                    while (it.hasNext()) {
                        column = it.next();
                        if (column.columnName().equals("id")) {
                            // Don't update it
                            continue;
                        }
                        if (column.isList()) {
                            rs.updateObject(column.columnName(), this.getString((List<?>)columns.get(column)));
                        }
                        else {
                            rs.updateObject(column.columnName(), columns.get(column));
                        }
                    }
                    rs.updateRow();
                }
                else {
                    throw new DatabaseWriteException("Error updating DataAccess to MySQL, no such entry: " + data.toString());
                }
            }
        }
        catch (SQLException ex) {
            log.error(ex.getMessage(), ex);
        }
        catch (DatabaseTableInconsistencyException dtie) {
            log.error(dtie.getMessage(), dtie);
        }
        catch (DatabaseReadException e) {
            log.error(e.getMessage(), e);
        }
        finally {
            PreparedStatement st = null;
            try {
                st = rs != null && rs.getStatement() instanceof PreparedStatement ? (PreparedStatement)rs.getStatement() : null;
            }
            catch (SQLException e) {
                log.error(e.getMessage(), e);
            }
            close(conn, st, rs);
        }
    }

    @Override
    public void updateAll(DataAccess template, Map<DataAccess, Map<String, Object>> list) throws DatabaseWriteException {
        // FIXME: Might be worthwhile collecting all queries into one statement?
        // But then if something errors out it's hard to find what it was
        for (DataAccess da : list.keySet()) {
            update(da, list.get(da));
        }
    }

    @Override
    public void remove(DataAccess dataAccess, Map<String, Object> filters) throws DatabaseWriteException {
        Connection conn = JdbcConnectionManager.getConnection();
        PreparedStatement ps = null;

        try {
            if (filters.size() > 0) {
                StringBuilder sb = new StringBuilder();
                Object[] fieldNames = filters.keySet().toArray();
                for (int i = 0; i < fieldNames.length && i < fieldNames.length; i++) {
                    sb.append("`").append(fieldNames[i]);
                    if (i + 1 < fieldNames.length) {
                        sb.append("`=? AND ");
                    }
                    else {
                        sb.append("`=?");
                    }
                }

                ps = conn.prepareStatement("DELETE FROM `" + dataAccess.getName() + "` WHERE " + sb.toString() + "LIMIT 1");
                for (int i = 0; i < fieldNames.length && i < fieldNames.length; i++) {
                    String fieldName = String.valueOf(fieldNames[i]);
                    Column col = dataAccess.getColumnForName(fieldName);
                    if (col == null) {
                        throw new DatabaseReadException("Error deleting MySQL row in " + dataAccess.getName() + ". Column " + fieldNames[i] + " does not exist!");
                    }
                    setToStatement(i + 1, filters.get(fieldName), ps, col);
                }

                if (ps.executeUpdate() == 0) {
                    throw new DatabaseWriteException("Error removing from MySQL: no rows updated!");
                }
            }
        }
        catch (DatabaseReadException dre) {
            log.error(dre.getMessage(), dre);
        }
        catch (SQLException ex) {
            log.error(ex.getMessage(), ex);
        }
        finally {
            close(conn, ps, null);
        }
    }

    @Override
    public void removeAll(DataAccess dataAccess, Map<String, Object> filters) throws DatabaseWriteException {
        Connection conn = JdbcConnectionManager.getConnection();
        PreparedStatement ps = null;

        try {
            if (filters.size() > 0) {
                StringBuilder sb = new StringBuilder();
                Object[] fieldNames = filters.keySet().toArray();
                for (int i = 0; i < fieldNames.length && i < fieldNames.length; i++) {
                    sb.append("`").append(fieldNames[i]);
                    if (i + 1 < fieldNames.length) {
                        sb.append("`=? AND ");
                    }
                    else {
                        sb.append("`=?");
                    }
                }

                ps = conn.prepareStatement("DELETE FROM `" + dataAccess.getName() + "` WHERE " + sb.toString());
                for (int i = 0; i < fieldNames.length && i < fieldNames.length; i++) {
                    String fieldName = String.valueOf(fieldNames[i]);
                    Column col = dataAccess.getColumnForName(fieldName);
                    if (col == null) {
                        throw new DatabaseReadException("Error deleting MySQL row in " + dataAccess.getName() + ". Column " + fieldNames[i] + " does not exist!");
                    }
                    setToStatement(i + 1, filters.get(fieldName), ps, col);
                }

                if (ps.executeUpdate() == 0) {
                    throw new DatabaseWriteException("Error removing from MySQL: no rows updated!");
                }
            }
        }
        catch (DatabaseReadException dre) {
            log.error(dre.getMessage(), dre);
        }
        catch (SQLException ex) {
            log.error(ex.getMessage(), ex);
        }
        finally {
            close(conn, ps, null);
        }
    }

    @Override
    public void load(DataAccess da, Map<String, Object> filters) throws DatabaseReadException {
        ResultSet rs = null;
        Connection conn = JdbcConnectionManager.getConnection();
        HashMap<String, Object> dataSet = new HashMap<String, Object>();
        try {
            rs = this.getResultSet(conn, da, filters, true);
            if (rs != null) {
                if (rs.next()) {
                    for (Column column : da.getTableLayout()) {
                        if (column.isList()) {
                            dataSet.put(column.columnName(), this.getList(column.dataType(), rs.getString(column.columnName())));
                        }
                        else if (rs.getObject(column.columnName()) instanceof Boolean) {
                            dataSet.put(column.columnName(), rs.getBoolean(column.columnName()));
                        }
                        else {
                            dataSet.put(column.columnName(), rs.getObject(column.columnName()));
                        }
                    }
                    da.load(dataSet);
                }
            }
        }
        catch (DatabaseReadException dre) {
            log.error(dre.getMessage(), dre);
        }
        catch (SQLException ex) {
            log.error(ex.getMessage(), ex);
        }
        catch (DatabaseTableInconsistencyException dtie) {
            log.error(dtie.getMessage(), dtie);
        }
        catch (DatabaseAccessException e) {
            log.error(e.getMessage(), e);
        }
        finally {
            try {
                PreparedStatement st = rs != null && rs.getStatement() instanceof PreparedStatement ? (PreparedStatement)rs.getStatement() : null;
                close(conn, st, rs);
            }
            catch (SQLException ex) {
                log.error(ex.getMessage(), ex);
            }
        }
    }

    @Override
    public void loadAll(DataAccess typeTemplate, List<DataAccess> datasets, Map<String, Object> filters) throws DatabaseReadException {
        ResultSet rs = null;
        Connection conn = JdbcConnectionManager.getConnection();
        List<HashMap<String, Object>> stuff = new ArrayList<HashMap<String, Object>>();
        try {
            rs = this.getResultSet(conn, typeTemplate, filters, false);
            if (rs != null) {
                while (rs.next()) {
                    HashMap<String, Object> dataSet = new HashMap<String, Object>();
                    for (Column column : typeTemplate.getTableLayout()) {
                        if (column.isList()) {
                            dataSet.put(column.columnName(), this.getList(column.dataType(), rs.getString(column.columnName())));
                        }
                        else if (rs.getObject(column.columnName()) instanceof Boolean) {
                            dataSet.put(column.columnName(), rs.getBoolean(column.columnName()));
                        }
                        else {
                            dataSet.put(column.columnName(), rs.getObject(column.columnName()));
                        }
                    }
                    stuff.add(dataSet);
                }
            }
        }
        catch (DatabaseReadException dre) {
            log.error(dre.getMessage(), dre);
        }
        catch (SQLException ex) {
            log.error(ex.getMessage(), ex);
        }
        catch (DatabaseTableInconsistencyException dtie) {
            log.error(dtie.getMessage(), dtie);
        }
        finally {
            try {
                PreparedStatement st = rs != null && rs.getStatement() instanceof PreparedStatement ? (PreparedStatement)rs.getStatement() : null;
                close(conn, st, rs);
            }
            catch (SQLException ex) {
                log.error(ex.getMessage(), ex);
            }
        }
        try {
            for (HashMap<String, Object> temp : stuff) {
                DataAccess newData = typeTemplate.getInstance();
                newData.load(temp);
                datasets.add(newData);
            }
        }
        catch (DatabaseAccessException dae) {
            log.error(dae.getMessage(), dae);
        }
    }

    @Override
    public void updateSchema(DataAccess schemaTemplate) throws DatabaseWriteException {
        Connection conn = JdbcConnectionManager.getConnection();
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            // First check if the table exists, if it doesn't we'll skip the rest
            // of this method since we're creating it fresh.
            DatabaseMetaData metadata = conn.getMetaData();
            rs = metadata.getTables(null, null, schemaTemplate.getName(), null);
            if (!rs.first()) {
                this.createTable(schemaTemplate);
            }
            else {

                LinkedList<String> toRemove = new LinkedList<String>();
                HashMap<String, Column> toAdd = new HashMap<String, Column>();
                Iterator<Column> it = schemaTemplate.getTableLayout().iterator();

                // TODO: Should update primary keys ...
                Column column;
                while (it.hasNext()) {
                    column = it.next();
                    toAdd.put(column.columnName(), column);
                }

                for (String col : this.getColumnNames(schemaTemplate)) {
                    if (!toAdd.containsKey(col)) {
                        toRemove.add(col);
                    }
                    else {
                        toAdd.remove(col);
                    }
                }

                for (String name : toRemove) {
                    this.deleteColumn(schemaTemplate.getName(), name);
                }
                for (Map.Entry<String, Column> entry : toAdd.entrySet()) {
                    try {
                        this.insertColumn(schemaTemplate.getName(), entry.getValue(), schemaTemplate.getClass().getField(entry.getValue().columnName()).get(schemaTemplate));
                    }
                    catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                    catch (NoSuchFieldException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        catch (SQLException sqle) {
            throw new DatabaseWriteException("Error updating MySQL schema: " + sqle.getMessage());
        }
        catch (DatabaseTableInconsistencyException dtie) {
            log.error("Error updating MySQL schema." + dtie.getMessage(), dtie);
        }
        finally {
            close(conn, ps, rs);
        }
    }

    public void createTable(DataAccess data) throws DatabaseWriteException {
        Connection conn = JdbcConnectionManager.getConnection();
        PreparedStatement ps = null;

        try {
            StringBuilder fields = new StringBuilder();
            HashMap<Column, Object> columns = data.toDatabaseEntryList();
            Iterator<Column> it = columns.keySet().iterator();
            List<String> primary = new ArrayList<String>(2);

            Column column;
            while (it.hasNext()) {
                column = it.next();
                fields.append("`").append(column.columnName()).append("` ");
                fields.append(this.getDataTypeSyntax(column.dataType()));
                if (column.autoIncrement()) {
                    fields.append(" AUTO_INCREMENT");
                }
                if (column.notNull()) {
                    fields.append(" NOT NULL");
                }
                if (column.columnType().equals(Column.ColumnType.PRIMARY)) {
                    primary.add(column.columnName());
                }
                try {
                    Object defVal = data.getClass().getField(column.columnName()).get(data);
                    if (defVal != null) {
                        fields.append(" DEFAULT ").append(defVal.toString());
                    }
                }
                catch (IllegalAccessException e) {
                    // OOPS
                }
                catch (NoSuchFieldException e) {
                    // OOPS
                }

                if (it.hasNext()) {
                    fields.append(", ");
                }
            }
            if (primary.size() > 0) {
                fields.append(", PRIMARY KEY(`").append(
                        StringUtil.joinString(primary.toArray(new String[primary.size()]), ",", 0))
                        .append("`)");
            }
            ps = conn.prepareStatement("CREATE TABLE IF NOT EXISTS `" + data.getName() + "` (" + fields.toString() + ") CHARACTER SET utf8 COLLATE utf8_general_ci");
            ps.execute();
        }
        catch (SQLException ex) {
            throw new DatabaseWriteException("Error creating MySQL table '" + data.getName() + "'. " + ex.getMessage());
        }
        catch (DatabaseTableInconsistencyException ex) {
            log.error(ex.getMessage() + " Error creating MySQL table '" + data.getName() + "'. ", ex);
        }
        finally {
            close(conn, ps, null);
        }
    }
    

    public void insertColumn(String tableName, Column column, Object defVal) throws DatabaseWriteException {
        Connection conn = JdbcConnectionManager.getConnection();
        PreparedStatement ps = null;

        try {
            if (column != null && !column.columnName().trim().equals("")) {
                ps = conn.prepareStatement("ALTER TABLE `" + tableName + "` ADD `" + column.columnName() + "` "
                                                   + this.getDataTypeSyntax(column.dataType())
                                                   + (column.notNull() ? " NOT NULL" : "")
                                                   + (defVal != null ? " DEFAULT " + defVal.toString() : "")
                                          );
                ps.execute();
            }
        }
        catch (SQLException ex) {
            throw new DatabaseWriteException("Error adding MySQL column: " + column.columnName(), ex);
        }
        finally {
            close(conn, ps, null);
        }
    }

    public void deleteColumn(String tableName, String columnName) throws DatabaseWriteException {
        Connection conn = JdbcConnectionManager.getConnection();
        PreparedStatement ps = null;

        try {
            if (columnName != null && !columnName.trim().equals("")) {
                ps = conn.prepareStatement("ALTER TABLE `" + tableName + "` DROP `" + columnName + "`");
                ps.execute();
            }
        }
        catch (SQLException ex) {
            throw new DatabaseWriteException("Error deleting MySQL column: " + columnName);
        }
        finally {
            close(conn, ps, null);
        }
    }

    public boolean doesEntryExist(DataAccess data) throws DatabaseWriteException {
        Connection conn = JdbcConnectionManager.getConnection();
        PreparedStatement ps = null;
        ResultSet rs = null;
        boolean toRet = false;

        try {
            StringBuilder sb = new StringBuilder();
            HashMap<Column, Object> columns = data.toDatabaseEntryList();
            Iterator<Column> it = columns.keySet().iterator();

            Column column;
            while (it.hasNext()) {
                column = it.next();
                if (!column.autoIncrement()) {
                    Object o = columns.get(column);
                    if (o == null) {
                        continue;
                    }
                    if (sb.length() > 0) {
                        sb.append(" AND `").append(column.columnName()).append("`");
                    }
                    else {
                        sb.append("`").append(column.columnName()).append("`");
                    }
                    sb.append(" = ?");
                    // if (it.hasNext()) {
                    // sb.append("' = ? AND ");
                    // } else {
                    // sb.append("' = ?");
                    // }
                }
            }
            ps = conn.prepareStatement("SELECT * FROM `" + data.getName() + "` WHERE " + sb.toString());
            it = columns.keySet().iterator();

            int index = 1;

            while (it.hasNext()) {
                column = it.next();
                if (!column.autoIncrement()) {
                    Object o = columns.get(column);
                    if (o == null) {
                        continue;
                    }
                    setToStatement(index, columns.get(column), ps, column);
//                    ps.setObject(index, this.convert(columns.get(column)));
                    index++;
                }
            }
            rs = ps.executeQuery();
            if (rs != null) {
                toRet = rs.next();
            }
        }
        catch (SQLException ex) {
            throw new DatabaseWriteException(ex.getMessage() + " Error checking MySQL Entry Key in "
                                                     + data.toString()
            );
        }
        catch (DatabaseTableInconsistencyException ex) {
            log.error("", ex);
        }
        finally {
            close(conn, ps, rs);
        }
        return toRet;
    }

    public ResultSet getResultSet(Connection conn, DataAccess data, Map<String, Object> filters, boolean limitOne) throws DatabaseReadException {
        PreparedStatement ps;
        ResultSet toRet;

        try {

            if (filters.size() > 0) {
                StringBuilder sb = new StringBuilder();
                Object[] fieldNames = filters.keySet().toArray();
                for (int i = 0; i < fieldNames.length && i < fieldNames.length; i++) {
                    sb.append("`").append(fieldNames[i]);
                    if (i + 1 < fieldNames.length) {
                        sb.append("`=? AND ");
                    }
                    else {
                        sb.append("`=?");
                    }
                }
                if (limitOne) {
                    sb.append(" LIMIT 1");
                }
                ps = conn.prepareStatement("SELECT * FROM `" + data.getName() + "` WHERE " + sb.toString(), ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
                for (int i = 0; i < fieldNames.length && i < fieldNames.length; i++) {
                    String fieldName = String.valueOf(fieldNames[i]);
                    Column col = data.getColumnForName(fieldName);
                    if (col == null) {
                        throw new DatabaseReadException("Error fetching MySQL ResultSet in " + data.getName() + ". Column " + fieldNames[i] + " does not exist!");
                    }
                    setToStatement(i + 1, filters.get(fieldName), ps, col);
                }
            }
            else {
                if (limitOne) {
                    ps = conn.prepareStatement("SELECT * FROM `" + data.getName() + "` LIMIT 1", ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
                }
                else {
                    ps = conn.prepareStatement("SELECT * FROM `" + data.getName() + "`", ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
                }
            }
            toRet = ps.executeQuery();
        }
        catch (SQLException ex) {
            throw new DatabaseReadException("Error fetching MySQL ResultSet in " + data.getName(), ex);
        }
        catch (Exception ex) {
            throw new DatabaseReadException("Error fetching MySQL ResultSet in " + data.getName(), ex);
        }

        return toRet;
    }

    public List<String> getColumnNames(DataAccess data) {
        Statement statement = null;
        ResultSet resultSet = null;

        ArrayList<String> columns = new ArrayList<String>();
        String columnName;

        Connection connection = JdbcConnectionManager.getConnection();
        try {
            statement = connection.createStatement();
            resultSet = statement.executeQuery("SHOW COLUMNS FROM `" + data.getName() + "`");
            while (resultSet.next()) {
                columnName = resultSet.getString("field");
                columns.add(columnName);
            }
        }
        catch (SQLException ex) {
            log.error(ex.getMessage(), ex);
        }
        finally {
            if (statement != null) {
                try {
                    statement.close();
                }
                catch (SQLException e) {
                    log.error(e.getMessage(), e);
                }
            }
            close(connection, null, resultSet);
        }
        return columns;
    }

    private String getDataTypeSyntax(Column.DataType type) {
        switch (type) {
            case BYTE:
                return "TINYINT";
            case INTEGER:
                return "INT";
            case FLOAT:
                return "FLOAT";
            case DOUBLE:
                return "DOUBLE";
            case LONG:
                return "BIGINT";
            case SHORT:
                return "SMALLINT";
            case STRING:
                return "TEXT";
            case BOOLEAN:
                return "BOOLEAN";
        }
        return "";
    }

    /**
     * Replaces '*' character with '\\*' if the Object is a String.
     *
     * @param o
     *
     * @return string representation of the given object
     */
    private String convert(Object o) {
        if (o instanceof String && ((String)o).contains("*")) {
            ((String)o).replace("*", "\\*");
        }
        return String.valueOf(o);
    }

    /**
     * Sets the given object as the given type to the given index
     * of the given PreparedStatement.
     *
     * @param index
     *         the index to set to
     * @param o
     *         the object to set
     * @param ps
     *         the prepared statement
     * @param t
     *         the DataType hint
     *
     * @throws DatabaseWriteException
     *         when an SQLException was raised or when the data type doesn't match the objects type
     */
    private void setToStatement(int index, Object o, PreparedStatement ps, Column t) throws DatabaseWriteException {
        try {
            if (t.isList()) {
                ps.setString(index, getString((List<?>)o));
            }
            else {
                switch (t.dataType()) {
                    case BYTE:
                        ps.setByte(index, (Byte)o);
                        break;
                    case INTEGER:
                        ps.setInt(index, (Integer)o);
                        break;
                    case FLOAT:
                        ps.setFloat(index, (Float)o);
                        break;
                    case DOUBLE:
                        ps.setDouble(index, (Double)o);
                        break;
                    case LONG:
                        ps.setLong(index, (Long)o);
                        break;
                    case SHORT:
                        ps.setShort(index, (Short)o);
                        break;
                    case STRING:
                        ps.setString(index, (String)o);
                        break;
                    case BOOLEAN:
                        ps.setBoolean(index, (Boolean)o);
                        break;
                }
            }
        }
        catch (SQLException e) {
            throw new DatabaseWriteException("Failed to set property to prepared statement!", e);
        }
        catch (ClassCastException e) {
            throw new DatabaseWriteException("Failed to set property to prepared statement!", e);
        }
    }

    /**
     * Gets a Java List representation from the mysql String.
     *
     * @param type
     * @param field
     *
     * @return
     */
    private List<Comparable<?>> getList(Column.DataType type, String field) {
        List<Comparable<?>> list = new ArrayList<Comparable<?>>();
        if (field == null) {
            return list;
        }
        switch (type) {
            case BYTE:
                for (String s : field.split(this.LIST_REGEX)) {
                    if (s.equals(NULL_STRING)) {
                        list.add(null);
                        continue;
                    }
                    list.add(Byte.valueOf(s));
                }
                break;
            case INTEGER:
                for (String s : field.split(this.LIST_REGEX)) {
                    if (s.equals(NULL_STRING)) {
                        list.add(null);
                        continue;
                    }
                    list.add(Integer.valueOf(s));
                }
                break;
            case FLOAT:
                for (String s : field.split(this.LIST_REGEX)) {
                    if (s.equals(NULL_STRING)) {
                        list.add(null);
                        continue;
                    }
                    list.add(Float.valueOf(s));
                }
                break;
            case DOUBLE:
                for (String s : field.split(this.LIST_REGEX)) {
                    if (s.equals(NULL_STRING)) {
                        list.add(null);
                        continue;
                    }
                    list.add(Double.valueOf(s));
                }
                break;
            case LONG:
                for (String s : field.split(this.LIST_REGEX)) {
                    if (s.equals(NULL_STRING)) {
                        list.add(null);
                        continue;
                    }
                    list.add(Long.valueOf(s));
                }
                break;
            case SHORT:
                for (String s : field.split(this.LIST_REGEX)) {
                    if (s.equals(NULL_STRING)) {
                        list.add(null);
                        continue;
                    }
                    list.add(Short.valueOf(s));
                }
                break;
            case STRING:
                for (String s : field.split(this.LIST_REGEX)) {
                    if (s.equals(NULL_STRING)) {
                        list.add(null);
                        continue;
                    }
                    list.add(s);
                }
                break;
            case BOOLEAN:
                for (String s : field.split(this.LIST_REGEX)) {
                    if (s.equals(NULL_STRING)) {
                        list.add(null);
                        continue;
                    }
                    list.add(Boolean.valueOf(s));
                }
                break;
        }
        return list;
    }

    /**
     * Get the database entry for a Java List.
     *
     * @param list
     *
     * @return a string representation of the passed list.
     */
    public String getString(List<?> list) {
        if (list == null) {
            return NULL_STRING;
        }
        StringBuilder sb = new StringBuilder();
        for (Object o : list) {
            if (o == null) {
                sb.append(NULL_STRING);
            }
            else {
                sb.append(String.valueOf(o));
            }
            sb.append(this.LIST_REGEX);
        }
        if (sb.length() > 0) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }

    /**
     * Close a set of working data.
     * This will return all the data to the connection pool.
     * You can pass null for objects that are not relevant in your given context
     *
     * @param c
     *         the connection object
     * @param ps
     *         the prepared statement
     * @param rs
     *         the result set
     */
    private void close(Connection c, PreparedStatement ps, ResultSet rs) {
        try {
            if (ps != null) {
                ps.close();
            }
            if (rs != null) {
                rs.close();
            }
            if (c != null) {
                c.close();
            }
        }
        catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
    }
}
