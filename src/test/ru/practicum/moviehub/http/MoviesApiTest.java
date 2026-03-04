package ru.practicum.moviehub.http;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.google.gson.Gson;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Year;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MoviesApiTest {
    private static final String BASE = "http://localhost:8080";
    private static MoviesServer server;
    private static HttpClient client;
    private static MoviesStore store;
    private static final Gson gson = new Gson();

    @BeforeAll
    static void beforeAll() {
        store = new MoviesStore();
        server = new MoviesServer(store, 8080);
        server.start();
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @BeforeEach
    void beforeEach() {
        store.clear();
    }

    @AfterAll
    static void afterAll() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        String body = resp.body().trim();
        assertTrue(body.startsWith("[") && body.endsWith("]"),
                "Ожидается JSON-массив");
    }

    @Test
    void getMovies_whenNotEmpty_returnsMoviesList() throws Exception {

        store.add(new Movie(1, "Terminator", 1984));
        store.add(new Movie(2, "The Lord of the Rings", 2001));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());

        String body = resp.body();
        assertTrue(body.contains("Terminator"));
        assertTrue(body.contains("The Lord of the Rings"));
    }

    @Test
    void postMovie_withCorrectData_addsMovie() throws Exception {

        String body = "{ \"title\": \"Terminator\", \"year\": 1984 }";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = client.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        assertEquals(201, resp.statusCode());
    }

    @Test
    void postMovie_withEmptyTitle_returns422() throws Exception {
        String body = "{ \"title\": \"\", \"year\": 1984 }";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = client.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        assertEquals(422, resp.statusCode());
        assertTrue(resp.body().contains("Отсутствует название"));
    }

    @Test
    void postMovie_LongTitle_returns422() throws Exception {
        String titleL = "iiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiii";

        String body = "{ \"title\": \"" + titleL + "\", \"year\": 1984 }";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = client.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        assertEquals(422, resp.statusCode());
        assertTrue(resp.body().contains("Название не должно превышать 100 символов"));
    }

    @Test
    void postMovie_NotCorrectYear_returns422() throws Exception {
        String body = "{ \"title\": \"Terminator\", \"year\": 1489 }";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = client.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        assertEquals(422, resp.statusCode());
        assertTrue(resp.body().contains(
                "Год должен быть между 1888 и " + (Year.now().getValue() + 1)
        ));
    }

    @Test
    void postMovie_NotCorrectContentType_returns415() throws Exception {
        String body = "{ \"title\": \"Terminator\", \"year\": 1984 }";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "text/html")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = client.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        assertEquals(415, resp.statusCode());
    }

    @Test
    void getMovieById_ReturnsMovie() throws Exception {

        store.add(new Movie(1, "Terminator", 1984));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies" + "/1"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());

        String contentType =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentType);

        assertTrue(resp.body().contains("\"id\":1"));
        assertTrue(resp.body().contains("Terminator"));
        assertTrue(resp.body().contains("\"year\":1984"));
    }

    @Test
    void getMovieById_whenNotFound_returns404() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies" + "/100"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, resp.statusCode());

        assertTrue(resp.body().contains("Фильм не найден"));
    }

    @Test
    void deleteMovieById_DeletesMovie() throws Exception {

        store.add(new Movie(1, "Terminator", 1984));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies" + "/1"))
                .DELETE()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(204, resp.statusCode());

        assertTrue(store.isEmpty());
    }

    @Test
    void deleteMovieById_whenIdIsNotNumber_returns400() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies" + "/q"))
                .DELETE()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode());

        assertTrue(resp.body().contains("Некорректный ID"));
    }

    @Test
    void deleteMovieById_whenNotFound_returns404() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies" + "/2"))
                .DELETE()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, resp.statusCode());

        assertTrue(resp.body().contains("Фильм не найден"));
    }

    @Test
    void getMoviesByYear_whenNoMoviesByYear_returnsEmptyList() throws Exception {
        store.add(new Movie(1, "Terminator", 1984));
        store.add(new Movie(2, "The Lord of the Rings", 2001));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies" + "?year=2025"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());

        String body = resp.body().trim();
        assertEquals("[]", body);
    }

    @Test
    void getMoviesByYear_whenYearIsNotNumber_returns400() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies" + "?year=q"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode());
        assertTrue(
                resp.body().contains("Некорректный параметр запроса — 'year'"));
    }

    @Test
    void getMoviesByYear_whenMoviesExist_returnsFilteredList() throws Exception {

        store.add(new Movie(1, "Blade Runner", 1984));
        store.add(new Movie(2, "Terminator", 1984));
        store.add(new Movie(3, "The Lord of the Rings", 2001));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies" + "?year=1984"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());

        String contentType =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentType);

        List<Movie> movies = gson.fromJson(
                resp.body(),
                new ListOfMoviesTypeToken().getType()
        );
        System.out.println(resp.body());
        assertEquals(2, movies.size());

        for (Movie movie : movies) {
            assertEquals(1984, movie.getYear());
        }
    }
}