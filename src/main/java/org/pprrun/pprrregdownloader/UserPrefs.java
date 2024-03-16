/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.pprrun.pprrregdownloader;

/**
 *
 * @author John Garner <segfaultcoredump@gmail.com>
 */
import java.io.File;
import java.util.HexFormat;
import java.util.prefs.Preferences;



/**
 *
 * @author John
 */
public class UserPrefs {
    /**
    * SingletonHolder is loaded on the first execution of Singleton.getInstance() 
    * or the first access to SingletonHolder.INSTANCE, not before.
    */
    
    Preferences prefs = Preferences.userRoot().node("PPRRRegDownloader");
    
    
    private static class SingletonHolder { 
            private static final UserPrefs INSTANCE = new UserPrefs();
    }

    public static UserPrefs getInstance() {
            return SingletonHolder.INSTANCE;
    }
    
    public void setPPRRScoreDir(File f) {
        prefs.put("PPRRScoreDir",f.getAbsolutePath());
    }
     
    
    public File getPPRRScoreDir(){
        File cwd = new File(prefs.get("PPRRScoreDir", System.getProperty("user.home")));
        if (cwd.exists() && cwd.isDirectory()){
            return cwd;
        } 

        return new File(System.getProperty("user.home"));
        
    }
    
    public void setGlobalPrefs(String key, String value){
        prefs.put(key, value);
    }
    
    public String getGlobalPrefs(String key){
        return prefs.get(key, "");
    }
    
    public void setRSUPassword(String password){
        prefs.put("RSUPassword", HexFormat.of().formatHex(password.getBytes()));
    }
    
    public String getRSUPassword(){
        if (!"".equals(prefs.get("RSUPassword",""))){
            return new String(HexFormat.of().parseHex(prefs.get("RSUPassword","")));
        } else return "";
    }
    
    
}
