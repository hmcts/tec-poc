package uk.gov.hmcts.reform.tecpoc.http;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

class SampleFunctionalTest {
    protected static final String CONTENT_TYPE_VALUE = "application/json";
    private static final String TEST_URL = System.getenv().getOrDefault("TEST_URL", "http://localhost:8080");

    @Test
    void functionalTest() {
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
