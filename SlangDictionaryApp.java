import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

public class SlangDictionaryApp extends JFrame {
private Map<String, String> slangDictionary;
private List<String> searchHistory;
private JTextArea outputTextArea;
private JTextArea historyTextArea;
private JTextField searchTextField;

public SlangDictionaryApp() {
    slangDictionary = new HashMap<>();
    searchHistory = new ArrayList<>();

    loadSlangDictionaryFromFile("slang.txt");

    setTitle("Slang Dictionary");
    setSize(500, 400);
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    JPanel panel = new JPanel();
    getContentPane().add(panel);
    placeComponents(panel);

    setVisible(true);
}

private void placeComponents(JPanel panel) {
    panel.setLayout(null);

    JLabel searchLabel = new JLabel("Search:");
    searchLabel.setBounds(10, 10, 80, 25);
    panel.add(searchLabel);

    searchTextField = new JTextField(20);
    searchTextField.setBounds(100, 10, 160, 25);
    panel.add(searchTextField);

    JButton searchButton = new JButton("Search");
    searchButton.setBounds(280, 10, 80, 25);
    panel.add(searchButton);

    outputTextArea = new JTextArea();
    outputTextArea.setEditable(false);
    JScrollPane outputScrollPane = new JScrollPane(outputTextArea);
    outputScrollPane.setBounds(10, 40, 480, 200);
    panel.add(outputScrollPane);

    historyTextArea = new JTextArea();
    historyTextArea.setEditable(false);
    JScrollPane historyScrollPane = new JScrollPane(historyTextArea);
    historyScrollPane.setBounds(10, 250, 480, 100);
    panel.add(historyScrollPane);

    JButton addButton = new JButton("Add");
    addButton.setBounds(370, 10, 80, 25);
    panel.add(addButton);

    JButton editButton = new JButton("Edit");
    editButton.setBounds(460, 10, 80, 25);
    panel.add(editButton);

    JButton deleteButton = new JButton("Delete");
    deleteButton.setBounds(550, 10, 80, 25);
    panel.add(deleteButton);

    JButton resetButton = new JButton("Reset");
    resetButton.setBounds(640, 10, 80, 25);
    panel.add(resetButton);

    JButton randomButton = new JButton("Random");
    randomButton.setBounds(730, 10, 80, 25);
    panel.add(randomButton);

    JButton quizButton = new JButton("Quiz");
    quizButton.setBounds(820, 10, 80, 25);
    panel.add(quizButton);

    searchButton.addActionListener(e -> searchButtonClicked());
    addButton.addActionListener(e -> addButtonClicked());
    editButton.addActionListener(e -> editButtonClicked());
}
private void searchButtonClicked() {
    String keyword = searchTextField.getText().trim();
    searchSlangWord(keyword);
    searchByDefinition(keyword);
    addToSearchHistory(keyword);
    updateHistoryTextArea();
}

private void searchSlangWord(String keyword) {
    StringBuilder resultBuilder = new StringBuilder();
    for (Map.Entry<String, String> entry : slangDictionary.entrySet()) {
        String slangWord = entry.getKey();
        String definition = entry.getValue();
        if (slangWord.contains(keyword)) {
            resultBuilder.append(slangWord).append(": ").append(definition).append("\n");
        }
    }
    String result = resultBuilder.toString().trim();
    if (result.isEmpty()) {
        result = "Slang words not found.";
    }
    outputTextArea.setText(result);
}

private void searchByDefinition(String keyword) {
    StringBuilder resultBuilder = new StringBuilder();
    for (Map.Entry<String, String> entry : slangDictionary.entrySet()) {
        String slangWord = entry.getKey();
        String definition = entry.getValue();
        if (definition.contains(keyword)) {
            resultBuilder.append(slangWord).append(": ").append(definition).append("\n");
        }
    }
    String result = resultBuilder.toString().trim();
    if (!result.isEmpty()) {
        result = "\nDefinitions containing the keyword:\n" + result;
        outputTextArea.append(result);
    }
}

private void addToSearchHistory(String keyword) {
    searchHistory.add(keyword);
}

private void updateHistoryTextArea() {
    StringBuilder historyBuilder = new StringBuilder();
    for (String keyword : searchHistory) {
        historyBuilder.append(keyword).append("\n");
    }
    historyTextArea.setText(historyBuilder.toString());
}

private void loadSlangDictionaryFromFile(String filename) {
    try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split("`");
            if (parts.length == 2) {
                String slangWord = parts[0].trim();
                String definition = parts[1].trim();
                slangDictionary.put(slangWord, definition);
            }
        }
    } catch (IOException e) {
        e.printStackTrace();
    }
}

private void saveSlangDictionaryToFile(String filename) {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))){
        for (Map.Entry<String, String> entry : slangDictionary.entrySet()) {
            String slangWord = entry.getKey();
            String definition = entry.getValue();
            writer.write(slangWord + "`" + definition);
            writer.newLine();
        }
    } catch (IOException e) {
        e.printStackTrace();
    }
}

private void addButtonClicked() {
    String slangWord = JOptionPane.showInputDialog(this, "Enter slang word:");
    if (slangWord != null) {
        slangWord = slangWord.trim();
        if (slangWord.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Slang word cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (slangDictionary.containsKey(slangWord)) {
            int choice = JOptionPane.showConfirmDialog(this, "Slang word already exists. Do you want to overwrite?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                String definition = JOptionPane.showInputDialog(this, "Enter definition:");
                if (definition != null) {
                    definition = definition.trim();
                    if (!definition.isEmpty()) {
                        slangDictionary.put(slangWord, definition);
                        saveSlangDictionaryToFile("slang.txt");
                        JOptionPane.showMessageDialog(this, "Slang word overwritten successfully.");
                    } else {
                        JOptionPane.showMessageDialog(this, "Definition cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        } else {
            String definition = JOptionPane.showInputDialog(this, "Enter definition:");
            if (definition != null) {
                definition = definition.trim();
                if (!definition.isEmpty()) {
                    slangDictionary.put(slangWord, definition);
                    saveSlangDictionaryToFile("slang.txt");
                    JOptionPane.showMessageDialog(this, "Slang word added successfully.");
                } else {
                    JOptionPane.showMessageDialog(this, "Definition cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }
}
private void editButtonClicked() {
    String slangWord = JOptionPane.showInputDialog(this, "Nhập từ lóng cần sửa:");
    if (slangWord != null) {
        slangWord = slangWord.trim();
        if (slangWord.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Từ lóng không được để trống.", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (slangDictionary.containsKey(slangWord)) {
            String currentDefinition = slangDictionary.get(slangWord);
            String newDefinition = JOptionPane.showInputDialog(this, "Định nghĩa hiện tại: " + currentDefinition + "\nNhập định nghĩa mới:");

            if (newDefinition != null) {
                newDefinition = newDefinition.trim();
                if (!newDefinition.isEmpty()) {
                    slangDictionary.put(slangWord, newDefinition);
                    saveSlangDictionaryToFile("slang.txt");
                    JOptionPane.showMessageDialog(this, "Từ lóng được sửa thành công.");
                } else {
                    JOptionPane.showMessageDialog(this, "Định nghĩa không được để trống.", "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        } else {
            JOptionPane.showMessageDialog(this, "Từ lóng không tồn tại.", "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }
}

public static void main(String[] args) {
    new SlangDictionaryApp();
}
}