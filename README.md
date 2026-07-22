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

## Improving with an AI coding assistant

The code is intentionally seeded with **documented bugs, security gaps, and improvement hooks**.
In-game warning banners tell you exactly what is wrong on each page.
Below are copy-paste prompts you can give to any AI coding assistant (Bob, OpenCode, GitHub Copilot…)
to fix or improve each issue.

---

### ✊ Rock Paper Scissors

#### 🐛 Fix: invalid move silently returns a loss

```
In RpsResource.java the catch block for IllegalArgumentException just renders
an "invalid" result page instead of rejecting the request properly.
Add a @ServerExceptionMapper (or an explicit check before valueOf) that returns
a 400 Bad Request with the message "Invalid move. Choose ROCK, PAPER or SCISSORS."
and update the template to show a proper error banner.
```

#### ➕ Improve: win/loss streak counter

```
Add an @ApplicationScoped ScoreboardBean that tracks total wins, losses, and draws
across all Rock Paper Scissors rounds in memory.
Expose a GET /rps/stats endpoint that returns the counts as JSON,
and show a small scoreboard on the rps.html template.
```

---

### 🔤 Word Scramble

#### 🔓 Fix: answer exposed in URL

```
ScrambleResource passes the correct word as a plain ?word= query parameter,
so anyone can read the answer in the browser address bar.
Move the current word into an @SessionScoped bean (add quarkus-undertow-websockets
or use a signed cookie via a HMAC helper) so the answer never leaves the server.
Remove the hidden <input name="word"> from scramble.html.
```

#### 🐛 Fix: case-sensitive guess check

```
In ScrambleResource.java the line `word.equals(guess)` is case-sensitive,
so a player typing "Quarkus" instead of "quarkus" is told they are wrong.
Change it to word.equalsIgnoreCase(guess) and add a test in ScrambleResourceTest
that submits a mixed-case guess and expects a "Correct" response.
```

#### 🐛 Fix: infinite loop on short words

```
The scramble() method in WordBank.java loops until the shuffled string differs
from the original. For 2-letter words this can loop forever.
Add a maxAttempts counter (e.g. 20) and return the original word unchanged
if no distinct scramble is found within that limit.
Add a unit test that calls scramble("ab") 100 times and asserts it always returns.
```

---

### 🧠 Trivia Quiz

#### 🔓 Fix: client-side score tampering

```
QuizResource passes `score` and `answers` as hidden HTML form fields, which
a player can edit in browser DevTools to inflate their score.
Introduce an @SessionScoped QuizSession bean that stores the current question
index, accumulated score, and submitted answers on the server side.
Remove the hidden fields from quiz.html and read state from the session bean
in QuizResource instead of from query parameters.
```

#### ➕ Improve: load questions from JSON

```
QuestionBank.java has a hardcoded List of questions.
Replace it with a @ApplicationScoped bean that reads questions from
src/main/resources/questions.json on startup using Jackson ObjectMapper.
Add a sample questions.json file with the existing 5 questions so the app
still works out of the box, and write a test that verifies all questions load.
```

---

### 🌐 Cross-cutting improvements

```
Add @RolesAllowed("player") to all three game resources and wire up HTTP Basic
authentication using the quarkus-elytron-security-properties-file extension.
Create a src/main/resources/users.properties with a single test user.
Update the README with login instructions.
```

```
Add a GET /leaderboard endpoint backed by a Panache entity (add quarkus-hibernate-orm-panache
and quarkus-jdbc-h2 extensions) that persists RPS results with player name and outcome.
Show the top 10 wins on the arcade home page.
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
