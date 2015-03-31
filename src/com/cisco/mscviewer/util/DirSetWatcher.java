/*------------------------------------------------------------------
 * Copyright (c) 2014 Cisco Systems
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *------------------------------------------------------------------*/
/**
 * @author Roberto Attias
 * @since  Aug 2014
 */
package com.cisco.mscviewer.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author rattias
 */
public class DirSetWatcher extends Thread { 
    private final String[] paths;
    private final Vector<Watcher> watchers;
    private Thread dirWatcherThread;
    private boolean exit;
    private WatchKey wk = null;
    private WatchService ws = null;
    private Thread wt;
    
    public DirSetWatcher(String[] arr) {        
        paths = arr.clone();
        watchers = new Vector<Watcher>();
        FileSystem defaultFS = FileSystems.getDefault();
        try {
            ws = defaultFS.newWatchService();
            for(String ps: paths) {
                Path p = defaultFS.getPath(ps);
                p.register(ws,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
            }
        } catch (IOException ex) {
        }
    }

    public void add(Watcher w) {
        watchers.add(w);
    }
    
 
    @Override
    public void run() {
        dirWatcherThread = Thread.currentThread();
        HashMap<String, Long> modificationTime = new HashMap<String, Long>(); 

        if (wt != null)
            throw new Error("Internal Error: second thread spawned");
        wt = Thread.currentThread();
        // loop forever to watch directories
        try {
            while(true) {
                do {
                    wk = ws.take();
                }while(wk == null);
                            
                long ts = System.currentTimeMillis();
                if (exit) {
                    System.out.println("Dir watcher exiting");
                    break;
                }
                if (wk != null) {
                    String parentPath = ((Path)wk.watchable()).toString();
                    List<WatchEvent<?>> events = wk.pollEvents();
                    for (WatchEvent<?> event : events) { 
                        Path path = (Path)event.context();
                        File  file = new File(parentPath, path.toString());
                        System.out.println("file is "+file.getPath());
                        Long mod = modificationTime.get(file.getPath());
                        long lm = file.lastModified();
                        if (mod != null && lm - mod < 100) {
                            continue;
                        }
                        modificationTime.put(file.getPath(), lm);
                        for(Watcher w: watchers)
                            w.event(parentPath, event);
                        Kind<?> kind = event.kind();
                            
                        if (kind.equals(StandardWatchEventKinds.ENTRY_CREATE)) {
                            if (file.isDirectory()) {
                                path.register(ws, 
                                        StandardWatchEventKinds.ENTRY_CREATE,
                                        StandardWatchEventKinds.ENTRY_MODIFY,
                                        StandardWatchEventKinds.ENTRY_DELETE);
                            }
                            //System.out.println(ts+": Entry created:" + file.getPath());
                        } else if (kind.equals(StandardWatchEventKinds.ENTRY_DELETE)) {
                            System.out.println(ts+": Entry deleted:" + path);
                        } else if (kind.equals(StandardWatchEventKinds.ENTRY_MODIFY)) {
                            //System.out.println(ts+": ["+cnt+"] Entry modified:" + path);
                        }
                    }                    
                    if (!wk.reset())
                        break;
                }
                Thread.sleep(1000);
            }
        } catch (InterruptedException ex) {
        } catch (IOException ex) {
            Logger.getLogger(DirSetWatcher.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if (wk != null)
                wk.cancel();
            try {
                ws.close();
            } catch (IOException ex) {}
        }
        System.out.println("thread "+Thread.currentThread().getName()+" dying");
    }

    @SuppressWarnings("deprecation")
    public void destroy() {
        if (dirWatcherThread != null) {
            exit = true;
            dirWatcherThread.interrupt();
            try {
                dirWatcherThread.join();
            } catch (InterruptedException ex) {                
            }            
            System.out.println("destroyed thread "+dirWatcherThread.getName());
        }
        dirWatcherThread = null;
    }
    
    public static void main(String[] args) throws InterruptedException {
        DirSetWatcher dw = new DirSetWatcher(new String[]{"c:/temp/dwtest/a", "c:/temp/dwtest/b"});         
        dw.start();
        Thread.sleep(100000);
    }
}    

