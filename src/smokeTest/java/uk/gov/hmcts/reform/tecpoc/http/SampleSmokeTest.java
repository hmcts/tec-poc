package uk.gov.hmcts.reform.tecpoc.http;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

class SampleSmokeTest {
    private static final String TEST_URL = System.getenv().getOrDefault("TEST_URL", "http://localhost:8080");

    @Test
    void smokeTest() {
        Response response = given()
            .baseUri(TEST_URL)
            .contentType(ContentType.JSON)
            .when()
            .get()
            .then()
            .extract().response();

        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertTrue(response.asString().startsWith("Welcome"));
    }
}
