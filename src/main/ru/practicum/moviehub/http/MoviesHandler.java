package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MoviesHandler extends BaseHttpHandler {

    private final MoviesStore store;
    private final Gson gson = new Gson();

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        String[] parts = path.split("/");
        switch (method) {

            case "GET":
                String query = ex.getRequestURI().getQuery();

                if (parts.length == 2 && parts[1].equals("movies")) {

                    if (query == null) {
                        handleGetAll(ex);
                    } else {
                        String[] queryParts = query.split("=");
                        if (queryParts.length == 2 && queryParts[0].equals("year")) {
                            handleGetByYear(ex, queryParts[1]);
                        } else {
                            sendError(ex, 400, "Некорректный параметр запроса — 'year'");
                        }
                    }
                } else if (parts.length == 3 && parts[1].equals("movies")) {

                    handleGetById(ex, parts[2]);
                } else {
                    sendError(ex, 404, "Не найдено");
                }
                break;

            case "POST":
                if (path.equals("/movies")) {
                    handlePost(ex);
                } else {
                    sendError(ex, 404, "Не найдено");
                }
                break;

            case "DELETE":
                if (parts.length == 3 && parts[1].equals("movies")) {
                    handleDeleteById(ex, parts[2]);
                } else {
                    sendError(ex, 404, "Не найдено");
                }
                break;

            default:
                sendError(ex, 405, "Method Not Allowed");
                break;
        }
    }

    private void handleGetAll(HttpExchange ex) throws IOException {
        Collection<Movie> movies = store.findAll();
        String json = gson.toJson(movies);
        sendJson(ex, 200, json);
    }

    private void handleGetById(HttpExchange ex, String movieIdString) throws IOException {
        Movie movie = getMovieById(ex, movieIdString);
        if (movie == null)
            return;

        String json = gson.toJson(movie);
        sendJson(ex, 200, json);
    }

    private void handleGetByYear(HttpExchange ex, String movieYearString) throws IOException {
        int year;
        try {
            year = Integer.parseInt(movieYearString);
        } catch (NumberFormatException e) {
            sendError(ex, 400, "Некорректный параметр запроса — 'year'");
            return;
        }

        List<Movie> filteredByYear = new ArrayList<>();
        for (Movie movie : store.findAll()) {
            if (movie.getYear() == year) {
                filteredByYear.add(movie);
            }
        }

        String json = gson.toJson(filteredByYear);
        sendJson(ex, 200, json);
    }

    private void handlePost(HttpExchange ex) throws IOException {
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.startsWith("application/json")) {
            sendError(ex, 415, "Unsupported Media Type");
            return;
        }

        Movie movieRequest;
        try (InputStream inputStream = ex.getRequestBody()) {
            String body = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            movieRequest = gson.fromJson(body, Movie.class);
        } catch (Exception e) {
            sendError(ex, 400, "Некорректный JSON");
            return;
        }


        ArrayList<String> validationErrors = validateMovie(movieRequest);
        if (!validationErrors.isEmpty()) {
            sendJson(ex, 422, gson.toJson(new ErrorResponse("Ошибка валидации",validationErrors)));
            return;
        }


        int newId = 1;
        Collection<Movie> allMovies = store.findAll();
        for (Movie movie : allMovies) {
            if (movie.getId() >= newId) {
                newId = movie.getId() + 1;
            }
        }

        Movie movie = new Movie(newId, movieRequest.getTitle(), movieRequest.getYear());
        store.add(movie);

        String json = gson.toJson(movie);
        sendJson(ex, 201, json);
    }

    private void handleDeleteById(HttpExchange ex, String movieIdString) throws IOException {
        Movie movie = getMovieById(ex, movieIdString);
        if (movie == null) return;

        store.remove(movie.getId());
        sendNoContent(ex);
    }


    private ArrayList<String> validateMovie(Movie movie) {
        ArrayList<String> validationErrors = new ArrayList<>();
        if (movie.getTitle() == null || movie.getTitle().isBlank()) {
            validationErrors.add("Отсутствует название");
        }
        if (movie.getTitle().length() > 100) {
            validationErrors.add("Название не должно превышать 100 символов");
        }
        int currentYear = Year.now().getValue();
        if (movie.getYear() < 1888 || movie.getYear() > currentYear + 1) {
            validationErrors.add("Год должен быть между 1888 и " + (currentYear + 1));
        }
        return validationErrors;
    }

    private Movie getMovieById(HttpExchange ex, String movieIdString) throws IOException {
        int id;
        try {
            id = Integer.parseInt(movieIdString);
        } catch (NumberFormatException e) {
            sendError(ex, 400, "Некорректный ID");
            return null;
        }

        Movie movie = store.findById(id);
        if (movie == null) {
            sendError(ex, 404, "Фильм не найден");
            return null;
        }

        return movie;
    }
}