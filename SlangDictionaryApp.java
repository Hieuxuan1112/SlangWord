import java.awt.BorderLayout;
import java.awt.FlowLayout;
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
import java.awt.Dimension;


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

        JPanel panel = new JPanel(new BorderLayout());
        getContentPane().add(panel);
        placeComponents(panel);

        setVisible(true);
    }

    private void placeComponents(JPanel panel) {
        panel.add(createSearchPanel(), BorderLayout.NORTH);
        panel.add(createOutputPanel(), BorderLayout.CENTER);
        panel.add(createButtonPanel(), BorderLayout.SOUTH);
    }

    private JPanel createSearchPanel() {
        JPanel searchPanel = new JPanel(new FlowLayout());

        JLabel searchLabel = new JLabel("Search:");
        searchTextField = new JTextField(20);
        JButton searchButton = new JButton("Search");

        searchPanel.add(searchLabel);
        searchPanel.add(searchTextField);
        searchPanel.add(searchButton);

        searchButton.addActionListener(e -> searchButtonClicked());

        return searchPanel;
    }

    private JPanel createOutputPanel() {
        JPanel outputPanel = new JPanel(new BorderLayout());
    
        outputTextArea = new JTextArea();
        outputTextArea.setEditable(false);
        JScrollPane outputScrollPane = new JScrollPane(outputTextArea);
    
        historyTextArea = new JTextArea();
        historyTextArea.setEditable(false);
        JScrollPane historyScrollPane = new JScrollPane(historyTextArea);
    
        outputPanel.add(outputScrollPane, BorderLayout.CENTER);
        outputPanel.add(historyScrollPane, BorderLayout.SOUTH);
    
        // Adjust the preferred size of historyScrollPane
        historyScrollPane.setPreferredSize(new Dimension(historyScrollPane.getPreferredSize().width, 150));
    
        return outputPanel;
    }
    

    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout());

        JButton addButton = new JButton("Add");
        JButton editButton = new JButton("Edit");
        JButton deleteButton = new JButton("Delete");
        JButton resetButton = new JButton("Reset");
        JButton randomButton = new JButton("Random");
        JButton quizButton = new JButton("Quiz");

        buttonPanel.add(addButton);
        buttonPanel.add(editButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(resetButton);
        buttonPanel.add(randomButton);
        buttonPanel.add(quizButton);

        addButton.addActionListener(e -> addButtonClicked());
        editButton.addActionListener(e -> editButtonClicked());
        deleteButton.addActionListener(e -> deleteButtonClicked());
        resetButton.addActionListener(e -> resetButtonClicked());
        randomButton.addActionListener(e -> randomButtonClicked());
        quizButton.addActionListener(e -> quizButtonClicked());

        return buttonPanel;
    }

    private void searchButtonClicked() {
        String keyword = searchTextField.getText().trim();
        if (!keyword.isEmpty()) {
            searchSlangWord(keyword);
            searchByDefinition(keyword);
            addToSearchHistory(keyword);
            updateHistoryTextArea();
        }
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
            JOptionPane.showMessageDialog(this, "Error loading slang dictionary from file.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveSlangDictionaryToFile(String filename) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            for (Map.Entry<String, String> entry : slangDictionary.entrySet()) {
                String slangWord = entry.getKey();
                String definition = entry.getValue();
                writer.write(slangWord + "`" + definition);
                writer.newLine();
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error saving slang dictionary to file.", "Error", JOptionPane.ERROR_MESSAGE);
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
            handleAddEditButton(slangWord);
        }
    }

    private void editButtonClicked() {
        String slangWord = JOptionPane.showInputDialog(this, "Enter slang word to edit:");
        if (slangWord != null) {
            slangWord = slangWord.trim();
            if (slangWord.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Slang word cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            handleAddEditButton(slangWord);
        }
    }

    private void handleAddEditButton(String slangWord) {
        if (slangDictionary.containsKey(slangWord)) {
            handleExistingSlangWord(slangWord);
        } else {
            handleNewSlangWord(slangWord);
        }
    }

    private void handleExistingSlangWord(String slangWord) {
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
    }

    private void handleNewSlangWord(String slangWord) {
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

    private void deleteButtonClicked() {
        String slangWord = JOptionPane.showInputDialog(this, "Enter slang word to delete:");
        if (slangWord != null) {
            slangWord = slangWord.trim();
            if (slangWord.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Slang word cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            handleDeleteButton(slangWord);
        }
    }

    private void handleDeleteButton(String slangWord) {
        if (slangDictionary.containsKey(slangWord)) {
            int choice = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete this slang word?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                slangDictionary.remove(slangWord);
                saveSlangDictionaryToFile("slang.txt");
                JOptionPane.showMessageDialog(this, "Slang word deleted successfully.");
            }
        } else {
            JOptionPane.showMessageDialog(this, "Slang word does not exist.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void resetButtonClicked() {
        int choice = JOptionPane.showConfirmDialog(this, "Are you sure you want to reset the dictionary?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            slangDictionary.clear();
            loadSlangDictionaryFromFile("slang.txt");
            updateHistoryTextArea();
            JOptionPane.showMessageDialog(this, "Dictionary reset successfully.");
        }
    }

    private void randomButtonClicked() {
        List<String> keys = new ArrayList<>(slangDictionary.keySet());
        if (!keys.isEmpty()) {
            int randomIndex = (int) (Math.random() * keys.size());
            String randomSlangWord = keys.get(randomIndex);
            JOptionPane.showMessageDialog(this, "Random slang word:\n" + randomSlangWord, "Random Slang Word", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, "Dictionary is empty.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void quizButtonClicked() {
        List<String> keys = new ArrayList<>(slangDictionary.keySet());
        if (keys.size() >= 4) {
            int randomIndex = (int) (Math.random() * keys.size());
            String correctAnswer = keys.get(randomIndex);
            String definition = slangDictionary.get(correctAnswer);

            List<String> options = new ArrayList<>();
            options.add(correctAnswer);

            while (options.size() < 4) {
                int randomOptionIndex = (int) (Math.random() * keys.size());
                String randomOption = keys.get(randomOptionIndex);
                if (!options.contains(randomOption)) {
                    options.add(randomOption);
                }
            }

            java.util.Collections.shuffle(options);

            StringBuilder quizMessage = new StringBuilder("Find the slang word for the following definition:\n\n");
            quizMessage.append(definition).append("\n\nOptions:\n");
            for (int i = 0; i < options.size(); i++) {
                quizMessage.append(i + 1).append(". ").append(options.get(i)).append("\n");
            }

            int userChoice = JOptionPane.showOptionDialog(this, quizMessage.toString(), "Quiz Game", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options.toArray(), null);

            if (userChoice >= 0 && userChoice < options.size() && options.get(userChoice).equals(correctAnswer)) {
                JOptionPane.showMessageDialog(this, "Correct! The slang word is \"" + correctAnswer + "\".", "Quiz Result", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Wrong. The correct slang word is \"" + correctAnswer + "\".", "Quiz Result", JOptionPane.INFORMATION_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(this, "Not enough slang words to play the quiz.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        new SlangDictionaryApp();
    }
}
