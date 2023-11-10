import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

public class SlangDictionaryApp extends JFrame {
    private Map<String, String> slangDictionary;
    private List<String> searchHistory;
    private JTextArea outputTextArea;
    private JTextField searchTextField;

    public SlangDictionaryApp() {
        slangDictionary = new HashMap<>();
        searchHistory = new ArrayList<>();

        loadSlangDictionaryFromFile("slang_dictionary.txt");

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
        JScrollPane scrollPane = new JScrollPane(outputTextArea);
        scrollPane.setBounds(10, 40, 480, 310);
        panel.add(scrollPane);

        searchButton.addActionListener(e -> searchButtonClicked());
    }

    private void searchButtonClicked() {
        String keyword = searchTextField.getText().trim();
        searchSlangWord(keyword);
        searchByDefinition(keyword);
        addToSearchHistory(keyword);
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

    private void loadSlangDictionaryFromFile(String filename) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("=");
                if (parts.length == 2) {
                    String slangWord = parts[0].trim().toLowerCase();
                    String definition = parts[1].trim().toLowerCase();
                    slangDictionary.put(slangWord, definition);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new SlangDictionaryApp();
    }
}