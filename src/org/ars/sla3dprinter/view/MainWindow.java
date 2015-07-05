package org.ars.sla3dprinter.view;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.security.AccessControlException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.NumberFormatter;

import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;
import jssc.SerialPortList;

import org.ars.sla3dprinter.util.Consts;
import org.ars.sla3dprinter.util.Consts.UIAction;
import org.ars.sla3dprinter.util.SerialUtils;
import org.ars.sla3dprinter.util.TextUtils;
import org.ars.sla3dprinter.util.Utils;

import com.kitfox.svg.Circle;
import com.kitfox.svg.SVGCache;
import com.kitfox.svg.SVGDiagram;
import com.kitfox.svg.SVGElement;
import com.kitfox.svg.SVGElementException;
import com.kitfox.svg.SVGException;
import com.kitfox.svg.SVGRoot;
import com.kitfox.svg.SVGUniverse;
import com.kitfox.svg.animation.AnimationElement;
import com.kitfox.svg.app.beans.SVGIcon;
import com.kitfox.svg.xml.StyleAttribute;

public class MainWindow implements ActionListener, ProjectWorker.OnWorkerUpdateListener {
    private static final int START_POS_X    = 100;
    private static final int START_POS_Y    = 100;

    private Vector<String> mCommPorts = new Vector<String>();
    private Vector<String> mCommBauds = new Vector<String>();
    private Vector<GraphicsDevice> mGraphicDevices = new Vector<GraphicsDevice>();
    private File mSelectedProject;

    private JFrame mFrmSla3dPrinter;

    private JPanel mStepMotorPane;

    // UI components for Serial ports
    private JPanel mComPortPane;
    private String mSelectedPort;
    private SerialPort mSerialPort;
    private JComboBox mComboPorts;
    private JButton mBtnPortOpen;
    private JButton mBtnPortClose;
    private JButton mBtnPortRefresh;
    private JComboBox mComboBauds;

    // UI components for Platform Motor
    private JButton mBtnPlatformUp;
    private JButton mBtnPlatformDown;

    // UI components for VGA display
    private JPanel mVgaOutputPane;
    private JComboBox mComboVGA;
    private JButton mBtnVGARefresh;

    // UI components for printing config
    private JTextField mInputBaseExpo;
    private JTextField mInputLayerUm;
    private JTextField mInputLayerExpo;

    // UI components for target project
    private JPanel mProjectPane;
    private JLabel mLblProject;
    private JButton mBtnOpenProject;
    private JButton mBtnPrint;
    private JLabel mLblEstimated;

    private JButton mBtnProjectorOn;
    private JButton mBtnProjectorOff;
    private JTextField mInputScale;
    private JTextField mInputUpLiftSteps;

    // Resource part for images
    private Font mUIFont = new Font("Monaco", Font.PLAIN, 14);
    private Image mImgRefresh;
    private NumberFormatter mIntegerInputFormat = new NumberFormatter(
                    new DecimalFormat());

    SVGUniverse mSVGUniverse = SVGCache.getSVGUniverse();
    SVGDiagram mSelectedSVGDiagram;
    SVGRoot mSelectedSVGRoot;

    // FileChooser
    final JFileChooser mFileChooser;

    {
        JFileChooser fc = new JFileChooser();
        try
        {
        fc.setDialogTitle("Open project");
        fc.setFileFilter(new FileFilter() {
            final Matcher matchLevelFile = Pattern.compile(".*\\.svg[z]?").matcher("");
            public boolean accept(File file)
            {
                if (file.isDirectory()) return true;
                matchLevelFile.reset(file.getName());
                return matchLevelFile.matches();
            }

            public String getDescription() { return "SVG file (*.svg, *.svgz)"; }
        });
        } catch (AccessControlException ex) {
            //Do not create file chooser if webstart refuses permissions
        }
        mFileChooser = fc;
    }

    /**
     * Create the application.
     */
    public MainWindow() {
        prepareResources();
        loadCommPorts();
        loadGraphicDevices();
        initViews();
        disposeResources();
    }

    private void prepareResources() {
        try {
            mImgRefresh = ImageIO
                    .read(MainWindow.class
                            .getResource("/org/ars/sla3dprinter/images/ic_refresh.png"));
        } catch (IOException ex) {
            Utils.log(ex);
        }

        mIntegerInputFormat.setAllowsInvalid(false);
        mIntegerInputFormat.setValueClass(Integer.class);

        // Comm Baud bands
        mCommBauds.add(Consts.COMM_BAND_9600);
        mCommBauds.add(Consts.COMM_BAND_14400);
        mCommBauds.add(Consts.COMM_BAND_19200);
        mCommBauds.add(Consts.COMM_BAND_28800);
        mCommBauds.add(Consts.COMM_BAND_38400);
        mCommBauds.add(Consts.COMM_BAND_57600);
        mCommBauds.add(Consts.COMM_BAND_115200);
    }

    private void disposeResources() {
        mImgRefresh = null;
    }

    /**
     * Initialize the contents of the frame.
     */
    private void initViews() {
        initWindowFrame();
        initPlatformMotorPanel();
        initComPortPanel();
        initVGAOutputPanel();
        initInputProjectPanel();
        initMiscPanel();
    }

    private void initWindowFrame() {
        mFrmSla3dPrinter = new JFrame();
        mFrmSla3dPrinter.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent we) {
                if (mSerialPort != null) {
                    SerialUtils.closePort(mSerialPort);
                }
                System.exit(0);
            }
        });
        mFrmSla3dPrinter.setTitle("SLA 3D Printer");
        mFrmSla3dPrinter.setBounds(START_POS_X, START_POS_Y, 730, 390);
        mFrmSla3dPrinter.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mFrmSla3dPrinter.getContentPane().setLayout(null);
        mFrmSla3dPrinter.setResizable(true);
    }

    // Step motor pane
    private void initPlatformMotorPanel() {
        mStepMotorPane = new JPanel();
        mStepMotorPane.setForeground(Color.BLUE);
        mStepMotorPane.setToolTipText("");
        mStepMotorPane.setBorder(new TitledBorder(new EtchedBorder(
                EtchedBorder.LOWERED, null, null), "Platform motor control",
                TitledBorder.LEADING, TitledBorder.TOP, null, Color.BLUE));
        mStepMotorPane.setBounds(368, 6, 350, 200);
        mFrmSla3dPrinter.getContentPane().add(mStepMotorPane);
        mStepMotorPane.setLayout(null);

        JLabel lblLayerHeight = new JLabel("Layer Height(um):");
        lblLayerHeight.setFont(mUIFont);
        lblLayerHeight.setBounds(6, 23, 150, 30);
        mStepMotorPane.add(lblLayerHeight);

        JLabel lblLayerExposure = new JLabel("Exposure(sec):");
        lblLayerExposure.setFont(mUIFont);
        lblLayerExposure.setBounds(6, 63, 120, 30);
        mStepMotorPane.add(lblLayerExposure);

        mInputLayerUm = new JTextField("1");
        mInputLayerUm.setFont(mUIFont);
        mInputLayerUm.setColumns(10);
        mInputLayerUm.setBounds(151, 23, 80, 30);
        mStepMotorPane.add(mInputLayerUm);

        mInputLayerExpo = new JTextField("30");
        mInputLayerExpo.setFont(mUIFont);
        mInputLayerExpo.setColumns(10);
        mInputLayerExpo.setBounds(126, 63, 80, 30);
        mInputLayerExpo.getDocument().addDocumentListener(new DocumentListener() {

            @Override
            public void insertUpdate(DocumentEvent e) {
                updateEstimateTime();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateEstimateTime();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateEstimateTime();
            }
        });
        mStepMotorPane.add(mInputLayerExpo);

        mBtnPlatformUp = new JButton("Up");
        mBtnPlatformUp.setBounds(274, 23, 70, 29);
        mBtnPlatformUp.setActionCommand(UIAction.PLATFORM_UP.name());
        mBtnPlatformUp.setEnabled(false);
        mBtnPlatformUp.addActionListener(this);
        mStepMotorPane.add(mBtnPlatformUp);

        mBtnPlatformDown = new JButton("Down");
        mBtnPlatformDown.setBounds(274, 61, 70, 29);
        mBtnPlatformDown.setActionCommand(UIAction.PLATFORM_DOWN.name());
        mBtnPlatformDown.setEnabled(false);
        mBtnPlatformDown.addActionListener(this);
        mStepMotorPane.add(mBtnPlatformDown);
        
        JLabel label = new JLabel("Up lift steps:");
        label.setFont(new Font("Monaco", Font.PLAIN, 14));
        label.setBounds(6, 110, 120, 30);
        mStepMotorPane.add(label);

        mInputUpLiftSteps = new JTextField(Integer.toString(Consts.PULL_UP_STEPS));
        mInputUpLiftSteps.setFont(new Font("Monaco", Font.PLAIN, 14));
        mInputUpLiftSteps.setColumns(10);
        mInputUpLiftSteps.setBounds(126, 111, 80, 30);
        mStepMotorPane.add(mInputUpLiftSteps);
    }

    // COM port pane
    private void initComPortPanel() {
        mComPortPane = new JPanel();
        mComPortPane.setBorder(new TitledBorder(new EtchedBorder(
                EtchedBorder.LOWERED, null, null), "Printer conntection",
                TitledBorder.LEADING, TitledBorder.TOP, null, Color.BLUE));
        mComPortPane.setBounds(6, 6, 350, 130);
        mFrmSla3dPrinter.getContentPane().add(mComPortPane);
        mComPortPane.setLayout(null);

        mComboPorts = new JComboBox(mCommPorts);
        mComboPorts.setBounds(5, 20, 290, 30);
        mComPortPane.add(mComboPorts);
        mComboPorts.insertItemAt("", 0);
        mComboPorts.setSelectedIndex(0);
        mComboPorts.setActionCommand(UIAction.COM_PORT_CHANGE.name());
        mComboPorts.addActionListener(this);

        mBtnPortRefresh = new JButton("R");
        mBtnPortRefresh.setBounds(300, 20, 30, 30);
        if (mImgRefresh != null) {
            ImageIcon icon = new ImageIcon(mImgRefresh.getScaledInstance(20,
                    20, Image.SCALE_SMOOTH));
            mBtnPortRefresh.setIcon(icon);
            mBtnPortRefresh.setText("");
        }
        mBtnPortRefresh.setActionCommand(UIAction.REFRESH_PORT.name());
        mBtnPortRefresh.addActionListener(this);
        mComPortPane.add(mBtnPortRefresh);

        mBtnPortOpen = new JButton("Open");
        mBtnPortOpen.setBounds(150, 90, 85, 30);
        mBtnPortOpen.setActionCommand(UIAction.OPEN_PORT.name());
        mBtnPortOpen.addActionListener(this);
        mComPortPane.add(mBtnPortOpen);

        mBtnPortClose = new JButton("Close");
        mBtnPortClose.setBounds(240, 90, 85, 30);
        mBtnPortClose.setActionCommand(UIAction.CLOSE_PORT.name());
        mBtnPortClose.addActionListener(this);
        mComPortPane.add(mBtnPortClose);

        boolean hasPorts = mCommPorts.size() != 0;
        mBtnPortOpen.setEnabled(hasPorts);
        mComboPorts.setEnabled(hasPorts);
        mBtnPortClose.setEnabled(false);

        mComboBauds = new JComboBox(mCommBauds);
        mComboBauds.setSelectedIndex(0);
        mComboBauds.setEnabled(true);
        mComboBauds.setBounds(90, 55, 100, 30);
        mComboBauds.setActionCommand(UIAction.COM_BAUZ_CHANGE.name());
        mComPortPane.add(mComboBauds);

        JLabel lblBaud = new JLabel("Baud(Hz):");
        lblBaud.setFont(mUIFont);
        lblBaud.setBounds(10, 55, 80, 30);
        mComPortPane.add(lblBaud);
    }

    // VGA Output section
    private void initVGAOutputPanel() {
        mVgaOutputPane = new JPanel();
        mVgaOutputPane.setBorder(new TitledBorder(new EtchedBorder(
                EtchedBorder.LOWERED, null, null), "Projector connection",
                TitledBorder.LEADING, TitledBorder.TOP, null, Color.BLUE));
        mVgaOutputPane.setBounds(6, 150, 350, 96);
        mFrmSla3dPrinter.getContentPane().add(mVgaOutputPane);
        mVgaOutputPane.setLayout(null);

        mComboVGA = new JComboBox(mGraphicDevices);
        mComboVGA.setBounds(5, 20, 290, 30);
        mComboVGA.setSelectedIndex(0);
        mComboVGA.setActionCommand(UIAction.VGA_PORT_CHANGE.name());
        mVgaOutputPane.add(mComboVGA);

        mBtnVGARefresh = new JButton("R");
        mBtnVGARefresh.setBounds(300, 20, 30, 30);
        if (mImgRefresh != null) {
            ImageIcon icon = new ImageIcon(mImgRefresh.getScaledInstance(20,
                    20, Image.SCALE_SMOOTH));
            mBtnVGARefresh.setIcon(icon);
            mBtnVGARefresh.setText("");
        }
        mBtnVGARefresh.setActionCommand(UIAction.REFRESH_VGA.name());
        mBtnVGARefresh.addActionListener(this);
        mVgaOutputPane.add(mBtnVGARefresh);

        mBtnProjectorOn = new JButton("On");
        mBtnProjectorOn.setBounds(178, 62, 55, 29);
        mBtnProjectorOn.setActionCommand(UIAction.PROJECTOR_ON.name());
        mBtnProjectorOn.setEnabled(false);
        mBtnProjectorOn.addActionListener(this);
        mVgaOutputPane.add(mBtnProjectorOn);

        mBtnProjectorOff = new JButton("Off");
        mBtnProjectorOff.setBounds(232, 62, 63, 29);
        mBtnProjectorOff.setActionCommand(UIAction.PROJECTOR_OFF.name());
        mBtnProjectorOff.setEnabled(false);
        mBtnProjectorOff.addActionListener(this);
        mVgaOutputPane.add(mBtnProjectorOff);
    }

    // Init Input project panes
    private void initInputProjectPanel() {
        mProjectPane = new JPanel();
        mProjectPane.setForeground(Color.BLUE);
        mProjectPane.setBorder(new TitledBorder(new EtchedBorder(
                EtchedBorder.LOWERED, null, null), "3D Model projejct",
                TitledBorder.LEADING, TitledBorder.TOP, null, Color.BLUE));
        mProjectPane.setBounds(368, 218, 350, 136);
        mFrmSla3dPrinter.getContentPane().add(mProjectPane);
        mProjectPane.setLayout(null);

        mLblProject = new JLabel("");
        mLblProject.setHorizontalAlignment(SwingConstants.LEFT);
        mLblProject.setBounds(6, 22, 335, 30);
        mLblProject.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        mProjectPane.add(mLblProject);

        mBtnOpenProject = new JButton("Open Project");
        mBtnOpenProject.setBounds(95, 98, 120, 30);
        mBtnOpenProject.setActionCommand(UIAction.OPEN_PROJECT.name());
        mBtnOpenProject.addActionListener(this);
        mProjectPane.add(mBtnOpenProject);

        mBtnPrint = new JButton("Print");
        mBtnPrint.setBounds(221, 98, 120, 30);
        mProjectPane.add(mBtnPrint);
        mBtnPrint.setActionCommand(UIAction.START_PRINT.name());

        JLabel lblEstimate = new JLabel("Progress:");
        lblEstimate.setBounds(6, 64, 80, 30);
        mProjectPane.add(lblEstimate);
        lblEstimate.setFont(mUIFont);

        mLblEstimated = new JLabel("N/A");
        mLblEstimated.setBounds(85, 64, 256, 30);
        mProjectPane.add(mLblEstimated);
        mLblEstimated.setFont(mUIFont);
        mBtnPrint.addActionListener(this);

    }

    private void initMiscPanel() {
        JPanel mMiscPane = new JPanel();
        mMiscPane.setLayout(null);
        mMiscPane.setToolTipText("");
        mMiscPane.setForeground(Color.BLUE);
        mMiscPane.setBorder(new TitledBorder(new EtchedBorder(
                EtchedBorder.LOWERED, null, null), "Misc",
                TitledBorder.LEADING, TitledBorder.TOP, null, Color.BLUE));
        mMiscPane.setBounds(6, 258, 350, 96);

        JLabel lblBaseExposure = new JLabel("Base Exposure (sec):");
        lblBaseExposure.setFont(mUIFont);
        lblBaseExposure.setBounds(6, 59, 160, 30);
        mMiscPane.add(lblBaseExposure);

        mFrmSla3dPrinter.getContentPane().add(mMiscPane);

        mInputBaseExpo = new JTextField("30");
        mInputBaseExpo.setFont(mUIFont);
        mInputBaseExpo.setBounds(171, 59, 80, 30);
        mInputBaseExpo.getDocument().addDocumentListener(new DocumentListener() {

            @Override
            public void insertUpdate(DocumentEvent e) {
                updateEstimateTime();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateEstimateTime();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateEstimateTime();
            }
        });
        mMiscPane.add(mInputBaseExpo);
        mInputBaseExpo.setColumns(10);

        JLabel label = new JLabel("ImageScale");
        label.setFont(new Font("Monaco", Font.PLAIN, 14));
        label.setBounds(6, 21, 160, 30);
        mMiscPane.add(label);

        mInputScale = new JTextField("10");
        mInputScale.setFont(new Font("Monaco", Font.PLAIN, 14));
        mInputScale.setColumns(10);
        mInputScale.setBounds(171, 22, 80, 30);
        mMiscPane.add(mInputScale);
    }

    private void loadCommPorts() {
        mCommPorts.clear();
        String[] ports = SerialPortList.getPortNames();
        if (ports != null) {
            for (String port : ports) {
                mCommPorts.add(port);
            }
        }
    }

    private void loadGraphicDevices() {
        mGraphicDevices.clear();
        GraphicsDevice[] devices = GraphicsEnvironment
                .getLocalGraphicsEnvironment().getScreenDevices();
        if (devices != null) {
            for (GraphicsDevice device : devices) {
                mGraphicDevices.add(device);
            }
        }
    }

    public void show() {
        mFrmSla3dPrinter.setVisible(true);
        mFrmSla3dPrinter.requestFocus();
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        final String action = ae.getActionCommand();
        final Object source = ae.getSource();
        if (Utils.isTextEmpty(action)) return;
        if (source == null) return;

        CommandBase cmd;

        switch (UIAction.valueOf(action)) {
            case COM_PORT_CHANGE:
                JComboBox comboBox = (JComboBox) source;
                String comPorts = comboBox.getSelectedItem().toString();
                mSelectedPort = Utils.isTextEmpty(comPorts) ? null : comPorts;
                break;
            case COM_BAUZ_CHANGE:
                break;
            case VGA_PORT_CHANGE:
                break;
            case OPEN_PORT:
                if (Utils.isTextEmpty(mSelectedPort)) {
                    Utils.log("No selected comm port");
                    return;
                }
                mSerialPort = SerialUtils.openPort(mSelectedPort);
                if (SerialUtils.isPortAvailable(mSerialPort)) {
                    mBtnPortOpen.setEnabled(false);
                    mComboPorts.setEnabled(false);
                    mBtnPortClose.setEnabled(true);
                    mBtnPlatformUp.setEnabled(true);
                    mBtnPlatformDown.setEnabled(true);
                    mBtnProjectorOn.setEnabled(true);
                    mBtnProjectorOff.setEnabled(true);
                }
                break;
            case CLOSE_PORT:
                if (SerialUtils.closePort(mSerialPort)) {
                    mBtnPortOpen.setEnabled(true);
                    mComboPorts.setEnabled(true);
                    mBtnPortClose.setEnabled(false);
                    mBtnPlatformUp.setEnabled(false);
                    mBtnPlatformDown.setEnabled(false);
                    mBtnProjectorOn.setEnabled(false);
                    mBtnProjectorOff.setEnabled(false);
                    mSerialPort = null;
                }
                break;
            case REFRESH_PORT:
                loadCommPorts();
                break;
            case REFRESH_VGA:
                loadGraphicDevices();
                break;
            case OPEN_PROJECT:
                openFileChooser();
                break;
            case START_PRINT:
                promptFakeFrame();
                break;
            case PLATFORM_UP:
                if (mSerialPort == null || !SerialUtils.isPortAvailable(mSerialPort)) {
                  return;
                }
                try {
                    int steps = Integer.parseInt(mInputLayerUm.getText());
                    cmd = PrinterScriptFactory.generatePlatformMovement(PlatformMovement.DIRECTION_UP
                                    , steps);
                    SerialUtils.writeToPort(mSerialPort, cmd.getCommand());
                } catch (NumberFormatException nfe) {
                    System.err.println("Invalid number for layer height and stpes");
                }
                break;
            case PLATFORM_DOWN:
                if (mSerialPort == null || !SerialUtils.isPortAvailable(mSerialPort)) {
                    return;
                }
                try {
                    int steps = Integer.parseInt(mInputLayerUm.getText());
                    cmd = PrinterScriptFactory.generatePlatformMovement(PlatformMovement.DIRECTION_DOWN
                                    , steps);
                    SerialUtils.writeToPort(mSerialPort, cmd.getCommand());
                } catch (NumberFormatException nfe) {
                    System.err.println("Invalid number for layer height and stpes");
                }
                break;
            case PROJECTOR_ON:
                if (mSerialPort == null || !SerialUtils.isPortAvailable(mSerialPort)) {
                    return;
                }
                cmd = PrinterScriptFactory.generateProjectorCommand(true);
                SerialUtils.writeToPort(mSerialPort, cmd.getCommand());
                break;
            case PROJECTOR_OFF:
                if (mSerialPort == null || !SerialUtils.isPortAvailable(mSerialPort)) {
                    return;
                }
                cmd = PrinterScriptFactory.generateProjectorCommand(false);
                SerialUtils.writeToPort(mSerialPort, cmd.getCommand());
                break;
            default:
                Utils.log("Unknown action: " + action);
                break;
        }
    }

    private void openFileChooser() {
        if (mFileChooser == null) return;
        int retValue = mFileChooser.showOpenDialog(null);
        if (retValue == JFileChooser.APPROVE_OPTION) {
            mSelectedProject = mFileChooser.getSelectedFile();
            if (mSelectedProject == null) {
                return;
            }

            String path = null;
            try {
                path = mSelectedProject.getCanonicalPath();
            } catch (IOException ex) {
                Utils.log(ex);
                path = mSelectedProject.getName();
            }
            mLblProject.setText(mSelectedProject.getName());
            mLblProject.setToolTipText(path);

            updateEstimateTime();
        } else {
            System.out.println("No Selection ");
        }
    }

    private String projectEstimateSuffix;

    private void updateEstimateTime() {
        if (mSelectedProject == null) {
            mLblEstimated.setText("N/A");
            return;
        }

        SVGUniverse universe = SVGCache.getSVGUniverse();
        SVGDiagram diagram = universe.getDiagram(mSelectedProject.toURI());
        if (diagram == null) {
            mLblEstimated.setText("N/A");
            return;
        }
        if (diagram != null) {
            SVGRoot root = diagram.getRoot();
            int timeInSeconds = 0;
            int layerCount = 0;
            try {
                timeInSeconds += 60;    // Open printer wait
                timeInSeconds += 60;    // Close printer wait

                // base layer print time
                timeInSeconds += Integer.parseInt(mInputBaseExpo.getText());

                // each layer print time
                layerCount = root.getChildren(new ArrayList()).size();
                timeInSeconds += layerCount * Integer.parseInt(mInputLayerExpo.getText());

                // Total + 10% seconds for estimate
                timeInSeconds *= 1.1;
            } catch (NumberFormatException e) {
                timeInSeconds = 0;
            }

            long days = TimeUnit.SECONDS.toDays(timeInSeconds);
            long hours = TimeUnit.SECONDS.toHours(timeInSeconds) % 24;
            long minutes = TimeUnit.SECONDS.toMinutes(timeInSeconds) % 60;
            long seconds = timeInSeconds - days * 86400 - hours * 3600 - minutes * 60;
            projectEstimateSuffix = String.format(Consts.PATTERN_ESTIMATE_PROCESS_SUFFIX, layerCount, days, hours, minutes, seconds);
            mLblEstimated.setText("0" + projectEstimateSuffix);
        }
    }

    private void promptFakeFrame() {
        if (mSelectedProject == null) {
            showErrorDialog("Choose a SVG by \'Open Project\'");
            return;
        }
        if (!Consts.sFLAG_DEBUG_MODE && mSelectedPort == null) {
            showErrorDialog("Connect to printer before start printing");
            return;
        }

        // 1. Collect printing related data
        PrintingInfo info = new PrintingInfo();
        info.baseExpoTimeInSeconds = Integer.parseInt(mInputBaseExpo.getText());
        info.layerExpoTimeInSeconds = Integer.parseInt(mInputLayerExpo.getText());
        info.layerHeightInUms = Integer.parseInt(mInputLayerUm.getText());
        info.upLiftSteps = Integer.parseInt(mInputUpLiftSteps.getText());

        // 2. Prepare GraphicsDevice
        Object selected = mComboVGA.getSelectedItem();
        final GraphicsDevice device;
        final GraphicsConfiguration config;
        final JFrame f;
        if (selected instanceof GraphicsDevice) {
            device = (GraphicsDevice) selected;
            config = device.getDefaultConfiguration();
            f = new JFrame(config);
        } else {
            device = null;
            config = null;
            f = null;
        }

        if (device == null || config == null || f == null) {
            showErrorDialog("Printing projector not ready");
            return;
        }

        // 3. Prepare Worker
        final ProjectWorker worker;

        // Load target SVG file for the 3d model
        SVGUniverse universe = SVGCache.getSVGUniverse();
        SVGDiagram diagram = universe.getDiagram(mSelectedProject.toURI());
        if (diagram == null) {
            showErrorDialog("Cannot load project frile");
            return;
        }

        SVGRoot root = diagram.getRoot();
        if (root == null) {
            showErrorDialog("Project content has problem");
            return;
        }

        StyleAttribute width = root.getPresAbsolute("width");
        StyleAttribute height = root.getPresAbsolute("height");
        int targetWidth = device.getDisplayMode().getWidth();
        int targetHeight = device.getDisplayMode().getHeight();

        // Force not scale
        float scale = Float.parseFloat(mInputScale.getText());
        float scaledWidth = scale * width.getFloatValue();
        float scaledHeight = scale * height.getFloatValue();

        if (scaledWidth > targetWidth || scaledHeight > targetHeight) {
            JOptionPane.showMessageDialog(f,
                    "Scale to large! " + scale,
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        float imageX = (targetWidth - scaledWidth ) / 2;
        float imageY = (targetHeight - scaledHeight ) / 2;

        final DynamicIconPanel myPanel =
                        new DynamicIconPanel(targetWidth, targetHeight,
                                        Math.round(imageX), Math.round(imageY), scale);

        // Load target SVG file for the 3d model
        worker = new ProjectWorker(myPanel, root, mSerialPort, info, this);

        myPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escapeFromPrinting");
        myPanel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escapeFromPrinting");
        myPanel.getInputMap(JComponent.WHEN_FOCUSED)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escapeFromPrinting");
        myPanel.getActionMap().put("escapeFromPrinting", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                f.dispose();
                if (worker != null) {
                    worker.cancel(true);
                }
            }
        });

        f.getContentPane().add(myPanel);
        f.setUndecorated(true);
        f.setExtendedState(JFrame.MAXIMIZED_BOTH);
        f.pack();
        f.setVisible(true);

        if (Utils.isMac()) {
            Utils.enableFullScreenMode(f);
        }
        if (!Consts.sFLAG_DEBUG_MODE) {
            device.setFullScreenWindow(f);
        }

        // Kick-off worker
        worker.execute();
    }

    private void showErrorDialog(String message) {
        JOptionPane.showMessageDialog(mFrmSla3dPrinter, message, "Error",
                JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void onWorkerStarted(int totalLayer) {
        mLblEstimated.setText(0 + projectEstimateSuffix);
    }

    @Override
    public void onLayerExposed(int layer) {
        mLblEstimated.setText(layer + projectEstimateSuffix);
    }

    @Override
    public void onWorkerFinished(int resultCode) {
    }
}

class ProjectWorker extends SwingWorker<Void, SVGElement>
    implements SerialPortEventListener {

    public interface OnWorkerUpdateListener {
        void onWorkerStarted(int totalLayers);
        void onLayerExposed(int layer);
        void onWorkerFinished(int resultCode);
    }

    private OnWorkerUpdateListener listener;
    private DynamicIconPanel panel;
    private SVGRoot root;
    private SerialPort serialPort;
    private PrintingInfo printingInfo;

    // Dummy item for black screen
    private final SVGElement circle = new Circle();

    private final Object lock = new Object();

    private int layerIndex = 0;

    public ProjectWorker(DynamicIconPanel _panel, SVGRoot _root, SerialPort _serial, PrintingInfo info, OnWorkerUpdateListener _listener) {
        ensurePrintingInfoValid(info);

        panel = _panel;
        root = _root;
        serialPort = _serial;
        printingInfo = info;
        listener = _listener;

        try {
            circle.addAttribute("id", AnimationElement.AT_XML, "blank-page");
            circle.addAttribute("cx", AnimationElement.AT_XML, "10");
            circle.addAttribute("cy", AnimationElement.AT_XML, "10");
            circle.addAttribute("r", AnimationElement.AT_XML, "10");
            circle.addAttribute("fill", AnimationElement.AT_XML, Consts.sFLAG_DEBUG_MODE ? "white" : "black");
        } catch (SVGElementException e) {
            e.printStackTrace();
            e.printStackTrace();
        }
    }

    private void ensurePrintingInfoValid(PrintingInfo info) {
        if (info == null) {
            throw new IllegalArgumentException("PrintingInfo must not be null");
        }
        if (!info.valid()) {
            throw new IllegalArgumentException("There are something wrong in printing setup. " + info.toString());
        }
    }

    private void waitForNotify(int pauseTime) throws InterruptedException {
        if (!Consts.sFLAG_DEBUG_MODE) {
            synchronized(lock) {
                lock.wait();
            }
        } else {
            if (pauseTime < 0) {
                Thread.currentThread().sleep(300);
            } else {
                Thread.currentThread().sleep(pauseTime * 1000);
            }
        }
    }

    ArrayList<CommandBase> mDebugCommandList = new ArrayList<CommandBase>();
    private void addCommandToDebug(CommandBase cmd) {
        if (cmd != null) {
            mDebugCommandList.add(cmd);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Void doInBackground() throws Exception {
        if (!Consts.sFLAG_DEBUG_MODE) {
            if (serialPort == null || !serialPort.isOpened()) {
                System.out.println("No opened serialPort: " + serialPort);
                return null;
            }
            serialPort.addEventListener(this);
        }

        int i;
        int total = root.getNumChildren();
        List<SVGElement> children = new ArrayList<SVGElement>();
        children = root.getChildren(children);
        SVGElement element = null;

        List<CommandBase> commandsList;
        CommandBase cmd;

        int upSteps = printingInfo.upLiftSteps;

        if (!Consts.sFLAG_DEBUG_MODE) {
            // Push up for a little bit to avoid hide interrupt
            cmd = PrinterScriptFactory.generatePlatformMovement(PlatformMovement.DIRECTION_UP, 4000);
            processCommand(cmd);
            cmd = PrinterScriptFactory.generatePlatformMovement(PlatformMovement.DIRECTION_UP, 4000);
            processCommand(cmd);

            // Push down for a little bit to avoid hide interrupt
            cmd = PrinterScriptFactory.generatePlatformMovement(PlatformMovement.DIRECTION_DOWN, 4000);
            processCommand(cmd);

            // Return home
            commandsList = PrinterScriptFactory.generateCommandForResetPlatform();
            for (i = 0; i < commandsList.size(); i++) {
                cmd = commandsList.get(i);
                processCommand(cmd);
            }
            commandsList.clear();

            // Turn on projector
            panel.setBackground(Color.BLACK);
            panel.repaint();

            cmd = PrinterScriptFactory.generateProjectorCommand(true);
            processCommand(cmd);

            cmd = PrinterScriptFactory.generatePauseCommand(30);
            processCommand(cmd);

            cmd = PrinterScriptFactory.generateProjectorCommand(true);
            processCommand(cmd);

            cmd = PrinterScriptFactory.generatePauseCommand(30);
            processCommand(cmd);

            // Get ready to exposure for base layer
            commandsList = PrinterScriptFactory.generateCommandForExpoBase();
            for (i = 0; i < commandsList.size(); i++) {
                cmd = commandsList.get(i);
                processCommand(cmd);
            }
            commandsList.clear();

            // uplift for base
            cmd = PrinterScriptFactory.generatePlatformMovement(PlatformMovement.DIRECTION_UP, 40);
            processCommand(cmd);
        }

        // exposure base layer
        element = children.get(0);
        publish(element);

        cmd = PrinterScriptFactory.generatePauseCommand(printingInfo.baseExpoTimeInSeconds);
        processCommand(cmd, printingInfo.baseExpoTimeInSeconds);

        publish(circle);
        cmd = PrinterScriptFactory.generatePauseCommand(2);
        processCommand(cmd);

        // loop layers
        int layerSteps = printingInfo.getStepsPerLayer();

        int downSteps = upSteps - layerSteps;
        for (i = 1; i < total; i++) {
            layerIndex = i;

            // Go up 3 layer height
            cmd = PrinterScriptFactory.generatePlatformMovement(PlatformMovement.DIRECTION_UP, upSteps);
            processCommand(cmd);

            // Go down 2 layer height
            cmd = PrinterScriptFactory.generatePlatformMovement(PlatformMovement.DIRECTION_DOWN, downSteps);
            processCommand(cmd);

            // Exposure layer
            element = children.get(i);
            publish(element);
            cmd = PrinterScriptFactory.generatePauseCommand(printingInfo.layerExpoTimeInSeconds);
            processCommand(cmd, printingInfo.layerExpoTimeInSeconds);

            publish(circle);
            cmd = PrinterScriptFactory.generatePauseCommand(2);
            processCommand(cmd);
        }

        if (!Consts.sFLAG_DEBUG_MODE) {
            // Turn off projector
            cmd = PrinterScriptFactory.generateProjectorCommand(false);
            processCommand(cmd);

            cmd = PrinterScriptFactory.generatePauseCommand(1);
            processCommand(cmd);

            cmd = PrinterScriptFactory.generateProjectorCommand(false);
            processCommand(cmd);

            cmd = PrinterScriptFactory.generatePauseCommand(30);
            processCommand(cmd);

            // Return to home again
            commandsList = PrinterScriptFactory.generateCommandForResetPlatform();
            for (i = 0; i < commandsList.size(); i++) {
                cmd = commandsList.get(i);
                processCommand(cmd);
            }
            commandsList.clear();

            // Push down for a little bit to avoid hide interrupt
            cmd = PrinterScriptFactory.generatePlatformMovement(PlatformMovement.DIRECTION_DOWN, 4000);
            processCommand(cmd);
        }

        return null;
    }

    private void processCommand(CommandBase cmd) throws InterruptedException {
        processCommand(cmd, -1);
    }

    private void processCommand(CommandBase cmd, int pauseTime) throws InterruptedException {
        addCommandToDebug(cmd);
        SerialUtils.writeToPort(serialPort, cmd.getCommand());
        waitForNotify(pauseTime);
    }

    @Override
    protected void process(List<SVGElement> trunks) {
        if (isCancelled()) {
            return;
        }
        if (trunks.size() != 1) {
            return;
        }
        SVGElement element = trunks.get(0);
        panel.replaceLayerSVG(element);
        listener.onLayerExposed(layerIndex);
    }

    @Override
    protected void done() {
        panel = null;
        root = null;
        try {
            if (serialPort != null) {
                serialPort.removeEventListener();
            }
        } catch (SerialPortException e) {
            e.printStackTrace();
        }
        serialPort = null;
        if (mDebugCommandList.size() > 0) {
            for (CommandBase cmd : mDebugCommandList) {
                System.out.println(cmd.getCommand());
            }
        }
        listener.onWorkerFinished(0);
    }

    public void serialEvent(SerialPortEvent event) {
        if (event.isRXCHAR()) { //If data is available
            if (event.getEventValue() > 0) {
                try {
                    if (getState() == StateValue.STARTED) {
                        String feedback = serialPort.readString();
                        if (feedback.endsWith(">")) {
                            synchronized (lock) {
                                lock.notify();
                            }
                        }
                    }
                } catch (SerialPortException ex) {
                    ex.printStackTrace();
                }
            }
        } else if (event.isCTS()) { //If CTS line has changed state
            System.out.println((event.getEventValue() == 1) ? "CTS - ON" : "CTS - OFF");
        } else if (event.isDSR()) { //If DSR line has changed state
            System.out.println((event.getEventValue() == 1) ? "DSR - ON" : "DSR - OFF");
        } else {
            System.out.println(event.toString());
        }
    }
}

class DynamicIconPanel extends JPanel {
    public static final long serialVersionUID = 0;

    final SVGIcon icon;
    URI uri;

    SVGUniverse universe = SVGCache.getSVGUniverse();
    SVGDiagram diagram;
    SVGElement layerElement;

    int imageX;
    int imageY;

    public DynamicIconPanel(int width, int height, int _imageX, int _imageY, float scale)
    {
        ensureDimensionValid(width, height);
        ensureScaleValid(scale);

        imageX = _imageX;
        imageY = _imageY;

        String attribScale = String.format("scale(%f)", scale);
        StringReader reader = new StringReader(makeDynamicSVG(width, height, attribScale));
        uri = universe.loadSVG(reader, "myImage");
        diagram = universe.getDiagram(uri);
        icon = new SVGIcon();
        icon.setAntiAlias(true);
        icon.setSvgURI(uri);

        setBackground(Color.BLACK);
        setPreferredSize(new Dimension(width, height));
    }

    private void ensureDimensionValid(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("width and height must greater than 0");
        }
    }

    private void ensureScaleValid(float scale) {
        if (scale <= 0) {
            throw new IllegalArgumentException("scale must be greater than 0");
        }
    }

    public void paintComponent(Graphics g)
    {
        super.paintComponent(g);
        final int width = getWidth();
        final int height = getHeight();

        g.setColor(getBackground());
        g.fillRect(0, 0, width, height);

        icon.paintIcon(this, g, imageX, imageY);
    }

    private String makeDynamicSVG(int width, int height, String scale)
    {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        pw.println("<svg width=\"" + width +"\" height=\"" + height + "\" transform=\"" + scale + "\"></svg>");

        pw.close();
        return sw.toString();
    }

    public void replaceLayerSVG(SVGElement element) {
        if (layerElement != null) {
            resetLayers();
        }

        if (element == null) {
            return;
        }

        layerElement = element;

        try {
            SVGRoot root = diagram.getRoot();
            root.loaderAddChild(null, layerElement);

            // Update animation state or group and it's decendants so that it
            // reflects new animation values.
            // We could also call diagram.update(0.0) or
            // SVGCache.getSVGUniverse().update(). Note that calling
            // circle.update(0.0) won't display anything since even though it
            // will update the circle's state,
            // it won't update the parent group's state.
            universe.updateTime();
            repaint();
        } catch (SVGException e) {
            e.printStackTrace();
        }
    }

    public void resetLayers() {
        try {
            SVGRoot root = diagram.getRoot();
            root.removeChild(layerElement);

            universe.updateTime();
            repaint();
        } catch (SVGException e) {
            e.printStackTrace();
        }
    }
}

class PrintingInfo {
    int upLiftSteps;
    int baseExpoTimeInSeconds = -1;
    int layerExpoTimeInSeconds = -1;
    int layerHeightInUms = -1;

    public boolean valid() {
        return baseExpoTimeInSeconds > 0 && layerExpoTimeInSeconds > 0
            && layerHeightInUms > 0;
    }

    public int getStepsPerLayer() {
        return layerHeightInUms;  // Future need to load device info for steps per um
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("BaseExpoTime: ").append(baseExpoTimeInSeconds).append(", ");
        sb.append("LayerExpoTime: ").append(layerExpoTimeInSeconds).append(", ");
        sb.append("LayerHeight(um): ").append(layerHeightInUms).append(", ");
        sb.append("Up Lift Steps: ").append(upLiftSteps);
        return sb.toString();
    }
}

abstract class CommandBase {
    public abstract String getCommand();
    protected abstract String getCommandCode();

    @Override
    public String toString() {
        return getCommand();
    }
}

class PlatformMovement extends CommandBase {
    public static final int DIRECTION_UP    = 0;
    public static final int DIRECTION_DOWN  = 1;

    private static final String CODE_DIR_UP     = "02";
    private static final String CODE_DIR_DOWN   = "03";

    private static final String PLATFORM_MOVEMENT_PATTERN = "G%s Z%d;";
    final int direction;
    final int steps;

    public PlatformMovement(int _dir, int _steps) {
        direction = _dir;
        steps = _steps;
    }

    @Override
    public String getCommand() {
        return String.format(PLATFORM_MOVEMENT_PATTERN, getCommandCode(), steps);
    }

    @Override
    protected String getCommandCode() {
        switch (direction) {
            case DIRECTION_DOWN:
                return CODE_DIR_DOWN;
            case DIRECTION_UP:
                return CODE_DIR_UP;
            default:
                throw new IllegalArgumentException("Invalid direction code: " + direction);
        }
    }
}

class PauseCommand extends CommandBase {
    private static final String PAUSE_COMMAND_PATTERN = "G%s P%d;";

    private static final String CODE_PAUSE = "04";
    final int pauseTimeInSeconds;

    public PauseCommand(int _seconds) {
        pauseTimeInSeconds = _seconds;
    }

    @Override
    public String getCommand() {
        return String.format(PAUSE_COMMAND_PATTERN, getCommandCode(), pauseTimeInSeconds);
    }

    @Override
    protected String getCommandCode() {
        return CODE_PAUSE;
    }
}

class ProjectorCommand extends CommandBase {
    private static final String PROJECTOR_PATTERN    = "G%s;";

    private static final String CODE_ON     = "50";
    private static final String CODE_OFF    = "51";

    private final boolean makeOn;

    public ProjectorCommand(boolean _toOn) {
        makeOn = _toOn;
    }

    @Override
    public String getCommand() {
        return String.format(PROJECTOR_PATTERN, getCommandCode());
    }

    @Override
    protected String getCommandCode() {
        return makeOn ? CODE_ON : CODE_OFF;
    }
}

/**
 * G02 Z(steps); => linear move up <br/>
 * G03 Z(steps); => linear move down <br/>
 * G04 P(seconds); => delay <br/>
 * G50; => Send power on command <br/>
 * G51; => Send power off command <br/>
 * M02 Z(steps); => rotate tank up <br/>
 * M03 Z(steps); => rotate tank down <br/>
 * M100; => this help message <br/>
 * @author jimytc
 */
class PrinterScriptFactory {
    public static final int PAUSE_TIME_DEFAULT = 1; // 1 second
    public static final int RESET_COMMAND_SIZE = 30;
//.0025  10 mm / 4000 steps
// 195 mm
// 19.5
    public static List<CommandBase> generateCommandForResetPlatform() {
        CommandBase cmd;
        ArrayList<CommandBase> commandsList = new ArrayList<CommandBase>();
        for (int i = 0; i < RESET_COMMAND_SIZE; i++) {
            cmd = generatePlatformMovement(PlatformMovement.DIRECTION_UP, 4000);
            if (cmd != null) {
                commandsList.add(cmd);
            }
        }
        if (commandsList.size() != RESET_COMMAND_SIZE) {
            System.err.println("Unexpected command with null when preparing reset command list");
            throw new IllegalStateException("Unexpected command with null when preparing reset command list");
        }
        return commandsList;
    }

    public static List<CommandBase> generateCommandForExpoBase() {
        CommandBase cmd;
        ArrayList<CommandBase> commandsList = new ArrayList<CommandBase>();
        for (int i = 0; i < RESET_COMMAND_SIZE; i++) {
            cmd = generatePlatformMovement(PlatformMovement.DIRECTION_DOWN, 4000);
            if (cmd != null) {
                commandsList.add(cmd);
            }
        }
        if (commandsList.size() != RESET_COMMAND_SIZE) {
            System.err.println("Unexpected command with null when preparing exposure base command list");
            throw new IllegalStateException("Unexpected command with null when preparing exposure base command list");
        }
        return commandsList;
    }

    public static CommandBase generatePlatformMovement(int dir, int steps) {
        CommandBase cmd = new PlatformMovement(dir, steps);
        if (validateCommand(cmd)) {
            return cmd;
        } else {
            return null;
        }
    }

    public static CommandBase generatePauseCommand(int seconds) {
        CommandBase cmd = new PauseCommand(seconds);
        if (validateCommand(cmd)) {
            return cmd;
        } else {
            return null;
        }
    }

    public static CommandBase generateProjectorCommand(boolean toOn) {
        CommandBase cmd = new ProjectorCommand(toOn);
        if (validateCommand(cmd)) {
            return cmd;
        } else {
            return null;
        }
    }

    public static boolean validateCommand(CommandBase cmd) {
        if (cmd == null) return false;
        String strCmd = cmd.getCommand();
        if (TextUtils.isEmpty(strCmd)) return false;
        if (!strCmd.endsWith(";")) {
            System.err.println("Command not ends with \";\": " + cmd.getClass().getSimpleName());
            return false;
        }
        return true;
    }
}