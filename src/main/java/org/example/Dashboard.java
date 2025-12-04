package org.example;

import java.io.File;
import java.util.Arrays;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

public class Dashboard extends JFrame {

  private static final String CHOOSE_FILE = "Choose File";
  private static final String CHOOSE_FILE_LOCATION = "Choose File Location";
  private static final String COMPARE_RISE_BILLER_AND_PROVIDER = "Compare Rise Biller and Provider";
  private static final String NO_FILE_SELECTED = "No file selected.";
  private static final String PROVIDER = "provider";
  private static final String PROVIDER_LIST = "provider.list";
  private static final String RECONCILIATION_FAILED = "Reconciliation Failed";
  private static final String RISE = "rise";
  private static final String UNDERSCORE = "_";
  private static final String WRONG_FILE_FORMAT = "Wrong file format";
  private static final String WRONG_PROVIDER_OR_WRONG_FILE_FORMAT = "Wrong Provider or wrong file format";
  private JPanel MainPanel;
  private JButton providerButton;
  private JButton riseButton;
  private JButton submitButton;
  private JComboBox<String> providerCombo;
  private JButton chooseLocationButton;

  private File riseFile;
  private File providerFile;

  public Dashboard() {
    generateProviderConfig();
    setContentPane(MainPanel);
    setTitle(COMPARE_RISE_BILLER_AND_PROVIDER);
    setDefaultCloseOperation(EXIT_ON_CLOSE);
    setSize(300, 300);
    setLocationRelativeTo(null);
    riseButton.addActionListener(e -> openFileChooser(true));
    providerButton.setEnabled(false);
    chooseLocationButton.setEnabled(false);
    submitButton.setEnabled(false);
    providerButton.addActionListener(e -> openFileChooser(false));
    chooseLocationButton.addActionListener(e -> chooseOutputDirectory());
    setVisible(true);
    submitButton.addActionListener(e -> reconciliation(riseFile, providerFile));
  }

  private void resetFields() {
    providerButton.setEnabled(false);
    submitButton.setEnabled(false);
    chooseLocationButton.setEnabled(false);
    providerButton.setText(CHOOSE_FILE);
    riseButton.setText(CHOOSE_FILE);
    chooseLocationButton.setText(CHOOSE_FILE_LOCATION);
    riseFile = null;
    providerFile = null;
  }

  public static void main(String[] args) {
    new Dashboard();
  }

  private void reconciliation(File riseFile, File providerFile) {
    final TransactionService transactionService = new TransactionService();
    final String reconStatusMessage = transactionService.generateResultFile(providerFile, riseFile,
      (String) providerCombo.getSelectedItem(), chooseLocationButton.getText());
    JOptionPane.showMessageDialog(this, reconStatusMessage);
    //resetFields();
  }

  private void openFileChooser(boolean isRise) {
    final JFileChooser chooser = new JFileChooser();
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      final File selected = chooser.getSelectedFile();
      if (selected == null) {
        JOptionPane.showMessageDialog(this, NO_FILE_SELECTED);
        return;
      }
      if (isRise) {
        riseFile = selected;
        riseButton.setText(selected.getName());
        providerButton.setEnabled(true);
      } else {
        providerFile = selected;
        providerButton.setText(selected.getName());
        chooseLocationButton.setEnabled(true);
      }
    }
  }

  private void generateProviderConfig() {
    final String providerList = AppConfig.getProperties(PROVIDER_LIST);
    Arrays.stream(providerList.split(",")).forEach(provider -> {
      providerCombo.addItem(provider);
    });
  }

  private void chooseOutputDirectory() {
    final JFileChooser chooser = new JFileChooser();
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    final int result = chooser.showSaveDialog(this);
    if (result == JFileChooser.APPROVE_OPTION) {
      chooseLocationButton.setText(chooser.getSelectedFile().getAbsolutePath());
      submitButton.setEnabled(true);
    }
  }

}
