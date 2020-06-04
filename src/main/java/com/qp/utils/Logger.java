package com.qp.utils;

import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {
    public static void log(String str){
        String _pszFile = "./TestServer.log";
        try {
            LocalDateTime dateTime = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String szLOG = dateTime.format(formatter)+" "+str+"\r\n";
            System.out.print(szLOG);
            FileOutputStream out = new FileOutputStream(_pszFile, true);
            out.write(szLOG.getBytes());
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
