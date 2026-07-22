package io.agentcon.games.quiz;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

/**
 * In-memory question bank.
 *
 * Questions are about Quarkus, cloud-native, and general programming —
 * fitting for the AgentCon / MCPCon audience.
 *
 * AI IMPROVEMENT IDEAS:
 * - Load questions from a JSON file (src/main/resources/questions.json)
 *   to allow easy updates without recompiling.
 * - Randomise the order of questions each game round.
 * - Track question statistics (most-missed questions) in a database.
 */
@ApplicationScoped
public class QuestionBank {

    public static final List<Question> ALL = List.of(
            new Question(
                    "What build tool does Quarkus use for dependency injection?",
                    "A",
                    "CDI (Contexts and Dependency Injection)",
                    "Spring Framework",
                    "Guice",
                    "Dagger"),
            new Question(
                    "Which protocol does Quarkus use for reactive messaging by default?",
                    "B",
                    "gRPC",
                    "SmallRye Reactive Messaging",
                    "SOAP",
                    "GraphQL"),
            new Question(
                    "What is the name of Quarkus's template engine?",
                    "C",
                    "Thymeleaf",
                    "FreeMarker",
                    "Qute",
                    "Mustache"),
            new Question(
                    "Which container orchestration platform is Quarkus optimised for?",
                    "D",
                    "Docker Swarm",
                    "Nomad",
                    "Mesos",
                    "Kubernetes"),
            new Question(
                    "What does MCP stand for in the context of AI tooling?",
                    "A",
                    "Model Context Protocol",
                    "Machine Control Pipeline",
                    "Multi-Cloud Platform",
                    "Message Communication Protocol")
    );
}
