package com.hackathon.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:slotmind-tests;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false",
        "slotmind.ai.enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BackendApplicationTests {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper mapper;

    @Test
    @Order(1)
    void healthAndSeedEndpointsAreAvailable() throws Exception {
        JsonNode health = json(get("/actuator/health"));
        assertEquals("UP", health.path("status").asString(""));
        assertEquals(5, json(get("/api/providers")).size());
        assertEquals(8, json(get("/api/slots")).size());
        assertEquals(4, json(get("/api/appointments")).size());
        assertEquals(0, json(get("/api/suggestions")).size());
    }

    @Test
    @Order(2)
    void manualSuggestionCanBeRejectedWithoutChangingTheBooking() throws Exception {
        HttpResponse<String> created = post("/api/appointments/APT-004/suggest", "{}");
        assertEquals(201, created.statusCode());
        JsonNode suggestion = json(created);
        assertEquals("PENDING", suggestion.path("status").asString(""));
        assertEquals("INITIAL", suggestion.path("triggerReason").asString(""));

        HttpResponse<String> rejected = patch("/api/suggestions/" + suggestion.path("id").asString(""), "{\"status\":\"REJECTED\"}");
        assertEquals(200, rejected.statusCode());
        assertEquals("REJECTED", json(rejected).path("status").asString(""));
        JsonNode appointment = appointment("APT-004");
        assertEquals("CONFIRMED", appointment.path("status").asString(""));
        assertEquals("SLT-006", appointment.path("slot").path("id").asString(""));
    }

    @Test
    @Order(3)
    void bookingUsesOnlyTheSelectedProvidersOpenSlot() throws Exception {
        HttpResponse<String> created = post("/api/appointments", """
                {"patientName":"Test Patient","type":"ROUTINE","providerId":"PRV-004","slotId":"SLT-005"}
                """);
        assertEquals(201, created.statusCode());
        String appointmentId = json(created).path("id").asString("");
        assertEquals("CONFIRMED", appointment(appointmentId).path("status").asString(""));

        HttpResponse<String> duplicate = post("/api/appointments", """
                {"patientName":"Second Patient","type":"ROUTINE","providerId":"PRV-004","slotId":"SLT-005"}
                """);
        JsonNode duplicateError = assertApiError(duplicate, 409, "/api/appointments");
        assertTrue(duplicateError.path("message").asString("").contains("slot"));

        JsonNode reopenError = assertApiError(patch("/api/slots/SLT-001/status", "{\"status\":\"OPEN\"}"), 409, "/api/slots/SLT-001/status");
        assertTrue(reopenError.path("message").asString("").contains("cannot be reopened"));
        assertEquals("BOOKED", slot("SLT-001").path("status").asString(""));

        HttpResponse<String> wrongProvider = post("/api/appointments", """
                {"patientName":"Mismatch Patient","type":"ROUTINE","providerId":"PRV-001","slotId":"SLT-007"}
                """);
        assertEquals(400, wrongProvider.statusCode());
        assertEquals("BOOKED", slot("SLT-005").path("status").asString(""));
    }

    @Test
    @Order(4)
    void providerOfflineQueuesAffectedAppointmentsAndAcceptanceIsAtomic() throws Exception {
        HttpResponse<String> update = patch("/api/providers/PRV-001/status", "{\"status\":\"OFFLINE\"}");
        assertEquals(204, update.statusCode());
        assertEquals(204, patch("/api/providers/PRV-001/status", "{\"status\":\"OFFLINE\"}").statusCode());

        JsonNode first = awaitSuggestion("APT-001");
        JsonNode second = awaitSuggestion("APT-002");
        assertEquals("PROVIDER_OFFLINE", first.path("suggestion").path("triggerReason").asString(""));
        assertEquals("PROVIDER_OFFLINE", second.path("suggestion").path("triggerReason").asString(""));
        assertEquals(2, json(get("/api/suggestions")).size(), "repeating the same status update must not enqueue duplicates");
        assertEquals("RESCHEDULE_PENDING", first.path("status").asString(""));
        assertEquals("RESCHEDULE_PENDING", second.path("status").asString(""));

        JsonNode firstSuggestion = first.path("suggestion");
        JsonNode secondSuggestion = second.path("suggestion");
        assertEquals(firstSuggestion.path("slotId").asString(""), secondSuggestion.path("slotId").asString(""),
                "both recommendations should exercise a concurrent contention for the same slot");

        var pool = Executors.newFixedThreadPool(2);
        var startTogether = new CountDownLatch(1);
        try {
            var acceptFirst = pool.submit(() -> { startTogether.await(); return patch("/api/suggestions/" + firstSuggestion.path("id").asString(""), "{\"status\":\"ACCEPTED\"}"); });
            var acceptSecond = pool.submit(() -> { startTogether.await(); return patch("/api/suggestions/" + secondSuggestion.path("id").asString(""), "{\"status\":\"ACCEPTED\"}"); });
            startTogether.countDown();
            HttpResponse<String> resultOne = acceptFirst.get(10, TimeUnit.SECONDS);
            HttpResponse<String> resultTwo = acceptSecond.get(10, TimeUnit.SECONDS);
            assertEquals(1, java.util.stream.Stream.of(resultOne, resultTwo).filter(response -> response.statusCode() == 200).count(),
                    () -> "Concurrent accept responses: " + resultOne.statusCode() + " " + resultOne.body() + " | " + resultTwo.statusCode() + " " + resultTwo.body());
            assertEquals(1, java.util.stream.Stream.of(resultOne, resultTwo).filter(response -> response.statusCode() == 409).count(),
                    () -> "Concurrent accept responses: " + resultOne.statusCode() + " " + resultOne.body() + " | " + resultTwo.statusCode() + " " + resultTwo.body());
            assertTrue(java.util.stream.Stream.of(resultOne, resultTwo).allMatch(response -> response.statusCode() == 200 || response.statusCode() == 409));

            String acceptedAppointmentId = resultOne.statusCode() == 200
                    ? json(resultOne).path("appointmentId").asString("") : json(resultTwo).path("appointmentId").asString("");
            String pendingAppointmentId = acceptedAppointmentId.equals("APT-001") ? "APT-002" : "APT-001";
            assertEquals("RESCHEDULED", appointment(acceptedAppointmentId).path("status").asString(""));
            assertEquals("RESCHEDULE_PENDING", appointment(pendingAppointmentId).path("status").asString(""));
            assertEquals("BOOKED", slot(firstSuggestion.path("slotId").asString("")).path("status").asString(""));
            String releasedSlotId = acceptedAppointmentId.equals("APT-001") ? "SLT-001" : "SLT-002";
            assertEquals("OPEN", slot(releasedSlotId).path("status").asString(""));

            JsonNode pendingSuggestion = pendingAppointmentId.equals("APT-001") ? firstSuggestion : secondSuggestion;
            HttpResponse<String> rejectStale = patch("/api/suggestions/" + pendingSuggestion.path("id").asString(""), "{\"status\":\"REJECTED\"}");
            assertEquals(200, rejectStale.statusCode());
        } finally {
            pool.shutdownNow();
        }

        HttpResponse<String> restore = patch("/api/providers/PRV-001/status", "{\"status\":\"AVAILABLE\"}");
        assertEquals(204, restore.statusCode());
    }

    @Test
    @Order(5)
    void cancelledSlotTriggersSuggestionThatCanBeAccepted() throws Exception {
        assertEquals(204, patch("/api/slots/SLT-003/status", "{\"status\":\"CANCELLED\"}").statusCode());
        assertEquals(204, patch("/api/slots/SLT-003/status", "{\"status\":\"CANCELLED\"}").statusCode());
        JsonNode affected = awaitSuggestion("APT-003");
        JsonNode suggestion = affected.path("suggestion");
        assertEquals("SLOT_CANCELLED", suggestion.path("triggerReason").asString(""));
        assertEquals(1, json(get("/api/suggestions")).size(), "repeating the same cancellation must not enqueue duplicates");
        assertEquals("RESCHEDULE_PENDING", affected.path("status").asString(""));

        HttpResponse<String> accepted = patch("/api/suggestions/" + suggestion.path("id").asString(""), "{\"status\":\"ACCEPTED\"}");
        assertEquals(200, accepted.statusCode());
        assertEquals("RESCHEDULED", appointment("APT-003").path("status").asString(""));
        assertEquals("BOOKED", slot(suggestion.path("slotId").asString("")).path("status").asString(""));
        assertEquals("CANCELLED", slot("SLT-003").path("status").asString(""));
    }

    @Test
    @Order(6)
    void manualRescheduleAndStatusFilteringWork() throws Exception {
        String pendingId = appointment("APT-001").path("status").asString("").equals("RESCHEDULE_PENDING") ? "APT-001" : "APT-002";
        HttpResponse<String> created = post("/api/appointments/" + pendingId + "/suggest", "{}");
        assertEquals(201, created.statusCode());
        JsonNode suggestion = json(created);
        assertEquals("INITIAL", suggestion.path("triggerReason").asString(""));
        assertEquals(200, patch("/api/suggestions/" + suggestion.path("id").asString(""), "{\"status\":\"ACCEPTED\"}").statusCode());
        assertEquals("RESCHEDULED", appointment(pendingId).path("status").asString(""));
        assertTrue(json(get("/api/appointments?status=RESCHEDULED")).size() >= 3);
    }

    @Test
    @Order(7)
    void invalidResourcesAndInvalidStatusesReturnClientErrors() throws Exception {
        assertApiError(post("/api/appointments/MISSING/suggest", "{}"), 404, "/api/appointments/MISSING/suggest");
        assertApiError(patch("/api/slots/SLT-004/status", "{\"status\":\"BOOKED\"}"), 400, "/api/slots/SLT-004/status");
        assertApiError(patch("/api/providers/PRV-002/status", "{\"status\":\"NOT_A_STATUS\"}"), 400, "/api/providers/PRV-002/status");
        assertApiError(get("/api/appointments?status=NOT_A_STATUS"), 400, "/api/appointments");
        JsonNode routeError = assertApiError(get("/api/unknown-resource"), 404, "/api/unknown-resource");
        assertEquals("The requested endpoint was not found.", routeError.path("message").asString(""));

        HttpResponse<String> invalidPatient = post("/api/appointments", """
                {"patientName":"  ","type":"ROUTINE","providerId":"PRV-002","slotId":"SLT-004"}
                """);
        JsonNode validationError = assertApiError(invalidPatient, 400, "/api/appointments");
        assertTrue(validationError.path("message").asString("").contains("patientName"));
        assertApiError(post("/api/appointments", "not-json"), 400, "/api/appointments");
        assertNotNull(json(get("/api/providers")));
    }

    @Test
    @Order(8)
    void concurrentAcceptanceAndRejectionCannotOverwriteEachOther() throws Exception {
        JsonNode testAppointment = null;
        for (JsonNode row : json(get("/api/appointments"))) {
            if (row.path("patientName").asString("").equals("Test Patient")) {
                testAppointment = row;
                break;
            }
        }
        assertNotNull(testAppointment);

        JsonNode suggestion = json(post("/api/appointments/" + testAppointment.path("id").asString("") + "/suggest", "{}"));
        String suggestionId = suggestion.path("id").asString("");
        var pool = Executors.newFixedThreadPool(2);
        var startTogether = new CountDownLatch(1);
        try {
            var accept = pool.submit(() -> { startTogether.await(); return patch("/api/suggestions/" + suggestionId, "{\"status\":\"ACCEPTED\"}"); });
            var reject = pool.submit(() -> { startTogether.await(); return patch("/api/suggestions/" + suggestionId, "{\"status\":\"REJECTED\"}"); });
            startTogether.countDown();
            HttpResponse<String> acceptResult = accept.get(10, TimeUnit.SECONDS);
            HttpResponse<String> rejectResult = reject.get(10, TimeUnit.SECONDS);
            assertEquals(1, java.util.stream.Stream.of(acceptResult, rejectResult).filter(response -> response.statusCode() == 200).count());
            assertEquals(1, java.util.stream.Stream.of(acceptResult, rejectResult).filter(response -> response.statusCode() == 409).count());

            String resolution = json(acceptResult.statusCode() == 200 ? acceptResult : rejectResult).path("status").asString("");
            String appointmentStatus = appointment(testAppointment.path("id").asString("")).path("status").asString("");
            assertEquals(resolution.equals("ACCEPTED") ? "RESCHEDULED" : "CONFIRMED", appointmentStatus);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @Order(9)
    void unavailableReplacementDoesNotMoveAppointmentIntoPendingState() throws Exception {
        for (JsonNode availableSlot : json(get("/api/slots"))) {
            if (availableSlot.path("status").asString("").equals("OPEN")) {
                assertEquals(204, patch("/api/slots/" + availableSlot.path("id").asString("") + "/status",
                        "{\"status\":\"CANCELLED\"}").statusCode());
            }
        }

        HttpResponse<String> noSuggestion = post("/api/appointments/APT-004/suggest", "{}");
        assertApiError(noSuggestion, 409, "/api/appointments/APT-004/suggest");
        assertEquals("CONFIRMED", appointment("APT-004").path("status").asString(""));
    }

    private JsonNode assertApiError(HttpResponse<String> response, int expectedStatus, String expectedPath) throws Exception {
        assertEquals(expectedStatus, response.statusCode());
        JsonNode body = json(response);
        assertTrue(body.path("timestamp").isString());
        assertEquals(expectedStatus, body.path("status").asInt());
        assertTrue(body.path("error").isString());
        assertTrue(body.path("message").isString());
        assertEquals(expectedPath, body.path("path").asString(""));
        return body;
    }

    private JsonNode awaitSuggestion(String appointmentId) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
        JsonNode current = appointment(appointmentId);
        while ((current.path("suggestion").isMissingNode() || current.path("suggestion").isNull())
                && System.nanoTime() < deadline) {
            Thread.sleep(100);
            current = appointment(appointmentId);
        }
        assertTrue(current.path("suggestion").isObject(), "expected an asynchronous suggestion for " + appointmentId);
        return current;
    }

    private JsonNode appointment(String id) throws Exception {
        for (JsonNode appointment : json(get("/api/appointments"))) {
            if (appointment.path("id").asString("").equals(id)) return appointment;
        }
        throw new AssertionError("Appointment not found: " + id);
    }

    private JsonNode slot(String id) throws Exception {
        for (JsonNode slot : json(get("/api/slots"))) {
            if (slot.path("id").asString("").equals(id)) return slot;
        }
        throw new AssertionError("Slot not found: " + id);
    }

    private JsonNode json(HttpResponse<String> response) throws Exception { return mapper.readTree(response.body()); }
    private HttpResponse<String> get(String path) throws Exception { return send("GET", path, null); }
    private HttpResponse<String> post(String path, String body) throws Exception { return send("POST", path, body); }
    private HttpResponse<String> patch(String path, String body) throws Exception { return send("PATCH", path, body); }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json");
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body);
        return http.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
    }
}
