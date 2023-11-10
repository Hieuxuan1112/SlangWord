import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.util.HashMap;

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
        editButton.setBounds(370, 40, 80, 25);
        panel.add(editButton);

        JButton deleteButton = new JButton("Delete");
        deleteButton.setBounds(370, 70, 80, 25);
        panel.add(deleteButton);

        JButton resetButton = new JButton("Reset");
        resetButton.setBounds(370, 100, 80, 25);
        panel.add(resetButton);

        JButton randomButton = new JButton("On This Day");
        randomButton.setBounds(460, 10, 100, 25);
        panel.add(randomButton);

        JButton quizButton = new JButton("Quiz");
        quizButton.setBounds(460, 40, 100, 25);
        panel.add(quizButton);

        searchButton.addActionListener(e -> searchButtonClicked());
        addButton.addActionListener(e -> addButtonClicked());
        editButton.addActionListener(e -> editButtonClicked());
        deleteButton.addActionListener(e -> deleteButtonClicked());
        resetButton.addActionListener(e -> resetButtonClicked());
        randomButton.addActionListener(e -> randomButtonClicked());
        quizButton.addActionListener(e -> quizButtonClicked());
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
        for (String keyword : searchHistory){
            historyBuilder.append(keyword).append("\n");
        }
        historyTextArea.setText(historyBuilder.toString());
    }

    private void addButtonClicked() {
        String slangWord = JOptionPane.showInputDialog(this, "Enter the slang word:");
        String definition = JOptionPane.showInputDialog(this, "Enter the definition:");
        slangDictionary.put(slangWord, definition);
        outputTextArea.setText("Slang word added successfully.");
    }

    private void editButtonClicked() {
        String slangWord = JOptionPane.showInputDialog(this, "Enter the slang word to edit:");
        if (slangDictionary.containsKey(slangWord)) {
            String newDefinition = JOptionPane.showInputDialog(this, "Enter the new definition:");
            slangDictionary.put(slangWord, newDefinition);
            outputTextArea.setText("Slang word edited successfully.");
        } else {
            outputTextArea.setText("Slang word not found.");
        }
    }

    private void deleteButtonClicked() {
        String slangWord = JOptionPane.showInputDialog(this, "Enter the slang word to delete:");
        if (slangDictionary.containsKey(slangWord)) {
            slangDictionary.remove(slangWord);
            outputTextArea.setText("Slang word deleted successfully.");
        } else {
            outputTextArea.setText("Slang word not found.");
        }
    }

    private void resetButtonClicked() {
        int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to reset the dictionary?");
        if (confirm == JOptionPane.YES_OPTION) {
            slangDictionary.clear();
            outputTextArea.setText("Dictionary reset successfully.");
        }
    }

    private void randomButtonClicked() {
        Random random = new Random();
        List<String> keys = new ArrayList<>(slangDictionary.keySet());
        if (keys.isEmpty()) {
            outputTextArea.setText("No slang words available.");
        } else {
            String randomSlangWord = keys.get(random.nextInt(keys.size()));
            String definition = slangDictionary.get(randomSlangWord);
            outputTextArea.setText(randomSlangWord + ": " + definition);
        }
    }

    private void quizButtonClicked() {
        List<String> keys = new ArrayList<>(slangDictionary.keySet());
        if (keys.isEmpty()) {
            outputTextArea.setText("No slang words available for the quiz.");
        } else {
            Random random = new Random();
            int randomIndex = random.nextInt(keys.size());
            String randomSlangWord = keys.get(randomIndex);
            String definition = slangDictionary.get(randomSlangWord);
            String userAnswer = JOptionPane.showInputDialog(this, "What is the definition of \"" + randomSlangWord + "\"?");
            if (userAnswer != null && userAnswer.equalsIgnoreCase(definition)) {
                outputTextArea.setText("Correct! \"" + randomSlangWord + "\" means \"" + definition + "\".");
            } else {
                outputTextArea.setText("Incorrect! \"" + randomSlangWord + "\" means \"" + definition + "\".");
            }
        }
    }

    private void loadSlangDictionaryFromFile(String filename) {
        // Implement the logic to load the slang dictionary from a file
    }

    public static void main(String[] args) {
        new SlangDictionaryApp();
    }
}