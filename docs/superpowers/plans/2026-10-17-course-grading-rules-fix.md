# Course Grading Rules Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix laboratory score calculation, control point score handling, tie-breaking in leaderboard sorting, and academic grading logic to strictly comply with the official course regulations.

**Architecture:**
- Introduce a static lab specification catalog (`LabSpec`) for the 8 laboratory works with individual `maxRawScore` and `maxDefenseScore` (summing to 143.0 total).
- Refactor `LabScoreService.recordLab` to calculate late penalty across total raw score, enforce the 60% cap if defense is not passed, round score to 2 decimal places with `RoundingMode.HALF_UP`, and properly evaluate acceptance.
- Support `Double` scores for control point attempts in `QuizAttempt` and `LeaderboardService`, capping individual KTs at 25.0 and total KT score at 100.0.
- Update `LeaderboardService` comparator to break ties on equal CTF scores and equal submission timestamps by ascending count of incorrect attempts.
- Fix `GradingService.calculateGrade` to strictly take percentage values `[0.0, 100.0]`, removing the buggy auto-multiply logic for values <= 1.0, and add `calculateFinalGradeWithExam` supporting voluntary exams.

**Tech Stack:** Java 17/21, Spring Boot 3, Spring Data JPA, PostgreSQL, Flyway, JUnit 5, AssertJ, Mockito.

## Global Constraints

- Preserve all existing public API methods and endpoints.
- Ensure all calculations follow official regulations:
  - Total lab points = 143.0 (Labs 1-8: 10, 12, 12, 15, 24, 28, 20, 22).
  - Individual lab score: `calculatedScore = (rawScore + defenseScore) * penaltyCoefficient`.
  - Defense threshold: `defenseScore >= 0.50 * maxDefenseScore`. If not met, cap `calculatedScore <= 0.60 * maxTotal`.
  - Acceptance: `rawScore >= 0.40 * maxRawScore` AND `totalRaw >= 0.40 * maxTotal` AND `defenseScore >= 0.50 * maxDefenseScore`.
  - Lab total sum rounded to 2 decimal places (`HALF_UP`).
  - Control points (KT) can have fractional scores (e.g. 17.5), capped at 25.0 each and 100.0 total.
  - CTF sorting tie-breaker: CTF score DESC -> last successful tx time ASC -> wrong attempts count ASC -> username ASC.
  - Grade calculation: strictly 0..100 percentages. `Math.max(courseGrade, examGrade)` for voluntary exam. If `!isEligible`, grade is `null`.

---

### Task 1: Database Migration & Entity Adjustments for Lab Scores and Quiz Attempts

**Files:**
- Modify: `backend/src/main/resources/db/migration/V15__add_test_type_and_student_labs.sql`
- Create: `backend/src/main/resources/db/migration/V16__alter_quiz_attempt_score_to_double.sql`
- Modify: `backend/src/main/java/com/arcx/ctfplatform/students/entity/lab/StudentLab.java`
- Modify: `backend/src/main/java/com/arcx/ctfplatform/tests/entity/QuizAttempt.java`
- Modify: `backend/src/main/java/com/arcx/ctfplatform/academic/service/AdminAnalyticsService.java:310`

**Interfaces:**
- Consumes: Existing DB schemas V1..V15.
- Produces: `StudentLab` without hardcoded 14.75/3.125 defaults; `QuizAttempt` supporting `Double` score and flexible builder/setter.

- [ ] **Step 1: Update V15 migration and create V16 migration**
Remove default 14.75 and 3.125 from `V15__add_test_type_and_student_labs.sql`, setting `DEFAULT 0`.
Create `V16__alter_quiz_attempt_score_to_double.sql`:
```sql
ALTER TABLE quiz_attempts ALTER COLUMN score TYPE DOUBLE PRECISION USING score::DOUBLE PRECISION;
```

- [ ] **Step 2: Update StudentLab and QuizAttempt entities**
In `StudentLab.java`, set `maxRawScore = 0.0` and `maxDefenseScore = 0.0` by default. Update `isAccepted()` to check:
```java
public boolean isAccepted() {
    if (this.isAccepted) {
        return true;
    }
    if (maxRawScore > 0 || maxDefenseScore > 0) {
        double maxTotal = maxRawScore + maxDefenseScore;
        double totalRaw = rawScore + defenseScore;
        boolean rawPassed = (maxRawScore <= 0 || rawScore >= 0.40 * maxRawScore) &&
                            (maxTotal <= 0 || totalRaw >= 0.40 * maxTotal);
        boolean defensePassed = (maxDefenseScore <= 0 || defenseScore >= 0.50 * maxDefenseScore);
        return rawPassed && defensePassed;
    }
    return false;
}
```
In `QuizAttempt.java`, change `score` field to `Double` with custom builder and setter accepting `Number` to preserve backward compatibility.
In `AdminAnalyticsService.java`, ensure `attempt.getScore()` is handled gracefully as `Number`/`Double`.

- [ ] **Step 3: Run `./gradlew compileJava` to verify compilation**
Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

---

### Task 2: LabScoreService Logic Updates (Catalog, Formula, 60% Limit, 2-Decimal Precision)

**Files:**
- Modify: `backend/src/main/java/com/arcx/ctfplatform/students/service/lab/LabScoreService.java`
- Test: `backend/src/test/java/com/arcx/ctfplatform/students/service/lab/LabScoreServiceTest.java`

**Interfaces:**
- Consumes: `StudentLabRepository`, `StudentRepository`, `LabScoreRepository`, `LabScoreHistoryRepository`.
- Produces: `LabScoreService.LAB_SPECS`, `LabScoreService.getLabSpec(int)`, updated `recordLab`, updated `getFinalLabScore`.

- [ ] **Step 1: Write failing unit tests for LabScoreService**
Add tests in `LabScoreServiceTest.java`:
1. Verify lab specs for all 8 labs match regulation values (sum = 143.0).
2. Verify 60% cap when defense is not passed: Lab 1 (`raw = 8.0, defense = 0.5`) -> `score = 6.00`, `isAccepted = false`.
3. Verify late penalty applies to both raw and defense (`(totalRaw) * penaltyCoefficient`).
4. Verify `getFinalLabScore` returns sum of `StudentLab` scores rounded to 2 decimal places.

- [ ] **Step 2: Run test to verify it fails**
Run: `./gradlew test --tests com.arcx.ctfplatform.students.service.lab.LabScoreServiceTest`
Expected: FAIL due to missing lab specs and old calculation logic.

- [ ] **Step 3: Implement lab specs catalog and new calculation in LabScoreService**
Add `LabSpec` record:
```java
public record LabSpec(int labNumber, double maxRawScore, double maxDefenseScore) {
    public double maxTotal() {
        return maxRawScore + maxDefenseScore;
    }
}
```
Define `LAB_SPECS` mapping 1..8:
1 -> (8.0, 2.0)
2 -> (10.0, 2.0)
3 -> (10.0, 2.0)
4 -> (12.0, 3.0)
5 -> (20.0, 4.0)
6 -> (24.0, 4.0)
7 -> (16.0, 4.0)
8 -> (18.0, 4.0)
Implement 1.2 calculation algorithm in `recordLab`:
```java
double maxTotal = maxRawScore + maxDefenseScore;
double totalRaw = rawScore + defenseScore;
double calculatedScore = totalRaw * penaltyCoefficient;

boolean defensePassed = (maxDefenseScore <= 0) || (defenseScore >= 0.50 * maxDefenseScore);
if (!defensePassed) {
    calculatedScore = Math.min(calculatedScore, 0.60 * maxTotal);
}
calculatedScore = roundToTwoDecimals(calculatedScore);

boolean rawPassed = (maxRawScore <= 0 || rawScore >= 0.40 * maxRawScore) &&
                    (maxTotal <= 0 || totalRaw >= 0.40 * maxTotal);
boolean accepted = rawPassed && defensePassed;
```
Update `getFinalLabScore`:
```java
List<StudentLab> labs = studentLabRepository.findAllByStudentId(studentId);
if (!labs.isEmpty()) {
    double sum = labs.stream().mapToDouble(StudentLab::getScore).sum();
    return roundToTwoDecimals(sum);
}
return labScoreRepository.findByStudentId(studentId)
        .map(s -> roundToTwoDecimals((double) s.getScore()))
        .orElse(0.0);
```

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew test --tests com.arcx.ctfplatform.students.service.lab.LabScoreServiceTest`
Expected: PASS

---

### Task 3: LeaderboardService Control Points and CTF Tie-Breaking

**Files:**
- Modify: `backend/src/main/java/com/arcx/ctfplatform/leaderboard/service/LeaderboardService.java`
- Test: `backend/src/test/java/com/arcx/ctfplatform/leaderboard/service/LeaderboardServiceTest.java`

**Interfaces:**
- Consumes: `QuizAttemptRepository`, `AttemptRepository`, `GradingService`, `LabScoreService`.
- Produces: Fractional KT score handling (up to 25.0 each, 100.0 total) and wrong attempts tie-breaker sorting.

- [ ] **Step 1: Write failing unit tests in LeaderboardServiceTest**
Add tests:
1. Fractional control point score (e.g. 17.5 points) correctly contributing to `bKt` and `kPercent`.
2. Tie-breaker sorting: students with equal CTF scores and equal timestamps are sorted by ascending wrong attempts count.

- [ ] **Step 2: Run test to verify it fails**
Run: `./gradlew test --tests com.arcx.ctfplatform.leaderboard.service.LeaderboardServiceTest`
Expected: FAIL

- [ ] **Step 3: Implement fractional KT and tie-breaker sorting**
In `LeaderboardService.java`:
- Change `ktScores` to `Map<UUID, Double>`.
- Read attempt score as `double`, cap at `25.0`, sum and cap at `100.0`.
- Count `wrongAttemptsCount` for each student from `studentAttempts.stream().filter(a -> !a.isCorrect()).count()`.
- In `entries.sort(...)`, after `timeCompare`, compare `studentWrongAttempts` using `Long.compare(w1, w2)`.

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew test --tests com.arcx.ctfplatform.leaderboard.service.LeaderboardServiceTest`
Expected: PASS

---

### Task 4: GradingService Bug Fix and Voluntary Exam Support

**Files:**
- Modify: `backend/src/main/java/com/arcx/ctfplatform/academic/service/GradingService.java`
- Test: `backend/src/test/java/com/arcx/ctfplatform/academic/service/GradingServiceTest.java`

**Interfaces:**
- Consumes: `GradingScaleRepository`.
- Produces: `calculateGrade(s, l, k)` strictly on 0..100 scale; `calculateFinalGradeWithExam(s, l, k, isEligible, examScore)`.

- [ ] **Step 1: Write failing unit tests in GradingServiceTest**
Add tests:
1. Low percentages: `L = 0.70, K = 0.0, C = 0.0, S = 0.35` -> grade 2 (not 3 or 5).
2. Boundary cases: `S = 69.99` -> 3, `S = 70.00` -> 4.
3. Honors threshold: `S = 86.00, K = 59.00` -> 4.
4. Voluntary exam:
   - `isEligible = false` -> `null`
   - `courseGrade = 3, examScore = 85 (grade 5)` -> returns 5
   - `courseGrade = 4, examScore = 60 (grade 3)` -> returns 4 (`Math.max`)
   - `courseGrade = 4, examScore = null` -> returns 4

- [ ] **Step 2: Run test to verify it fails**
Run: `./gradlew test --tests com.arcx.ctfplatform.academic.service.GradingServiceTest`
Expected: FAIL

- [ ] **Step 3: Implement fixes in GradingService**
- Remove the `if (s <= 1.0 && ...)` auto-multiplication block in `calculateGrade`.
- Add `calculateFinalGradeWithExam(double s, double l, double k, boolean isEligible, Integer examScore)`:
  - If `!isEligible`, return `null`.
  - Calculate `int courseGrade = calculateGrade(s, l, k)`.
  - If `examScore != null`: convert to examGrade (<50 -> 2, 50..69 -> 3, 70..84 -> 4, >=85 -> 5) and return `Math.max(courseGrade, examGrade)`.
  - Otherwise return `courseGrade`.

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew test --tests com.arcx.ctfplatform.academic.service.GradingServiceTest`
Expected: PASS

---

### Task 5: End-to-End Verification Across the Entire Test Suite

**Files:**
- All touched files.

- [ ] **Step 1: Run complete test suite**
Run: `./gradlew test`
Expected: ALL TESTS PASS

- [ ] **Step 2: Verify git status and clean up any temp artifacts**
Verify clean state and code formatting.
