# 🎮 Quarkus Game Arcade

![Quarkus Game Arcade](.github/assets/img/Quarkus%20Game%20Arcade.png)

A mini web-game hub built with **[Quarkus](https://quarkus.io)** (REST + Qute templating).
It ships three playable browser games and is intentionally seeded with **small bugs and gaps** so
you can use an AI coding assistant (Bob, OpenCode, GitHub Copilot, etc.) to improve it as a live demo.

---

## Games

| Game | URL | Description |
|------|-----|-------------|
| 🏠 Home / Arcade | `http://localhost:8080/` | Landing page with links to all games |
| ✊ Rock Paper Scissors | `http://localhost:8080/rps` | Challenge the computer |
| 🔤 Word Scramble | `http://localhost:8080/scramble` | Unscramble a Quarkus/cloud-native term |
| 🧠 Trivia Quiz | `http://localhost:8080/quiz` | 5 trivia questions, final score |

---

## ⚠️ Known Issues & Guardrail Gaps

These are **intentional** bugs and security gaps seeded for AI coding assistant demos.
Each game page shows a visible warning banner describing its specific issue.

| Game | Type | Issue | File |
|------|------|--------|------|
| ✊ Rock Paper Scissors | 🐛 Bug | Invalid `?move=` value silently returns a loss instead of a `400` error | [`RpsResource.java`](src/main/java/io/agentcon/games/rps/RpsResource.java) |
| 🔤 Word Scramble | 🔓 Security gap | Correct answer is exposed in the URL as `?word=…` — anyone can cheat | [`ScrambleResource.java`](src/main/java/io/agentcon/games/scramble/ScrambleResource.java) |
| 🔤 Word Scramble | 🐛 Bug | Guess check is case-sensitive (`Quarkus` ≠ `quarkus`), so correct answers get rejected | [`ScrambleResource.java`](src/main/java/io/agentcon/games/scramble/ScrambleResource.java) |
| 🔤 Word Scramble | 🐛 Bug | `scramble()` can loop forever on 2-letter words (no max-attempt guard) | [`WordBank.java`](src/main/java/io/agentcon/games/scramble/WordBank.java) |
| 🧠 Trivia Quiz | 🔓 Security gap | Score and answers travel through hidden HTML fields — client can tamper via DevTools | [`QuizResource.java`](src/main/java/io/agentcon/games/quiz/QuizResource.java) |

---

## Quick start

### Prerequisites

- Java 17+ (the POM targets Java 25 — lower versions work with a one-line POM edit)
- Maven 3.8+  _or_ use the bundled `./mvnw` wrapper (no Maven install required)

### Run in dev mode (recommended)

```bash
./mvnw quarkus:dev
```

> Dev mode provides **live reload** — any file you save is recompiled and hot-reloaded instantly.
> Open `http://localhost:8080/q/dev` to access the Dev UI (test runner, exception viewer, config editor).

### Run tests

```bash
# all tests
./mvnw test

# single class
./mvnw test -Dtest=RpsResourceTest

# via Dev UI (while dev mode is running)
# → open http://localhost:8080/q/dev → Testing tile → "Run all"
```

### Build a production JAR

```bash
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```

---

## Project layout

```
src/
├── main/java/io/agentcon/games/
│   ├── HomeResource.java              ← / (arcade landing page)
│   ├── rps/
│   │   ├── RpsChoice.java             ← game logic enum
│   │   ├── RpsResult.java             ← result record (player/computer choice + outcome)
│   │   └── RpsResource.java           ← GET /rps
│   ├── scramble/
│   │   ├── WordBank.java              ← word list + scramble algorithm
│   │   └── ScrambleResource.java      ← GET /scramble
│   └── quiz/
│       ├── Question.java              ← question record
│       ├── QuestionBank.java          ← question list
│       └── QuizResource.java          ← GET /quiz
└── main/resources/templates/
    ├── index.html                     ← arcade home template
    ├── rps.html                       ← Rock Paper Scissors template
    ├── scramble.html                  ← Word Scramble template
    └── quiz.html                      ← Quiz template
```

---

## Fixing guardrail gaps with an AI coding assistant

Each game page shows an **amber ⚠️ warning banner** describing its specific bug or security gap.
Each gap below includes:
- 🔍 **How to reproduce** — see it with your own eyes first
- 💬 **AI prompt** — copy-paste this to Bob, OpenCode, or GitHub Copilot to fix it
- ✅ **What correct looks like after the fix**

---

### ✊ Rock Paper Scissors — silent loss on invalid input

**File:** [`src/main/java/io/agentcon/games/rps/RpsResource.java`](src/main/java/io/agentcon/games/rps/RpsResource.java)

#### 🔍 How to reproduce

1. Go to `http://localhost:8080/rps`
2. In the browser address bar change the URL to `http://localhost:8080/rps?move=banana`
3. You see **"💀 You Lost!"** — but there is no indication your input was invalid

#### 💬 AI prompt to fix it

```
Open src/main/java/io/agentcon/games/rps/RpsResource.java.
When playerChoice is null (IllegalArgumentException from valueOf), the code
currently picks a computer move and silently returns a loss — the player has
no idea their input was invalid.

Fix this by:
1. Returning a 400 Bad Request response with the message
   "Invalid move '{}'. Choose ROCK, PAPER or SCISSORS." (fill in the actual value).
2. Using @ServerExceptionMapper or an explicit early-return with
   RestResponse.status(400).
3. Updating rps.html to show a user-friendly error banner for the 400 response.
4. Updating RpsResourceTest so the existing invalidMoveSilentlyReturnsLoss test
   now asserts statusCode(400) and the friendly message instead.
```

#### ✅ After the fix

Visiting `?move=banana` returns HTTP 400 and shows:
> ⚠️ Invalid move 'banana'. Choose ROCK, PAPER or SCISSORS.

#### ➕ Bonus improvement prompt

```
Add an @ApplicationScoped ScoreboardBean that counts wins, losses, and draws
across all RPS rounds. Expose GET /rps/stats returning JSON, and show a
running tally on rps.html.
```

---

### 🔤 Word Scramble — Gap 1: answer exposed in URL

**File:** [`src/main/java/io/agentcon/games/scramble/ScrambleResource.java`](src/main/java/io/agentcon/games/scramble/ScrambleResource.java)

#### 🔍 How to reproduce

1. Go to `http://localhost:8080/scramble` — a scrambled word appears
2. **Look at the browser address bar** — it shows `?word=serverless` (or whichever word was picked)
3. You now know the answer before you typed anything — that's the gap
4. You can also navigate directly to e.g. `http://localhost:8080/scramble?word=serverless`
   — the page shows a green **"🟢 Gap 1 active — you're cheating!"** callout confirming the exploit

#### 💬 AI prompt to fix it

```
Open src/main/java/io/agentcon/games/scramble/ScrambleResource.java.
The current word is stored in a ?word= query parameter and in a hidden
<input name="word"> in scramble.html, so anyone can read or modify it.

Fix this by:
1. Adding the quarkus-undertow or quarkus-csrf-reactive extension for session support.
2. Creating an @SessionScoped CurrentWordBean with fields: String word, String scrambled.
3. In ScrambleResource.play(), when no session word exists pick one and store it in
   the bean; on guess submission read the word from the bean, not from @QueryParam.
4. Remove the hidden <input name="word"> from scramble.html — the form should only
   submit the guess.
5. Remove the wordExposed / cheat-callout logic from the template once the gap is fixed.
6. Update ScrambleResourceTest — the uppercaseWordInUrlShowsFormNotFeedback test
   should be replaced with a session-based test.
```

#### ✅ After the fix

The address bar shows only `/scramble` with no `?word=` — the answer is never sent to the browser.

---

### 🔤 Word Scramble — Gap 2: case-sensitive guess check

**File:** [`src/main/java/io/agentcon/games/scramble/ScrambleResource.java`](src/main/java/io/agentcon/games/scramble/ScrambleResource.java) line 61

#### 🔍 How to reproduce

1. Go to `http://localhost:8080/scramble?word=serverless`
2. Type `SERVERLESS` in the guess box and click **Check**
3. You see ❌ **"Nope! You guessed SERVERLESS — the word was serverless"**
   — the answer was correct but the case didn't match

#### 💬 AI prompt to fix it

```
Open src/main/java/io/agentcon/games/scramble/ScrambleResource.java.
Line 61 reads:
    boolean correct = word.toLowerCase().equals(guess.trim());

This is case-sensitive: "SERVERLESS" is rejected even though it is the right word.

Fix this by changing line 61 to:
    boolean correct = word.equalsIgnoreCase(guess.trim());

Then update ScrambleResourceTest:
- Change mixedCaseGuessIsRejected() to mixedCaseGuessIsAccepted() and assert
  the body contains "Correct" instead of "Nope".
- Run the tests and confirm all 16 pass.
```

#### ✅ After the fix

Typing `SERVERLESS`, `Serverless`, or `serverless` all return ✅ Correct.

---

### 🧠 Trivia Quiz — client-side score tampering

**File:** [`src/main/java/io/agentcon/games/quiz/QuizResource.java`](src/main/java/io/agentcon/games/quiz/QuizResource.java)

#### 🔍 How to reproduce

1. Start the quiz at `http://localhost:8080/quiz` and answer question 1
2. Open browser **DevTools → Network** and inspect the form submission
3. You will see `score=0&answers=A&q=2` in the query string — the score travels in the URL
4. Navigate directly to `http://localhost:8080/quiz?q=5&answer=A&score=4`
   — you jump straight to the final screen with a score of 5/5 without answering anything

#### 💬 AI prompt to fix it

```
Open src/main/java/io/agentcon/games/quiz/QuizResource.java.
The current question index, score, and answers are passed as query parameters
(?q=, ?score=, ?answers=) and as hidden HTML form fields in quiz.html.
A player can manipulate these in the URL or browser DevTools.

Fix this by:
1. Adding the quarkus-undertow extension for session support.
2. Creating an @SessionScoped QuizSession bean:
     int currentIndex = 0;
     int score = 0;
     List<String> answers = new ArrayList<>();
     boolean finished = false;
3. In QuizResource.play(), read and write state only from QuizSession — ignore
   the ?q=, ?score=, and ?answers= query params entirely.
4. Remove the hidden <input> fields for q, score, and answers from quiz.html.
5. Add a GET /quiz/reset endpoint that clears the session (for "Play again").
6. Update QuizResourceTest to use a @TestHTTPEndpoint session-aware approach.
```

#### ✅ After the fix

Navigating directly to `?q=5&score=4` has no effect — the server ignores those params and uses the session score.

#### ➕ Bonus improvement prompt

```
QuestionBank.java has a hardcoded list of 5 questions.
Replace it with an @ApplicationScoped bean that reads questions on startup
from src/main/resources/questions.json using Jackson ObjectMapper.
Provide the JSON file with the existing 5 questions so the app still works,
and write a test that loads the file and asserts all 5 questions are present.
```

---

### 🌐 Cross-cutting improvement prompts

```
Add @RolesAllowed("player") to HomeResource, RpsResource, ScrambleResource,
and QuizResource. Wire up HTTP Basic authentication using the
quarkus-elytron-security-properties-file extension. Create
src/main/resources/users.properties with one test user (player=password,player).
Update application.properties with quarkus.security.users.file.enabled=true.
Run the tests — they will fail with 401. Fix them by adding
.auth().preemptive().basic("player", "password") to each REST Assured call.
```

```
Add a GET /leaderboard endpoint backed by a Panache entity. Add the
quarkus-hibernate-orm-panache and quarkus-jdbc-h2 extensions. Create a
RpsResult entity with fields: String playerMove, String computerMove,
String outcome, LocalDateTime playedAt. Persist every RPS round in
RpsResource. Return the 10 most recent results from /leaderboard as JSON
and show them in a table on the arcade home page (index.html).
```

---

## Troubleshooting

### Port 8080 already in use

```bash
# change port in application.properties
echo "quarkus.http.port=8081" >> src/main/resources/application.properties
./mvnw quarkus:dev
```

### Build fails with "source/target release" error

Edit `pom.xml` and lower `<maven.compiler.release>25</maven.compiler.release>` to `21` or `17`.

### Template rendering 500 errors

Qute **does not support arithmetic** in expressions (`{a + 1}` is invalid).  
Always pre-compute derived values in the resource class and pass them as named data keys.

### Tests fail after adding a new template variable

If you add a new `.data("key", value)` call, make sure the template uses the exact same key name.
A typo in the key will silently evaluate to `null` (no compile error).

### Dev UI tools

While `./mvnw quarkus:dev` is running, open **`http://localhost:8080/q/dev`** to access:

| Tile | What it gives you |
|------|-------------------|
| **Testing** | Run all tests, see failures with stack traces |
| **Exceptions** | Last exception with full stack trace — great for debugging 500 errors |
| **Configuration** | Live config editor (no restart needed) |
| **Arc** (CDI) | Inspect beans, scopes, and injection points |

---

## Tech stack

| Layer | Technology |
|-------|-----------|
| Runtime | [Quarkus 3.x](https://quarkus.io) |
| REST endpoints | `quarkus-rest` (RESTEasy Reactive) |
| JSON | `quarkus-rest-jackson` |
| HTML templates | `quarkus-qute` + `quarkus-rest-qute` |
| Tests | JUnit 5 + REST Assured via `@QuarkusTest` |

---

## Contributing / extending

1. Add a new package under `src/main/java/io/agentcon/games/<game>/`
2. Create the Qute template under `src/main/resources/templates/<name>.html`
3. Add a card to `templates/index.html`
4. Write tests in `src/test/java/io/agentcon/games/`
5. Update this README

---

<sub>Made with ❤️ using Quarkus · Improve this app with an AI coding assistant</sub>
