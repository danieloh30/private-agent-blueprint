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

## Quick start

### Prerequisites

- Java 17+ (the POM targets Java 25 — lower versions work with a one-line POM edit)
- Maven 3.8+  _or_ use the bundled `./mvnw` wrapper (no Maven install required)

### Run in dev mode (recommended)

```bash
cd quarkus-games
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

The code is intentionally seeded with **documented bugs, permission gaps, and improvement hooks**
(look for `AI IMPROVEMENT IDEAS`, `KNOWN BUG`, `KNOWN ISSUE`, `PERMISSION GAP`, and
`GUARDRAIL GAP` comments throughout the source).

### Common prompts to try

```
Fix the input-validation bug in RpsResource — invalid moves currently silently
return a loss instead of a 400 error.
```

```
The Word Scramble game exposes the answer in the URL query parameter `word`.
Move the current word to server-side session state so players cannot cheat.
```

```
The scramble() method in WordBank can spin forever for 2-letter words.
Add a max-attempt guard and return the original word if no scramble is found.
```

```
Guess checking in ScrambleResource is case-sensitive, so "Quarkus" ≠ "quarkus".
Fix it to be case-insensitive.
```

```
Add a leaderboard endpoint GET /leaderboard backed by a Panache entity that
records wins, losses, and draws for Rock Paper Scissors.
```

```
Add @RolesAllowed("player") to all game endpoints and wire up Basic Auth so
only authenticated users can play.
```

```
Load quiz questions from src/main/resources/questions.json instead of the
hardcoded list in QuestionBank.java.
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
