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

    // Вспомогательные методы

    private static HttpResponse<String> getMovies() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static HttpResponse<String> getMovie(String id) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + id))
                .GET()
                .build();

        return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static HttpResponse<String> getMoviesByYear(String year) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=" + year))
                .GET()
                .build();

        return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static HttpResponse<String> postMovie(String body, String contentType) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }

        HttpRequest req = builder.build();

        return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static HttpResponse<String> deleteMovie(String id) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + id))
                .DELETE()
                .build();

        return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    // Тесты

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpResponse<String> resp = getMovies();

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

        HttpResponse<String> resp = getMovies();

        assertEquals(200, resp.statusCode());

        String body = resp.body();
        assertTrue(body.contains("Terminator"));
        assertTrue(body.contains("The Lord of the Rings"));
    }

    @Test
    void postMovie_withCorrectData_addsMovie() throws Exception {
        String body = "{ \"title\": \"Terminator\", \"year\": 1984 }";

        HttpResponse<String> resp = postMovie(body, "application/json");

        assertEquals(201, resp.statusCode());
    }

    @Test
    void postMovie_withEmptyTitle_returns422() throws Exception {
        String body = "{ \"title\": \"\", \"year\": 1984 }";

        HttpResponse<String> resp = postMovie(body, "application/json");

        assertEquals(422, resp.statusCode());
        assertTrue(resp.body().contains("Отсутствует название"));
    }

    @Test
    void postMovie_LongTitle_returns422() throws Exception {
        String titleL = "iiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiii";
        String body = "{ \"title\": \"" + titleL + "\", \"year\": 1984 }";

        HttpResponse<String> resp = postMovie(body, "application/json");

        assertEquals(422, resp.statusCode());
        assertTrue(resp.body().contains("Название не должно превышать 100 символов"));
    }

    @Test
    void postMovie_NotCorrectYear_returns422() throws Exception {
        String body = "{ \"title\": \"Terminator\", \"year\": 1489 }";

        HttpResponse<String> resp = postMovie(body, "application/json");

        assertEquals(422, resp.statusCode());
        assertTrue(resp.body().contains(
                "Год должен быть между 1888 и " + (Year.now().getValue() + 1)
        ));
    }

    @Test
    void postMovie_NotCorrectContentType_returns415() throws Exception {
        String body = "{ \"title\": \"Terminator\", \"year\": 1984 }";

        HttpResponse<String> resp = postMovie(body, "text/html");

        assertEquals(415, resp.statusCode());
    }

    @Test
    void getMovieById_ReturnsMovie() throws Exception {
        store.add(new Movie(1, "Terminator", 1984));

        HttpResponse<String> resp = getMovie("1");

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
        HttpResponse<String> resp = getMovie("100");

        assertEquals(404, resp.statusCode());
        assertTrue(resp.body().contains("Фильм не найден"));
    }

    @Test
    void deleteMovieById_DeletesMovie() throws Exception {
        store.add(new Movie(1, "Terminator", 1984));

        HttpResponse<String> resp = deleteMovie("1");

        assertEquals(204, resp.statusCode());
        assertTrue(store.isEmpty());
    }

    @Test
    void deleteMovieById_whenIdIsNotNumber_returns400() throws Exception {
        HttpResponse<String> resp = deleteMovie("q");

        assertEquals(400, resp.statusCode());
        assertTrue(resp.body().contains("Некорректный ID"));
    }

    @Test
    void deleteMovieById_whenNotFound_returns404() throws Exception {
        HttpResponse<String> resp = deleteMovie("2");

        assertEquals(404, resp.statusCode());
        assertTrue(resp.body().contains("Фильм не найден"));
    }

    @Test
    void getMoviesByYear_whenNoMoviesByYear_returnsEmptyList() throws Exception {
        store.add(new Movie(1, "Terminator", 1984));
        store.add(new Movie(2, "The Lord of the Rings", 2001));

        HttpResponse<String> resp = getMoviesByYear("2025");

        assertEquals(200, resp.statusCode());

        String body = resp.body().trim();
        assertEquals("[]", body);
    }

    @Test
    void getMoviesByYear_whenYearIsNotNumber_returns400() throws Exception {
        HttpResponse<String> resp = getMoviesByYear("q");

        assertEquals(400, resp.statusCode());
        assertTrue(resp.body().contains("Некорректный параметр запроса"));
    }

    @Test
    void getMoviesByYear_whenMoviesExist_returnsFilteredList() throws Exception {
        store.add(new Movie(1, "Blade Runner", 1984));
        store.add(new Movie(2, "Terminator", 1984));
        store.add(new Movie(3, "The Lord of the Rings", 2001));

        HttpResponse<String> resp = getMoviesByYear("1984");

        assertEquals(200, resp.statusCode());

        String contentType =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentType);

        List<Movie> movies = gson.fromJson(
                resp.body(),
                new ListOfMoviesTypeToken().getType()
        );

        assertEquals(2, movies.size());

        for (Movie movie : movies) {
            assertEquals(1984, movie.getYear());
        }
    }
}