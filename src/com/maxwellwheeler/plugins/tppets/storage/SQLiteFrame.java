package com.maxwellwheeler.plugins.tppets.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;

import com.maxwellwheeler.plugins.tppets.TPPets;

public class SQLiteFrame extends DBGeneral {
    private TPPets thisPlugin;
    private String dbPath;
    private String dbName;
    
    public SQLiteFrame(String dbPath, String dbName, TPPets thisPlugin) {
        super(thisPlugin);
        this.dbPath = dbPath;
        this.dbName = dbName;
        this.thisPlugin = thisPlugin;
    }
    
    @Override
    public Connection getConnection() {
        File dbDir = new File(dbPath);
        if (!dbDir.exists()) {
            try {
                dbDir.mkdir();
            } catch (SecurityException e) {
                thisPlugin.getLogger().log(Level.SEVERE, "Security Exception creating database");
            }
        }
        
        try {
            Connection dbc = DriverManager.getConnection(getJDBCPath());
            return dbc;
        } catch (SQLException e) {
            thisPlugin.getLogger().log(Level.SEVERE, "SQL Exception creating database");
            return null;
        }
    }

    private String getJDBCPath() {
        return "jdbc:sqlite:" + dbPath + "\\" + dbName + ".db";
    }

}