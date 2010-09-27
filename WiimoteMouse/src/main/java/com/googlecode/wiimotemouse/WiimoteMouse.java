package com.googlecode.wiimotemouse;

import java.awt.AWTException;
import java.awt.CheckboxMenuItem;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import javax.imageio.ImageIO;

import wiiusej.WiiUseApiManager;
import wiiusej.Wiimote;
import wiiusej.wiiusejevents.physicalevents.ExpansionEvent;
import wiiusej.wiiusejevents.physicalevents.IREvent;
import wiiusej.wiiusejevents.physicalevents.MotionSensingEvent;
import wiiusej.wiiusejevents.physicalevents.WiimoteButtonsEvent;
import wiiusej.wiiusejevents.utils.WiimoteListener;
import wiiusej.wiiusejevents.wiiuseapievents.ClassicControllerInsertedEvent;
import wiiusej.wiiusejevents.wiiuseapievents.ClassicControllerRemovedEvent;
import wiiusej.wiiusejevents.wiiuseapievents.DisconnectionEvent;
import wiiusej.wiiusejevents.wiiuseapievents.GuitarHeroInsertedEvent;
import wiiusej.wiiusejevents.wiiuseapievents.GuitarHeroRemovedEvent;
import wiiusej.wiiusejevents.wiiuseapievents.NunchukInsertedEvent;
import wiiusej.wiiusejevents.wiiuseapievents.NunchukRemovedEvent;
import wiiusej.wiiusejevents.wiiuseapievents.StatusEvent;

public class WiimoteMouse implements WiimoteListener, ActionListener,
        ItemListener {

    private static final File CONFIG_FILE =
        new File(System.getProperty("user.home"), ".wiimotemouse.xml");

    private Wiimote wiimote = null;

    private Integer wiimoteSync = new Integer(0);

    private boolean cancelled = false;

    private Integer cancelSync = new Integer(0);

    private boolean connecting = false;

    private Integer connectSync = new Integer(0);

    private int sensitivity = 3;

    private CheckboxMenuItem[] sensitivityItems = new CheckboxMenuItem[5];

    private CheckboxMenuItem aboveScreen = new CheckboxMenuItem("Above Screen",
            true);

    private CheckboxMenuItem belowScreen = new CheckboxMenuItem("Below Screen",
            false);

    private int x = 0;

    private int y = 0;

    private int width = 0;

    private int height = 0;

    private GraphicsDevice[] devices = null;

    private CheckboxMenuItem[] selectedDevices = new CheckboxMenuItem[0];

    private Menu screenMenu = new Menu("Screens");

    private Menu sensorBarMenu = new Menu("Sensor Bar");

    private Menu sensitivityMenu = new Menu("Sensitivity");

    private MenuItem exitItem = new MenuItem("Exit");

    private MenuItem redetectScreensItem = new MenuItem("Redetect Screens");

    private Integer deviceSync = new Integer(0);

    private Robot[] robots = null;

    private TrayIcon trayIcon = null;

    private Image wiimoteConnectedImage = null;

    private Image wiimoteDisconnectedImage = null;

    private Properties config = new Properties();

    public WiimoteMouse() throws IOException, AWTException {
        detectScreens();

        if (CONFIG_FILE.exists()) {
            FileInputStream input = new FileInputStream(CONFIG_FILE);
            config.load(input);
            input.close();
        } else {
            config.setProperty("sensitivity", String.valueOf(sensitivity));
            config.setProperty("sensorBarPosition", "above");
            for (int i = 0; i < selectedDevices.length; i++) {
                config.setProperty("screen" + i + "Selected", "true");
            }
        }
        for (int i = 0; i < selectedDevices.length; i++) {
            selectedDevices[i].setState(config.getProperty(
                    "screen" + i + "Selected", "true").equals("true"));
        }
        updateSelectedDevices();

        PopupMenu menu = new PopupMenu("Menu");

        sensorBarMenu.add(aboveScreen);
        sensorBarMenu.add(belowScreen);
        aboveScreen.addItemListener(this);
        belowScreen.addItemListener(this);
        if (config.getProperty("sensorBarPosition").equals("above")) {
            aboveScreen.setState(true);
            belowScreen.setState(false);
        } else {
            aboveScreen.setState(false);
            belowScreen.setState(true);
        }

        sensitivity = Integer.parseInt(config.getProperty("sensitivity"));
        for (int i = 1; i <= 5; i++) {
            sensitivityItems[i - 1] = new CheckboxMenuItem(String.valueOf(i));
            sensitivityItems[i - 1].setState(sensitivity == i);
            sensitivityItems[i - 1].addItemListener(this);
            sensitivityMenu.add(sensitivityItems[i - 1]);
        }

        exitItem.addActionListener(this);
        redetectScreensItem.addActionListener(this);

        menu.add(screenMenu);
        menu.add(sensorBarMenu);
        menu.add(sensitivityMenu);
        menu.addSeparator();
        menu.add(exitItem);

        wiimoteConnectedImage = ImageIO.read(getClass().getResourceAsStream(
            "/wiimotemouseon.png"));
        wiimoteDisconnectedImage = ImageIO.read(getClass().getResourceAsStream(
            "/wiimotemouseoff.png"));

        trayIcon = new TrayIcon(wiimoteDisconnectedImage, "WiimoteMouse", menu);
        SystemTray.getSystemTray().add(trayIcon);

        connectToWiimote();
    }

    public void saveConfig() {
        try {
            FileOutputStream output = new FileOutputStream(CONFIG_FILE);
            config.store(output, null);
            output.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void detectScreens() {
        synchronized (deviceSync) {
            GraphicsEnvironment environment =
                GraphicsEnvironment.getLocalGraphicsEnvironment();
            devices = environment.getScreenDevices();
            selectedDevices = new CheckboxMenuItem[devices.length];
            robots = new Robot[devices.length];
            screenMenu.removeAll();
            for (int i = 0; i < devices.length; i++) {
                try {
                    robots[i] = new Robot(devices[i]);
                    selectedDevices[i] = new CheckboxMenuItem(
                                String.valueOf(i), true);
                    screenMenu.add(selectedDevices[i]);
                    selectedDevices[i].addItemListener(this);
                } catch (AWTException e) {
                    e.printStackTrace();
                }
            }
            screenMenu.addSeparator();
            screenMenu.add(redetectScreensItem);
        }
    }

    public void updateSelectedDevices() {
        synchronized (deviceSync) {
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE;

            for (int i = 0; i < selectedDevices.length; i++) {
                if (selectedDevices[i].getState()) {
                    Rectangle bounds =
                        devices[i].getDefaultConfiguration().getBounds();
                    minX = Math.min(bounds.x, minX);
                    maxX = Math.max(bounds.x + bounds.width, maxX);
                    minY = Math.min(bounds.y, minY);
                    maxY = Math.max(bounds.y + bounds.height, maxY);
                }
            }

            synchronized (wiimoteSync) {
                x = minX;
                y = minY;
                width = maxX - minX;
                height = maxY - minY;
                if (wiimote != null) {
                    wiimote.setVirtualResolution(width, height);
                }
            }
        }
    }

    public void connectToWiimote() {
        synchronized (connectSync) {
            if (connecting) {
                while ((wiimote == null) && !cancelled) {
                    try {
                        connectSync.wait();
                    } catch (InterruptedException e) {
                        // Do Nothing
                    }
                }
                return;
            }
            connecting = true;
        }

        synchronized (cancelSync) {
            cancelled = false;
            while ((wiimote == null) && !cancelled) {
                if (!cancelled) {
                    Wiimote[] wiimotes = WiiUseApiManager.getWiimotes(1, false);
                    if (wiimotes != null && wiimotes.length > 0) {
                        synchronized (wiimoteSync) {
                            wiimote = wiimotes[0];
                            wiimote.setLeds(true, false, false, false);
                            wiimote.activateIRTRacking();
                            wiimote.activateMotionSensing();
                            if (aboveScreen.getState()) {
                                wiimote.setSensorBarAboveScreen();
                            } else {
                                wiimote.setSensorBarBelowScreen();
                            }
                            wiimote.setVirtualResolution(width, height);
                            wiimote.setIrSensitivity(sensitivity);
                            wiimote.addWiiMoteEventListeners(this);
                            trayIcon.setImage(wiimoteConnectedImage);
                            System.err.println("Wiimote connected!");
                        }
                    }
                }
                try {
                    cancelSync.wait(1000);
                } catch (InterruptedException e) {
                    // Does Nothing
                }
            }
        }

        synchronized (connectSync) {
            connecting = false;
            connectSync.notifyAll();
        }
    }

    public void onButtonsEvent(WiimoteButtonsEvent event) {
        if (event.isButtonAJustPressed()) {
            robots[0].mousePress(InputEvent.BUTTON1_MASK);
        }
        if (event.isButtonAJustReleased()) {
            robots[0].mouseRelease(InputEvent.BUTTON1_MASK);
        }
        if (event.isButtonBJustPressed()) {
            robots[0].mousePress(InputEvent.BUTTON3_MASK);
        }
        if (event.isButtonBJustReleased()) {
            robots[0].mouseRelease(InputEvent.BUTTON3_MASK);
        }
        if (event.isButtonUpHeld() || event.isButtonLeftHeld()) {
            robots[0].mouseWheel(-1);
        }
        if (event.isButtonDownHeld() || event.isButtonRightHeld()) {
            robots[0].mouseWheel(1);
        }
    }

    public void onClassicControllerInsertedEvent(
            ClassicControllerInsertedEvent event) {
        // Do Nothing

    }

    public void onClassicControllerRemovedEvent(
            ClassicControllerRemovedEvent event) {
        // Do Nothing
    }

    public void onDisconnectionEvent(DisconnectionEvent event) {
        synchronized (wiimoteSync) {
            if (event.getWiimoteId() == wiimote.getId()) {
                wiimote = null;
                trayIcon.setImage(wiimoteDisconnectedImage);
                connectToWiimote();
            }
        }
    }

    public void onExpansionEvent(ExpansionEvent event) {
        // Do Nothing
    }

    public void onGuitarHeroInsertedEvent(GuitarHeroInsertedEvent event) {
        // Do Nothing
    }

    public void onGuitarHeroRemovedEvent(GuitarHeroRemovedEvent event) {
        // Do Nothing
    }

    public void onIrEvent(IREvent event) {
        if ((event.getX() != 0) && (event.getY() != 0)) {
            int mouseX = x + event.getX();
            int mouseY = y + event.getY();
            robots[0].mouseMove(mouseX, mouseY);
        }
    }

    public void onMotionSensingEvent(MotionSensingEvent event) {
        // Do Nothing
    }

    public void onNunchukInsertedEvent(NunchukInsertedEvent event) {
        // Do Nothing
    }

    public void onNunchukRemovedEvent(NunchukRemovedEvent event) {
        // Do Nothing
    }

    public void onStatusEvent(StatusEvent event) {
        // Do Nothing
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == exitItem) {
            if (wiimote != null) {
                wiimote.disconnect();
            }
            SystemTray.getSystemTray().remove(trayIcon);
            System.exit(0);
        } else if (e.getSource() == redetectScreensItem) {
            detectScreens();
            updateSelectedDevices();
        }
    }

    public void itemStateChanged(ItemEvent e) {
        if (e.getSource() == aboveScreen) {
            synchronized (wiimoteSync) {
                aboveScreen.setState(true);
                belowScreen.setState(false);
                if (wiimote != null) {
                    wiimote.setSensorBarAboveScreen();
                }
                config.setProperty("sensorBarPosition", "above");
                saveConfig();
            }
        } else if (e.getSource() == belowScreen) {
            synchronized (wiimoteSync) {
                aboveScreen.setState(false);
                belowScreen.setState(true);
                if (wiimote != null) {
                    wiimote.setSensorBarBelowScreen();
                }
                config.setProperty("sensorBarPosition", "below");
                saveConfig();
            }
        } else {
            int newSensitivity = 0;
            for (int i = 0; i < sensitivityItems.length; i++) {
                if (e.getSource() == sensitivityItems[i]) {
                    newSensitivity = i + 1;
                    break;
                }
            }
            if (newSensitivity != 0) {
                synchronized (wiimoteSync) {
                    sensitivityItems[sensitivity - 1].setState(false);
                    sensitivityItems[newSensitivity - 1].setState(true);
                    sensitivity = newSensitivity;
                    if (wiimote != null) {
                        wiimote.setIrSensitivity(sensitivity);
                    }
                    config.setProperty("sensitivity",
                            String.valueOf(sensitivity));
                    saveConfig();
                }
            } else {
                for (int i = 0; i < selectedDevices.length; i++) {
                    if (e.getSource() == selectedDevices[i]) {
                        updateSelectedDevices();
                        config.setProperty("screen" + i + "Selected",
                                String.valueOf(selectedDevices[i].getState()));
                        saveConfig();
                    }
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        new WiimoteMouse();
    }

}
