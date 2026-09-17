import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Scanner;

/*
 * =====================================================================
 *  SPACED REPETITION SKILL & HABIT TRACKER
 *  Single-file consolidated edition.
 *
 *  This file contains every class, interface, and enum used by the
 *  application. It is functionally identical to the multi-package
 *  edition (models / engine / utils / main) shipped alongside this
 *  file, but is provided as one compilation unit for submissions that
 *  require a single .java file.
 *
 *  Java only allows ONE public top-level type per file, and it must
 *  match the filename. That type is `Main`, below. Every other class
 *  in this file is declared package-private (no access modifier) so
 *  they may legally coexist in the same file.
 *
 *  Compile:  javac Main.java
 *  Run:      java Main
 * =====================================================================
 */

/**
 * Application entry point. Delegates all control flow to a
 * SessionRunner (the console UI module).
 */
public class Main {
    public static void main(String[] args) {
        SessionRunner sessionRunner = new SessionRunner();
        sessionRunner.run();
    }
}

// =====================================================================
// MODELS
// =====================================================================

/**
 * Represents the broad domain a Task (Skill or Habit) belongs to.
 * Used as the key basis for the Map<String, List<Task>> categorization
 * structure maintained by the DeckManager.
 */
enum Category {
    TECHNICAL_SKILL,
    MUSICAL_SKILL,
    HANDWRITING,
    PHYSICAL_FITNESS
}

/**
 * An immutable log entry representing a single review event for a Task.
 * A List<ReviewRecord> forms the historical audit trail of a Task,
 * which is later used for CSV export and for testing the scheduling
 * engine.
 */
class ReviewRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private final LocalDate reviewDate;
    private final int qualityRating;
    private final int resultingIntervalDays;

    ReviewRecord(LocalDate reviewDate, int qualityRating, int resultingIntervalDays) {
        this.reviewDate = reviewDate;
        this.qualityRating = qualityRating;
        this.resultingIntervalDays = resultingIntervalDays;
    }

    LocalDate getReviewDate() {
        return reviewDate;
    }

    int getQualityRating() {
        return qualityRating;
    }

    int getResultingIntervalDays() {
        return resultingIntervalDays;
    }

    @Override
    public String toString() {
        return String.format("%s -> Rating: %d, Next Interval: %d day(s)",
                reviewDate, qualityRating, resultingIntervalDays);
    }
}

/**
 * Abstract base class representing any trackable item (a Skill or a
 * Habit) that is subject to spaced-repetition scheduling.
 *
 * Demonstrates:
 *  - Encapsulation: all fields are private with controlled accessors.
 *  - Abstraction: getTaskType() / getProgressSummary() are implemented
 *    differently by each concrete subclass (polymorphism).
 *  - Natural ordering via Comparable, so Task objects can be placed
 *    directly into a PriorityQueue ordered by nextReviewDate.
 */
abstract class Task implements Serializable, Comparable<Task> {

    private static final long serialVersionUID = 1L;

    /** Class-level counter used to hand out unique, human-readable IDs. */
    private static int idCounter = 1;

    private final int id;
    private String name;
    private String description;
    private Category category;
    private final LocalDate creationDate;
    private LocalDate nextReviewDate;
    private int repetitionCount;
    private double easinessFactor;
    private int currentInterval;
    private final List<ReviewRecord> history;

    protected Task(String name, String description, Category category) {
        this.id = idCounter++;
        this.name = name;
        this.description = description;
        this.category = category;
        this.creationDate = LocalDate.now();
        this.nextReviewDate = LocalDate.now();
        this.repetitionCount = 0;
        this.easinessFactor = 2.5; // Standard SuperMemo-2 starting easiness factor.
        this.currentInterval = 0;
        this.history = new ArrayList<>();
    }

    /**
     * Ensures the static ID counter never issues a duplicate ID after a
     * DeckManager has been restored from disk (since static fields are
     * not part of the serialized object graph).
     */
    static void ensureIdCounterAbove(int usedId) {
        if (usedId >= idCounter) {
            idCounter = usedId + 1;
        }
    }

    // ---- Abstract contract implemented polymorphically by subclasses ----
    abstract String getTaskType();
    abstract String getProgressSummary();

    // ---- Encapsulated accessors ----
    int getId() {
        return id;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    String getDescription() {
        return description;
    }

    void setDescription(String description) {
        this.description = description;
    }

    Category getCategory() {
        return category;
    }

    void setCategory(Category category) {
        this.category = category;
    }

    LocalDate getCreationDate() {
        return creationDate;
    }

    LocalDate getNextReviewDate() {
        return nextReviewDate;
    }

    void setNextReviewDate(LocalDate nextReviewDate) {
        this.nextReviewDate = nextReviewDate;
    }

    int getRepetitionCount() {
        return repetitionCount;
    }

    void setRepetitionCount(int repetitionCount) {
        this.repetitionCount = repetitionCount;
    }

    double getEasinessFactor() {
        return easinessFactor;
    }

    void setEasinessFactor(double easinessFactor) {
        this.easinessFactor = easinessFactor;
    }

    int getCurrentInterval() {
        return currentInterval;
    }

    void setCurrentInterval(int currentInterval) {
        this.currentInterval = currentInterval;
    }

    List<ReviewRecord> getHistory() {
        return Collections.unmodifiableList(history);
    }

    void addReviewRecord(ReviewRecord record) {
        this.history.add(record);
    }

    /** Natural ordering: earliest due date first, enabling PriorityQueue usage. */
    @Override
    public int compareTo(Task other) {
        return this.nextReviewDate.compareTo(other.nextReviewDate);
    }

    @Override
    public String toString() {
        return String.format("[#%d] %-38s | %-16s | Next Review: %s | Reps: %d | EF: %.2f",
                id, name, category, nextReviewDate, repetitionCount, easinessFactor);
    }
}

/**
 * Concrete Task subclass representing a "Skill" that is being acquired
 * or refined through deliberate, spaced practice (e.g. Java DSA, Harmonica,
 * Cursive Handwriting). Skills are tracked by a self-declared proficiency
 * label rather than a day-to-day streak.
 */
class SkillTask extends Task {

    private static final long serialVersionUID = 1L;

    private String proficiencyLevel;

    SkillTask(String name, String description, Category category, String proficiencyLevel) {
        super(name, description, category);
        this.proficiencyLevel = proficiencyLevel;
    }

    String getProficiencyLevel() {
        return proficiencyLevel;
    }

    void setProficiencyLevel(String proficiencyLevel) {
        this.proficiencyLevel = proficiencyLevel;
    }

    @Override
    String getTaskType() {
        return "Skill";
    }

    @Override
    String getProgressSummary() {
        return "Proficiency Level: " + proficiencyLevel
                + " | Successful repetitions logged: " + getRepetitionCount();
    }
}

/**
 * Concrete Task subclass representing a recurring "Habit" (e.g. a
 * Calisthenics routine) whose progress is best expressed as a streak
 * rather than a proficiency label. Demonstrates that subclasses of the
 * same abstract Task can carry entirely different internal state while
 * still being interchangeable wherever a Task is expected (polymorphism).
 */
class HabitTask extends Task {

    private static final long serialVersionUID = 1L;

    private int currentStreak;
    private int longestStreak;

    HabitTask(String name, String description, Category category) {
        super(name, description, category);
        this.currentStreak = 0;
        this.longestStreak = 0;
    }

    void incrementStreak() {
        currentStreak++;
        if (currentStreak > longestStreak) {
            longestStreak = currentStreak;
        }
    }

    void resetStreak() {
        currentStreak = 0;
    }

    int getCurrentStreak() {
        return currentStreak;
    }

    int getLongestStreak() {
        return longestStreak;
    }

    @Override
    String getTaskType() {
        return "Habit";
    }

    @Override
    String getProgressSummary() {
        return "Current Streak: " + currentStreak + " session(s) | Longest Streak: " + longestStreak + " session(s)";
    }
}

// =====================================================================
// ENGINE
// =====================================================================

/**
 * Strategy interface for spaced-repetition scheduling algorithms.
 * Decoupling the scheduling policy from the SessionRunner (client code)
 * means alternative algorithms (e.g. Leitner system, SM-18, a fixed-interval
 * scheme) could be substituted in the future without touching the console UI.
 */
interface SchedulingAlgorithm {

    /**
     * Given a Task and a user-supplied quality rating (1 = poor recall,
     * 5 = perfect recall), updates the Task's easiness factor, repetition
     * count, interval, and next review date, and appends a ReviewRecord
     * to its history.
     */
    void schedule(Task task, int qualityRating);
}

/**
 * Concrete implementation of the SuperMemo-2 (SM-2) spaced repetition
 * algorithm, adapted to accept a 1-5 quality rating instead of SM-2's
 * traditional 0-5 scale (1-2 = failed recall, 3-5 = successful recall of
 * increasing quality).
 *
 * Reference formula (original SM-2, q on a 0-5 scale):
 *   EF' = EF + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))
 *   EF' is clamped to a minimum of 1.3.
 *
 * Interval progression on success:
 *   n = 1  -> interval = 1 day
 *   n = 2  -> interval = 6 days
 *   n > 2  -> interval = round(previous interval * EF)
 *
 * On failure (q < 3), the repetition counter and interval both reset,
 * mirroring SM-2's "forgetting" behaviour.
 */
class SM2SchedulingAlgorithm implements SchedulingAlgorithm {

    private static final double MIN_EASINESS_FACTOR = 1.3;
    private static final int SECOND_REPETITION_INTERVAL = 6;
    private static final int FIRST_REPETITION_INTERVAL = 1;

    @Override
    public void schedule(Task task, int qualityRating) {
        if (qualityRating < 1 || qualityRating > 5) {
            throw new IllegalArgumentException("Quality rating must be between 1 and 5 inclusive.");
        }

        double easinessFactor = task.getEasinessFactor();
        int repetitions = task.getRepetitionCount();
        int newInterval;

        if (qualityRating < 3) {
            // Failed recall: restart the repetition sequence.
            repetitions = 0;
            newInterval = FIRST_REPETITION_INTERVAL;
            if (task instanceof HabitTask) {
                ((HabitTask) task).resetStreak();
            }
        } else {
            repetitions += 1;
            if (repetitions == 1) {
                newInterval = FIRST_REPETITION_INTERVAL;
            } else if (repetitions == 2) {
                newInterval = SECOND_REPETITION_INTERVAL;
            } else {
                newInterval = (int) Math.round(task.getCurrentInterval() * easinessFactor);
            }
            if (task instanceof HabitTask) {
                ((HabitTask) task).incrementStreak();
            }
        }

        double updatedEf = easinessFactor
                + (0.1 - (5 - qualityRating) * (0.08 + (5 - qualityRating) * 0.02));
        if (updatedEf < MIN_EASINESS_FACTOR) {
            updatedEf = MIN_EASINESS_FACTOR;
        }

        task.setEasinessFactor(updatedEf);
        task.setRepetitionCount(repetitions);
        task.setCurrentInterval(newInterval);
        task.setNextReviewDate(LocalDate.now().plusDays(newInterval));
        task.addReviewRecord(new ReviewRecord(LocalDate.now(), qualityRating, newInterval));
    }
}

/**
 * Owns the in-memory "deck" of all tracked Tasks, organized as a
 * Map<String, List<Task>> keyed by Category name. Provides full CRUD
 * operations and exposes due tasks via a PriorityQueue ordered by
 * next review date (earliest due first).
 */
class DeckManager implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Map<String, List<Task>> deck;

    DeckManager() {
        this.deck = new HashMap<>();
    }

    // ---- Create ----
    void addTask(Task task) {
        String key = task.getCategory().name();
        deck.computeIfAbsent(key, k -> new ArrayList<>()).add(task);
    }

    // ---- Read ----
    Optional<Task> findTaskById(int id) {
        return getAllTasks().stream().filter(t -> t.getId() == id).findFirst();
    }

    List<Task> getAllTasks() {
        List<Task> all = new ArrayList<>();
        for (List<Task> categoryTasks : deck.values()) {
            all.addAll(categoryTasks);
        }
        return all;
    }

    List<Task> getTasksByCategory(String categoryKey) {
        return deck.getOrDefault(categoryKey, Collections.emptyList());
    }

    Map<String, List<Task>> getDeck() {
        return deck;
    }

    int totalTaskCount() {
        return getAllTasks().size();
    }

    // ---- Delete ----
    boolean removeTaskById(int id) {
        for (List<Task> categoryTasks : deck.values()) {
            Iterator<Task> iterator = categoryTasks.iterator();
            while (iterator.hasNext()) {
                if (iterator.next().getId() == id) {
                    iterator.remove();
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Builds a PriorityQueue of every Task whose next review date is
     * today or earlier, ordered earliest-due-first via Task's natural
     * Comparable ordering.
     */
    PriorityQueue<Task> getDueTasksQueue() {
        PriorityQueue<Task> dueQueue = new PriorityQueue<>();
        LocalDate today = LocalDate.now();
        for (Task task : getAllTasks()) {
            if (!task.getNextReviewDate().isAfter(today)) {
                dueQueue.add(task);
            }
        }
        return dueQueue;
    }
}

/**
 * The console-based "Session Runner" module. Owns the main menu loop,
 * builds the priority queue of due tasks for a review session, and routes
 * user feedback into the SchedulingAlgorithm. Also hosts the Deck & Card
 * Manager (CRUD) submenu.
 */
class SessionRunner {

    private final DeckManager deckManager;
    private final SchedulingAlgorithm schedulingAlgorithm;
    private final FileManager fileManager;
    private final Scanner scanner;

    SessionRunner() {
        this.fileManager = new FileManager();
        DeckManager loaded = fileManager.loadDeckManager();
        if (loaded != null) {
            this.deckManager = loaded;
            for (Task task : deckManager.getAllTasks()) {
                Task.ensureIdCounterAbove(task.getId());
            }
            System.out.println("[System] Existing tracker data loaded successfully.\n");
        } else {
            this.deckManager = new DeckManager();
            SeedDataInitializer.populate(this.deckManager);
            System.out.println("[System] No saved data found. Initialized with seed data.\n");
        }
        this.schedulingAlgorithm = new SM2SchedulingAlgorithm();
        this.scanner = new Scanner(System.in);
    }

    void run() {
        printBanner();
        boolean running = true;
        while (running) {
            printMainMenu();
            int choice = InputValidator.readIntInRange(scanner, "Enter choice: ", 1, 5);
            switch (choice) {
                case 1:
                    startReviewSession();
                    break;
                case 2:
                    deckManagementMenu();
                    break;
                case 3:
                    viewAllTasks();
                    break;
                case 4:
                    fileManager.exportHistoryToCsv(deckManager);
                    break;
                case 5:
                    fileManager.saveDeckManager(deckManager);
                    System.out.println("Goodbye! Keep practicing.");
                    running = false;
                    break;
                default:
                    break;
            }
        }
        scanner.close();
    }

    private void printBanner() {
        System.out.println("=========================================================");
        System.out.println("   SPACED REPETITION SKILL & HABIT TRACKER (SM-2 Engine)");
        System.out.println("=========================================================");
    }

    private void printMainMenu() {
        System.out.println("\n--- MAIN MENU ---");
        System.out.println("1. Start Review Session");
        System.out.println("2. Manage Skills/Habits (CRUD)");
        System.out.println("3. View All Tasks");
        System.out.println("4. Export Review History (CSV)");
        System.out.println("5. Save & Exit");
    }

    // ------------------------------------------------------------------
    // Module: Session Runner (queues due tasks, processes feedback)
    // ------------------------------------------------------------------
    private void startReviewSession() {
        PriorityQueue<Task> dueQueue = deckManager.getDueTasksQueue();
        if (dueQueue.isEmpty()) {
            System.out.println("\n[Session] No tasks are due for review today. Great job staying ahead!");
            return;
        }

        System.out.println("\n[Session] " + dueQueue.size() + " task(s) due for review.");
        int sessionCount = 0;
        while (!dueQueue.isEmpty()) {
            Task current = dueQueue.poll();
            sessionCount++;
            System.out.println("\n--- Reviewing Task " + sessionCount + " ---");
            System.out.println(current);
            System.out.println("Type        : " + current.getTaskType());
            System.out.println("Description : " + current.getDescription());
            System.out.println("Progress    : " + current.getProgressSummary());

            int rating = InputValidator.readIntInRange(
                    scanner, "Rate your recall/performance (1=Poor ... 5=Excellent): ", 1, 5);

            schedulingAlgorithm.schedule(current, rating);

            System.out.println("[Scheduled] Next review for \"" + current.getName()
                    + "\" is on " + current.getNextReviewDate()
                    + " (interval: " + current.getCurrentInterval() + " day(s)).");
        }
        System.out.println("\n[Session] Review session complete. " + sessionCount + " task(s) processed.");
    }

    // ------------------------------------------------------------------
    // Module: Deck & Card Manager (CRUD)
    // ------------------------------------------------------------------
    private void deckManagementMenu() {
        boolean back = false;
        while (!back) {
            System.out.println("\n--- MANAGE SKILLS/HABITS ---");
            System.out.println("1. Add New Skill");
            System.out.println("2. Add New Habit");
            System.out.println("3. Edit Existing Task");
            System.out.println("4. Delete Task");
            System.out.println("5. Back to Main Menu");
            int choice = InputValidator.readIntInRange(scanner, "Enter choice: ", 1, 5);
            switch (choice) {
                case 1:
                    addSkill();
                    break;
                case 2:
                    addHabit();
                    break;
                case 3:
                    editTask();
                    break;
                case 4:
                    deleteTask();
                    break;
                case 5:
                    back = true;
                    break;
                default:
                    break;
            }
        }
    }

    private Category chooseCategory() {
        System.out.println("Select Category:");
        Category[] categories = Category.values();
        for (int i = 0; i < categories.length; i++) {
            System.out.println((i + 1) + ". " + categories[i]);
        }
        int idx = InputValidator.readIntInRange(scanner, "Enter choice: ", 1, categories.length);
        return categories[idx - 1];
    }

    private void addSkill() {
        String name = InputValidator.readNonEmptyLine(scanner, "Enter skill name: ");
        String desc = InputValidator.readNonEmptyLine(scanner, "Enter description: ");
        Category category = chooseCategory();
        String level = InputValidator.readNonEmptyLine(scanner, "Enter current proficiency level: ");
        SkillTask task = new SkillTask(name, desc, category, level);
        deckManager.addTask(task);
        System.out.println("[System] Skill added: " + task);
    }

    private void addHabit() {
        String name = InputValidator.readNonEmptyLine(scanner, "Enter habit name: ");
        String desc = InputValidator.readNonEmptyLine(scanner, "Enter description: ");
        Category category = chooseCategory();
        HabitTask task = new HabitTask(name, desc, category);
        deckManager.addTask(task);
        System.out.println("[System] Habit added: " + task);
    }

    private void editTask() {
        viewAllTasks();
        if (deckManager.totalTaskCount() == 0) {
            return;
        }
        int id = InputValidator.readIntInRange(scanner, "Enter Task ID to edit: ", 1, Integer.MAX_VALUE);
        deckManager.findTaskById(id).ifPresentOrElse(task -> {
            String name = InputValidator.readNonEmptyLine(
                    scanner, "Enter new name (current: " + task.getName() + "): ");
            String desc = InputValidator.readNonEmptyLine(scanner, "Enter new description: ");
            task.setName(name);
            task.setDescription(desc);
            System.out.println("[System] Task updated: " + task);
        }, () -> System.out.println("[Error] Task ID not found."));
    }

    private void deleteTask() {
        viewAllTasks();
        if (deckManager.totalTaskCount() == 0) {
            return;
        }
        int id = InputValidator.readIntInRange(scanner, "Enter Task ID to delete: ", 1, Integer.MAX_VALUE);
        boolean removed = deckManager.removeTaskById(id);
        System.out.println(removed ? "[System] Task removed." : "[Error] Task ID not found.");
    }

    private void viewAllTasks() {
        List<Task> all = deckManager.getAllTasks();
        if (all.isEmpty()) {
            System.out.println("\n[Info] No tasks currently tracked.");
            return;
        }
        System.out.println("\n--- ALL TRACKED SKILLS & HABITS ---");
        for (Task task : all) {
            System.out.println(task);
        }
    }
}

// =====================================================================
// UTILS
// =====================================================================

/**
 * Centralizes defensive console-input handling so the SessionRunner never
 * crashes on malformed user input (e.g. letters typed where a number is
 * expected). Every read loops until valid input is supplied.
 */
final class InputValidator {

    private InputValidator() {
        // Utility class; no instances.
    }

    static int readIntInRange(Scanner scanner, String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine().trim();
            try {
                int value = Integer.parseInt(line);
                if (value < min || value > max) {
                    System.out.println("[Input Error] Please enter a number between " + min + " and " + max + ".");
                    continue;
                }
                return value;
            } catch (NumberFormatException e) {
                System.out.println("[Input Error] Please enter a valid whole number.");
            }
        }
    }

    static String readNonEmptyLine(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine().trim();
            if (!line.isEmpty()) {
                return line;
            }
            System.out.println("[Input Error] This field cannot be empty.");
        }
    }
}

/**
 * Handles all persistence concerns for the application using pure Java
 * File I/O: binary object serialization for saving/restoring full
 * application state, and a human-readable CSV export of review history
 * (useful for external analysis or for grading/verification purposes).
 */
class FileManager {

    private static final String DATA_FILE = "tracker_data.ser";
    private static final String CSV_LOG_FILE = "review_history.csv";

    void saveDeckManager(DeckManager manager) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(DATA_FILE))) {
            oos.writeObject(manager);
            System.out.println("[System] Tracker state saved successfully to " + DATA_FILE);
        } catch (IOException e) {
            System.out.println("[Error] Could not save tracker state: " + e.getMessage());
        }
    }

    DeckManager loadDeckManager() {
        File file = new File(DATA_FILE);
        if (!file.exists()) {
            return null;
        }
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            return (DeckManager) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("[Error] Could not load saved state, starting fresh: " + e.getMessage());
            return null;
        }
    }

    void exportHistoryToCsv(DeckManager manager) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CSV_LOG_FILE))) {
            writer.println("TaskID,TaskName,Category,ReviewDate,QualityRating,ResultingIntervalDays");
            for (Task task : manager.getAllTasks()) {
                for (ReviewRecord record : task.getHistory()) {
                    writer.printf("%d,%s,%s,%s,%d,%d%n",
                            task.getId(),
                            escapeCsv(task.getName()),
                            task.getCategory(),
                            record.getReviewDate(),
                            record.getQualityRating(),
                            record.getResultingIntervalDays());
                }
            }
            System.out.println("[System] Review history exported to " + CSV_LOG_FILE);
        } catch (IOException e) {
            System.out.println("[Error] Could not export CSV: " + e.getMessage());
        }
    }

    private String escapeCsv(String value) {
        if (value.contains(",")) {
            return "\"" + value + "\"";
        }
        return value;
    }
}

/**
 * Pre-populates a fresh DeckManager with the four required seed entries
 * so the application is immediately demonstrable on first run.
 */
final class SeedDataInitializer {

    private SeedDataInitializer() {
        // Utility class; no instances.
    }

    static void populate(DeckManager manager) {
        SkillTask dsaSkill = new SkillTask(
                "Java DSA Problem Solving",
                "Daily practice of Data Structures & Algorithms problems in Java "
                        + "to build competitive programming and interview readiness.",
                Category.TECHNICAL_SKILL,
                "Intermediate"
        );

        SkillTask harmonicaSkill = new SkillTask(
                "Harmonica Practice",
                "Practicing scales, note bending, and simple melodies on a "
                        + "diatonic harmonica.",
                Category.MUSICAL_SKILL,
                "Beginner"
        );

        SkillTask cursiveSkill = new SkillTask(
                "Continuous Cursive Handwriting",
                "Practicing continuous, joined-up cursive handwriting for "
                        + "improved speed and legibility.",
                Category.HANDWRITING,
                "Beginner"
        );

        HabitTask calisthenicsHabit = new HabitTask(
                "Calisthenics (Pull-up progression)",
                "Progressive bodyweight training routine focused on building "
                        + "toward a strict, unassisted pull-up.",
                Category.PHYSICAL_FITNESS
        );

        manager.addTask(dsaSkill);
        manager.addTask(harmonicaSkill);
        manager.addTask(cursiveSkill);
        manager.addTask(calisthenicsHabit);
    }
}
