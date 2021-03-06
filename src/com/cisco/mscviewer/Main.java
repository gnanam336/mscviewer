/*------------------------------------------------------------------
 * Copyright (c) 2014 Cisco Systems
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *------------------------------------------------------------------*/
package com.cisco.mscviewer;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.script.ScriptException;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;

import com.cisco.mscviewer.graph.Graph;
import com.cisco.mscviewer.gui.DataPanel;
import com.cisco.mscviewer.gui.ResultPanel;
import com.cisco.mscviewer.gui.MainFrame;
import com.cisco.mscviewer.gui.MainPanel;
import com.cisco.mscviewer.gui.graph.HeatGraphWindow;
import com.cisco.mscviewer.io.JsonLoader;
import com.cisco.mscviewer.io.Loader;
import com.cisco.mscviewer.model.Entity;
import com.cisco.mscviewer.model.Event;
import com.cisco.mscviewer.model.Interaction;
import com.cisco.mscviewer.model.MSCDataModel;
import com.cisco.mscviewer.model.ViewModel;
import com.cisco.mscviewer.script.Python;
import com.cisco.mscviewer.script.ScriptResult;
import com.cisco.mscviewer.util.MSCViewerError;
import com.cisco.mscviewer.util.Report;
import com.cisco.mscviewer.util.Resources;
import com.cisco.mscviewer.util.Utils;

abstract class Opt {
    char shortName;
    String longName;
    String descr;
    boolean hasArg;

    public Opt(char sh, String ln, boolean arg, String ds) {
        shortName = sh;
        longName = ln;
        hasArg = arg;
        descr = ds;
    }

    abstract void found(String arg);
}

/**
 * class containing the main() method.
 * 
 * @author Roberto Attias
 * @since Jun 2012
 */
public class Main {
    public static final String PYPATH = "pypath";
    public static final String VERSION = "2.1.0";
    public static final boolean WITH_BLOCKS = true;
    private static boolean topologicalSorting = true;
    
    private static Loader loader;
   @SuppressWarnings("unused")
    private static String script;
    private static String loaderClass = "JsonLoader";
    static boolean batchMode = false;
    private static String batchFun = null;
    private static String plugins;
    private static HeatGraphWindow graph;

    private static final Opt[] opts = new Opt[] {
            new Opt('h', "help", true, "shows this help") {
                @Override
                void found(String arg) {
                    printHelp();
                    System.exit(0);
                }
            },
            new Opt('b', "batch", true,
                    "executes the passed python script in batch mode") {
                @Override
                void found(String arg) {
                    Main.batchMode = true;
                    final int idx = arg.indexOf(',');
                    if (idx == -1)
                        Main.script = arg;
                    else {
                        Main.script = arg.substring(0, idx);
                        batchFun = arg.substring(idx + 1);
                    }
                }
            },
            new Opt('p', PYPATH, true, "specify a Python module search path") {
                @Override
                void found(String arg) {
                    appendToPyPath(arg);
                }
            },
            new Opt('s', "script", true,
                    "opens the GUI and executes the passed python script") {
                @Override
                void found(String arg) {
                    Main.script = arg;
                }
            },
            new Opt('l', "loader", true, "specify loader to use for input file") {
                @Override
                void found(String arg) {
                    Main.loaderClass = arg;
                }
            },
            new Opt('r', "resource", true,
                    "specify a path for domain-specific resources") {
                @Override
                void found(String arg) {
                    Main.plugins = arg;
                    for (final String s : arg.split(File.pathSeparator)) {
                        final String dir = s + "/script";
                        if (new File(dir).isDirectory())
                            appendToPyPath(dir);
                    }
                }
            },
            new Opt('t', "no-topological-sorting", true, "disables topological sorting") {
                @Override
                void found(String arg) {
                    Main.topologicalSorting  = false;
                }
            }            
    };

    private Main() {}
    
    private static final void appendToPyPath(String s) {
        String v = System.getProperty(PYPATH);
        if (v != null && v.length() > 0)
            v += File.pathSeparator + s;
        else
            v = s;
        System.setProperty(PYPATH, v);
        System.out.println("PYPATH="+v);
    }

    private static void printHelp() {
        System.out.println("mscviewer options [file]");
        System.out.println("  starts mscviewer");
        for (final Opt opt : opts) {
            System.out.println("-" + opt.shortName + "\t--" + opt.longName
                    + (opt.hasArg ? " arg\t" : "\t") + opt.descr);
        }

    }

    private static void setLookAndFeel() {
        try {
            for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (UnsupportedLookAndFeelException|IllegalAccessException|InstantiationException|ClassNotFoundException e) {
            Logger logger = Logger.getLogger(Main.class.getName());
            logger.log(Level.INFO,"Requested L&F was not supported. defaulting.", e);                        
        } 
    }
    
    public static void main(String[] args) throws IOException,
    ClassNotFoundException, NoSuchMethodException,
    IllegalAccessException,
    InstantiationException, ScriptException, InterruptedException,
    InvocationTargetException {
        try {
            System.setProperty(PYPATH, Utils.getInstallDir()
                    + "/resources/default/script");

            setupUIDefaults();
            final int idx = processOptions(args);

            final String fname = (idx < args.length) ? args[idx] : null;
            final Class<?> cl = Class.forName("com.cisco.mscviewer.io." + loaderClass);

            Resources.init(Main.plugins);

            loader = (Loader) cl.newInstance();
            if (batchMode()) {
                if (fname == null) {
                    System.err.println("Missing input file");
                    System.exit(1);
                }
                loader.load(fname, MSCDataModel.getInstance(), true);
            } else {
                setLookAndFeel();
                SwingUtilities.invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                        int scrWidth = screenSize.width;
                        int scrHeight = screenSize.height;
                        int w = scrWidth*3/4;
                        int h = scrHeight*3/4;
                        int x = (scrWidth-w)/2;
                        int y = (scrHeight-h)/2;
                        MainFrame mf = new MainFrame(x, y, w, h);
                        mf.setVisible(true);
                        if (fname != null)
                            mf.loadFile(fname);
                    }
                });
               
            }
            if (script != null) {
                loader.waitIfLoading();
                final MainPanel mp = (MainFrame.getInstance() != null) ? MainFrame.getInstance().getMainPanel() : null;
                final Python p = new Python(mp);
                final ScriptResult sr = new ScriptResult();
                final String text = new String(Files.readAllBytes(Paths.get(script)),
                        StandardCharsets.UTF_8);
                p.exec(text);
                if (batchFun != null) {
                    p.eval(batchFun, sr);
                }
            }
        } catch(Exception t) {
            Report.exception("Exception while executing main program", t);
        }
        if (batchMode())
            System.exit(0);
    }

    private static int processOptions(String[] args) {
        int idx = 0;
        final int len = args.length;
        while (idx < len && args[idx].startsWith("-")) {
            char sa = '\0';
            String la = null;
            if (args[idx].length() == 2) {
                sa = args[idx].charAt(1);
                la = null;
            } else if (args[idx].charAt(1) == '-') {
                sa = 0;
                la = args[idx].substring(2);
            } else {
                System.err.println("Invalid option " + args[idx]);
                System.exit(1);
            }
            int i;
            for (i = 0; i < opts.length; i++) {
                if (opts[i].shortName == sa || opts[i].longName.equals(la)) {
                    String arg;
                    if (opts[i].hasArg && len > idx + 1) {
                        arg = args[idx + 1];
                        idx += 2;
                    } else {
                        arg = null;
                        idx += 1;
                    }
                    opts[i].found(arg);
                    break;
                }
            }
            if (i == opts.length) {
                System.err.println("Invalid option " + args[idx]);
                System.exit(1);
            }
        }
        return idx;
    }

    private static void setupUIDefaults() {
        final Font f = (Font) UIManager.getDefaults().get("Tree.font");
        UIManager.put("Button.font", f);
        UIManager.put("ToggleButton.font", f);
        UIManager.put("RadioButton.font", f);
        UIManager.put("CheckBox.font", f);
        UIManager.put("ColorChooser.font", f);
        UIManager.put("ComboBox.font", f);
        UIManager.put("Label.font", f);
        UIManager.put("List.font", f);
        UIManager.put("MenuBar.font", f);
        UIManager.put("MenuItem.font", f);
        UIManager.put("RadioButtonMenuItem.font", f);
        UIManager.put("CheckBoxMenuItem.font", f);
        UIManager.put("Menu.font", f);
        UIManager.put("PopupMenu.font", f);
        UIManager.put("OptionPane.font", f);
        UIManager.put("Panel.font", f);
        UIManager.put("ProgressBar.font", f);
        UIManager.put("ScrollPane.font", f);
        UIManager.put("Viewport.font", f);
        UIManager.put("TabbedPane.font", f);
        UIManager.put("Table.font", f);
        UIManager.put("TableHeader.font", f);
        UIManager.put("TextField.font", f);
        UIManager.put("PasswordField.font", f);
        UIManager.put("TextArea.font", f);
        UIManager.put("TextPane.font", f);
        UIManager.put("EditorPane.font", f);
        UIManager.put("TitledBorder.font", f);
        UIManager.put("ToolBar.font", f);
        UIManager.put("ToolTip.font", f);

    }

    public static boolean batchMode() {
        return batchMode;
    }

    public static MainFrame getMainFrame() {
        return MainFrame.getInstance();
    }

    public static Event getSelectedEvent() {
        return MainFrame.getInstance().getMainPanel().getMSCRenderer()
                .getSelectedEvent();
    }

    public static Interaction getSelectedInteraction() {
        return MainFrame.getInstance().getMainPanel().getMSCRenderer()
                .getSelectedInteraction();
    }

    public static void open(final Entity en) {
        Utils.dispatchOnAWTThreadNow(new Runnable() {
            @Override
            public void run() {
                MainFrame.getInstance().getViewModel().add(en);
            }
        });
    }

    public static Entity open(final String id) {
        final Entity en = MSCDataModel.getInstance().getEntity(id);
        Utils.dispatchOnAWTThreadNow(new Runnable() {
            @Override
            public void run() {
                final MainFrame mf = MainFrame.getInstance();
                if (en == null) {
                    final StringBuilder ents = new StringBuilder();
                    for (final Iterator<Entity> it = MSCDataModel.getInstance()
                            .getEntityIterator(false); it.hasNext();)
                        ents.append(it.next().getId() + ", ");
                    throw new MSCViewerError(
                            "Entity '"
                                    + id
                                    + "' not present in model. Available entities are: "
                                    + ents.toString());
                }
                mf.getViewModel().add(en);
            }
        });
        return en;
    }

    public static void open(final Entity[] en) {
        Utils.dispatchOnAWTThreadNow(new Runnable() {
            @Override
            public void run() {
                MainFrame.getInstance().getViewModel().add(en);
            }
        });
    }

    public static void open(final Event ev) {
        Utils.dispatchOnAWTThreadNow(new Runnable() {
            @Override
            public void run() {
                final MainFrame mf = MainFrame.getInstance();
                final ViewModel vm = mf.getViewModel();
                vm.add(ev.getEntity());
                final int idx = vm.indexOf(ev);
                mf.getMainPanel().makeEventWithIndexVisible(idx);
                mf.getMainPanel().getMSCRenderer()
                        .setSelectedEventByViewIndex(idx);
            }
        });
    }

    public static void hide(Entity en) {
        MainFrame.getInstance().getEntityHeader().remove(en);
    }

    public static void addResult(final String res) {
        Utils.dispatchOnAWTThreadLater(new Runnable() {
            @Override
            public void run() {
                MainFrame.getInstance().addResult(res);
            }
        });
    }

    public static void load(String path) throws IOException,
            InvocationTargetException, InterruptedException {
        final Loader l = new JsonLoader();
        final MainFrame mf = MainFrame.getInstance();
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                mf.getViewModel().reset();
                MSCDataModel.getInstance().reset();
            }
        });
        l.load(path, MSCDataModel.getInstance(), true);
        l.waitIfLoading();
    }

    public static void clearModel() {
        final MainFrame mf = MainFrame.getInstance();
        mf.getViewModel().reset();
        MSCDataModel.getInstance().reset();
    }

    public static void maximize() {
        final MainFrame mf = MainFrame.getInstance();
        mf.setExtendedState(mf.getExtendedState() | Frame.MAXIMIZED_BOTH);
    }

    public static void quit() {
        System.exit(0);
    }

    public static void captureDiagram(final String fname) {
        Utils.dispatchOnAWTThreadNow(new Runnable() {
            @Override
            public void run() {
                Utils.getPNGSnapshot("MainPanelJSP", fname);
            }
        });
    }

    public static void captureGUI(final String compName, final String fname) {
        Utils.dispatchOnAWTThreadNow(new Runnable() {
            @Override
            public void run() {
                Utils.getPNGSnapshot(compName, fname);
            }
        });
    }

    public static void select(Event ev) {
        Utils.dispatchOnAWTThreadNow(new Runnable() {
            @Override
            public void run() {
                MainFrame.getInstance().getMainPanel().getMSCRenderer()
                        .setSelectedEvent(ev);
            }
        });
    }

    public static void showDataTab() {
        MainFrame.getInstance().showTab(DataPanel.NAME);
    }

    public static void showResultsTab() {
        MainFrame.getInstance().showTab(ResultPanel.NAME);
    }

    public static void expandEntityTree() {
        MainFrame.getInstance().getEntityTree().expandAll();
    }

    public static void setLeftSplitPaneDividerLocation(float f) {
        MainFrame.getInstance().getLeftSplitPane().setDividerLocation(0);
        MainFrame.getInstance().getLeftSplitPane().setDividerLocation(f);
    }

    public static void setRightSplitPaneDividerLocation(float f) {
        MainFrame.getInstance().getRightSplitPane().setDividerLocation(0);
        MainFrame.getInstance().getRightSplitPane().setDividerLocation(f);
    }

    public static void setLeftRightSplitPaneDividerLocation(float f) {
        MainFrame.getInstance().getLeftRightSplitPane().setDividerLocation(0);
        MainFrame.getInstance().getLeftRightSplitPane().setDividerLocation(f);
    }

    public static void show(Graph g) {
        try {
            graph = new HeatGraphWindow(g);
        } catch (final Exception t) {
            Report.exception("Exception while opening Heat Graph", t);
        }
    }
    
    public static HeatGraphWindow getGraph() {
        return graph;
    }
    
    public static boolean shouldSortTopologically() {
        return topologicalSorting;
    }
    
    public static void setTopologicalSorting(boolean v) {
        topologicalSorting = v;
    }

}
