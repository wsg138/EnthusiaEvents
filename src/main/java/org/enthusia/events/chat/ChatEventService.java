package org.enthusia.events.chat;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitTask;
import org.enthusia.events.EnthusiaEventsPlugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

@SuppressWarnings("PMD.NullAssignment")
public final class ChatEventService implements Listener {

    private final EnthusiaEventsPlugin plugin;
    private final Economy economy;
    private final File file;
    private final List<TriviaQuestion> triviaQuestions = new ArrayList<>();
    private BukkitTask task;
    private ChatPrompt activePrompt;
    private int lastMinute = -1;

    public ChatEventService(EnthusiaEventsPlugin plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
        File directory = new File(plugin.getDataFolder(), "chat-events");
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().warning("Could not create chat-events folder.");
        }
        this.file = new File(directory, "chat-events.yml");
        reload();
    }

    public void reload() {
        if (!file.exists()) {
            writeDefaults();
        }
        triviaQuestions.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection trivia = yaml.getConfigurationSection("trivia");
        if (trivia != null) {
            for (String key : trivia.getKeys(false)) {
                ConfigurationSection section = trivia.getConfigurationSection(key);
                if (section != null) {
                    String question = section.getString("question", "");
                    String answer = normalize(section.getString("answer", ""));
                    if (!question.isBlank() && !answer.isBlank()) {
                        triviaQuestions.add(new TriviaQuestion(question, answer));
                    }
                }
            }
        }
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L * 60L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        activePrompt = null;
        lastMinute = -1;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        ChatPrompt prompt = activePrompt;
        if (prompt == null || !normalize(event.getMessage()).equals(prompt.answer())) {
            return;
        }
        event.setCancelled(true);
        activePrompt = null;
        Bukkit.getScheduler().runTask(plugin, () -> rewardWinner(event.getPlayer(), prompt));
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("chat-events.enabled", false)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        int minute = now.getMinute();
        if (minute == lastMinute) {
            return;
        }
        List<Integer> minutes = plugin.getConfig().getIntegerList("schedule.chat-event-minutes");
        if (minutes.isEmpty()) {
            minutes = List.of(0, 15, 30, 45);
        }
        if (!minutes.contains(minute)) {
            return;
        }
        lastMinute = minute;
        startPrompt();
    }

    private void startPrompt() {
        int choice = ThreadLocalRandom.current().nextInt(3);
        activePrompt = switch (choice) {
            case 0 -> triviaPrompt();
            case 1 -> mathPrompt();
            default -> scramblePrompt();
        };
        if (activePrompt == null) {
            return;
        }
        Bukkit.broadcastMessage(ChatColor.GOLD + "[Events] " + ChatColor.YELLOW + activePrompt.question());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (activePrompt != null) {
                Bukkit.broadcastMessage(ChatColor.GOLD + "[Events] " + ChatColor.GRAY
                        + "Chat event expired. Answer: " + ChatColor.WHITE + activePrompt.answer());
                activePrompt = null;
            }
        }, plugin.getConfig().getLong("chat-events.answer-window-seconds", 45L) * 20L);
    }

    private ChatPrompt triviaPrompt() {
        if (triviaQuestions.isEmpty()) {
            return mathPrompt();
        }
        TriviaQuestion question = triviaQuestions.get(ThreadLocalRandom.current().nextInt(triviaQuestions.size()));
        return new ChatPrompt("Trivia: " + question.question(), question.answer());
    }

    private ChatPrompt mathPrompt() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int left = random.nextInt(8, 61);
        int right = random.nextInt(3, 31);
        boolean multiply = random.nextBoolean();
        int answer = multiply ? left * right : left + right;
        return new ChatPrompt("Math: first to answer " + left + (multiply ? " x " : " + ") + right + " wins.", String.valueOf(answer));
    }

    private ChatPrompt scramblePrompt() {
        List<String> words = plugin.getConfig().getStringList("chat-events.scramble-words");
        if (words.isEmpty()) {
            words = List.of("enthusia", "events", "minecraft", "challenge", "winner");
        }
        String answer = words.get(ThreadLocalRandom.current().nextInt(words.size())).toLowerCase(Locale.ROOT);
        List<Character> letters = new ArrayList<>();
        for (char character : answer.toCharArray()) {
            letters.add(character);
        }
        java.util.Collections.shuffle(letters);
        StringBuilder scrambled = new StringBuilder();
        letters.forEach(scrambled::append);
        return new ChatPrompt("Unscramble: " + scrambled, answer);
    }

    private void rewardWinner(Player player, ChatPrompt prompt) {
        double reward = plugin.getConfig().getDouble("chat-events.reward", 50.0D);
        if (economy != null && reward > 0.0D) {
            economy.depositPlayer(player, reward);
        }
        Bukkit.broadcastMessage(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + player.getName()
                + " won the chat event. Answer: " + ChatColor.WHITE + prompt.answer());
    }

    private void writeDefaults() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("config-version", 1);
        yaml.set("trivia.0.question", "What material is used to craft a bed in Minecraft?");
        yaml.set("trivia.0.answer", "wool");
        yaml.set("trivia.1.question", "How many obsidian blocks are required for a full Nether portal frame?");
        yaml.set("trivia.1.answer", "14");
        yaml.set("trivia.2.question", "What boss drops a Nether Star?");
        yaml.set("trivia.2.answer", "wither");
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to create chat-events/chat-events.yml: " + e.getMessage());
        }
    }

    private String normalize(String value) {
        return ChatColor.stripColor(value == null ? "" : value).trim().toLowerCase(Locale.ROOT);
    }

    private record ChatPrompt(String question, String answer) {
    }

    private record TriviaQuestion(String question, String answer) {
    }
}
