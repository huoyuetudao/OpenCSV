package com.opencsv;
/**
 Copyright 2005 Bytecode Pty Ltd.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

import java.io.IOException;
import java.sql.*;
import java.text.SimpleDateFormat;

/**
 * helper class for processing JDBC ResultSet objects.
 */
public class ResultSetHelperService {

    // note: we want to maintain compatibility with Java 5 VM's
    // These types don't exist in Java 5

    public static int RESULT_FETCH_SIZE = 10000;
    static String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";
    static String DEFAULT_TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss.S";
    public int columnCount;
    public String[] columnNames;
    public String[] columnTypes;
    public int[]    columnTypesI;
    public String[] rowValue;
    public long cost =0;
    private SimpleDateFormat dateFormat;
    private SimpleDateFormat timeFormat;
    private SimpleDateFormat timeTZFormat;
    private ResultSet rs;

    /**
     * Default Constructor.
     */
    public ResultSetHelperService(ResultSet res, int fetchSize) throws SQLException {
        long sec= System.nanoTime();
        rs = res;
        rs.setFetchSize(fetchSize);
        rs.setFetchDirection(ResultSet.FETCH_FORWARD);
        ResultSetMetaData metadata = rs.getMetaData();
        columnCount = metadata.getColumnCount();
        rowValue = new String[columnCount];
        columnNames = new String[columnCount];
        columnTypes = new String[columnCount];
        columnTypesI= new int[columnCount];
        for (int i = 0; i < metadata.getColumnCount(); i++) {
            int type = metadata.getColumnType(i + 1);
            String value;
            switch (type) {
                case Types.BIT:
                case Types.JAVA_OBJECT:
                    value = "object";
                    break;
                case Types.BOOLEAN:
                    value = "boolean";
                    break;
                case Types.BIGINT:
                case Types.DECIMAL:
                case Types.DOUBLE:
                case Types.FLOAT:
                case Types.REAL:
                case Types.NUMERIC:
                case Types.INTEGER:
                case Types.TINYINT:
                case Types.SMALLINT:
                    value = "number";
                    break;
                case Types.TIME:
                    value = "date";
                    break;
                case Types.DATE:
                    value = "date";
                    break;
                case Types.TIMESTAMP:
                case -100:
                    value = "timestamp";
                    break;
                case -101:
                case -102:
                    value = "timestamptz";
                    break;
                case Types.BLOB:
                    value = "blob";
                    break;
                case Types.NCLOB:
                case Types.CLOB:
                    value = "clob";
                    break;
                default:
                    value = "string";
            }
            columnTypesI[i]= type;
            columnTypes[i] = value.intern();
            columnNames[i] = metadata.getColumnName(i + 1).intern();
        }
        cost += System.nanoTime()-sec;
    }

    public ResultSetHelperService(ResultSet res) throws SQLException {
        this(res, RESULT_FETCH_SIZE);
    }


    /**
     * Get all the column values from the result set.
     *
     * @return - String array containing all the column values.
     * @throws SQLException - thrown by the result set.
     * @throws IOException  - thrown by the result set.
     */
    public String[] getColumnValues() throws SQLException, IOException {
        return this.getColumnValues(true, DEFAULT_DATE_FORMAT, DEFAULT_TIMESTAMP_FORMAT);
    }

    /**
     * Get all the column values from the result set.
     *
     * @param trim - values should have white spaces trimmed.
     * @return - String array containing all the column values.
     * @throws SQLException - thrown by the result set.
     * @throws IOException  - thrown by the result set.
     */
    public String[] getColumnValues(boolean trim) throws SQLException, IOException {
        return this.getColumnValues(trim, DEFAULT_DATE_FORMAT, DEFAULT_TIMESTAMP_FORMAT);
    }

    /**
     * Get all the column values from the result set.
     *
     * @param trim             - values should have white spaces trimmed.
     * @param dateFormatString - format String for dates.
     * @param timeFormatString - format String for timestamps.
     * @return - String array containing all the column values.
     * @throws SQLException - thrown by the result set.
     * @throws IOException  - thrown by the result set.
     */
    public String[] getColumnValues(boolean trim, String dateFormatString, String timeFormatString) throws SQLException, IOException {
        long sec=System.nanoTime();
        if (!rs.next()) {
            rs.close();
            return null;
        }
        for (int i = 0; i < columnCount; i++) {
            getColumnValue(columnTypes[i], i + 1, trim, dateFormatString, timeFormatString);
        }
        cost += System.nanoTime()-sec;
        return rowValue;
    }

    /**
     * changes an object to a String.
     *
     * @param obj - Object to format.
     * @return - String value of an object or empty string if the object is null.
     */
    protected String handleObject(Object obj) {
        return obj == null ? "" : String.valueOf(obj);
    }


    protected String handleDate(int columnIndex, String dateFormatString) throws SQLException {
        java.sql.Date date = rs.getDate(columnIndex);
        if (dateFormat == null) {
            DEFAULT_DATE_FORMAT = dateFormatString;
            dateFormat = new SimpleDateFormat(dateFormatString);
        }
        return date == null ? null : dateFormat.format(date);
    }


    protected String handleTimestamp(int columnIndex, String timestampFormatString) throws SQLException {
        java.sql.Timestamp timestamp = rs.getTimestamp(columnIndex);
        if (timeFormat == null) {
            DEFAULT_TIMESTAMP_FORMAT = timestampFormatString;
            timeFormat = new SimpleDateFormat(timestampFormatString);
        }
        return timestamp == null ? null : timeFormat.format(timestamp);
    }

    protected String handleTimestampTZ(int columnIndex, String timestampFormatString) throws SQLException {
        java.sql.Timestamp timestamp = rs.getTimestamp(columnIndex);
        if (timeFormat == null) {
            timeTZFormat = new SimpleDateFormat(timestampFormatString + " S");
        }
        return timestamp == null ? null : timeTZFormat.format(timestamp);
    }

    private void getColumnValue(String colType, int colIndex, boolean trim, String dateFormatString, String timestampFormatString) throws SQLException, IOException {
        rowValue[colIndex - 1] = "";
        switch (colType) {
            case "object":
                rowValue[colIndex - 1] = handleObject(rs.getObject(colIndex));
                break;
            case "boolean":
                boolean b = rs.getBoolean(colIndex);
                rowValue[colIndex - 1] = Boolean.valueOf(b).toString();
                break;
            case "blob":
                Blob bl = rs.getBlob(colIndex);
                if (bl != null) {
                    byte[] src = bl.getBytes(1, (int) bl.length());
                    bl.free();
                    StringBuilder sb=new StringBuilder(src.length*2);
                    for (int i = 0; i < src.length; i++) {
                        int v = src[i] & 0xFF;
                        String hv = Integer.toHexString(v);
                        if (hv.length() < 2) {
                            sb.append(0);
                        }
                        sb.append(hv);
                    }
                    rowValue[colIndex - 1] = sb.toString().toUpperCase();
                }
                break;
            case "clob":
                Clob c = rs.getClob(colIndex);
                if (c != null) {
                    rowValue[colIndex - 1] = c.getSubString(1, (int) c.length());
                    c.free();
                }
                break;
            case "date":
            case "time":
                rowValue[colIndex - 1] = handleDate(colIndex, dateFormatString);
                break;
            case "timestamp":
                rowValue[colIndex - 1] = handleTimestamp(colIndex, timestampFormatString);
                break;
            case "timestamptz":
                rowValue[colIndex - 1] = handleTimestampTZ(colIndex, timestampFormatString);
                break;
            default:
                rowValue[colIndex - 1] = rs.getString(colIndex);
        }

        if (rowValue[colIndex - 1] == null) {
            rowValue[colIndex - 1] = "";
        }
        if (trim) rowValue[colIndex - 1] = rowValue[colIndex - 1].trim();
    }
}
