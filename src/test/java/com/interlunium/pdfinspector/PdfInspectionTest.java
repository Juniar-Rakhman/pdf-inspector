package com.interlunium.pdfinspector;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class PdfInspectionTest {

    @Test
    void analyzesSignedPdfEndToEnd() throws Exception {
        byte[] signedPdf = SignedPdfFixture.create();

        String documentId = given()
                .multiPart("file", "signed.pdf", signedPdf)
                .when().post("/api/pdf/analyze")
                .then().statusCode(200)
                .body("documentId", notNullValue())
                .extract().path("documentId");

        given().get("/api/pdf/{id}", documentId)
                .then().statusCode(200)
                .body("signatureCount", equalTo(1))
                .body("revisionCount", equalTo(2))
                .body("pageCount", equalTo(1));

        given().get("/api/pdf/{id}/signatures", documentId)
                .then().statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].signerName", equalTo(SignedPdfFixture.signerName()))
                .body("[0].coversWholeDocument", equalTo(true));

        given().get("/api/pdf/{id}/cms/0", documentId)
                .then().statusCode(200)
                .body("signerInfos", hasSize(1))
                .body("signerInfos[0].digestAlgorithmName", equalTo("SHA-256"))
                .body("signerInfos[0].signedAttributeCount", equalTo(4));

        given().get("/api/pdf/{id}/asn1/0", documentId)
                .then().statusCode(200)
                .body("tagName", equalTo("SEQUENCE"))
                .body("constructed", equalTo(true));

        given().get("/api/pdf/{id}/certificates", documentId)
                .then().statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].commonName", equalTo(SignedPdfFixture.signerName()))
                .body("[0].selfSigned", equalTo(true));

        given().get("/api/pdf/{id}/certificates/chains", documentId)
                .then().statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].complete", equalTo(true))
                .body("[0].nodes[0].type", equalTo("SIGNER"));

        given().get("/api/pdf/{id}/validation", documentId)
                .then().statusCode(200)
                .body("signatures[0].signatureIntegrity", equalTo("VALID"))
                .body("signatures[0].documentIntegrity", equalTo("VALID"))
                .body("certificates[0].status", equalTo("VALID"))
                .body("chains[0].complete", equalTo(true));

        given().get("/api/pdf/{id}/revisions", documentId)
                .then().statusCode(200)
                .body("size()", equalTo(2))
                .body("[1].signerNames", hasSize(1))
                .body("[1].isLast", equalTo(true));

        given().get("/api/pdf/{id}/content", documentId)
                .then().statusCode(200);
    }

    @Test
    void rejectsNonPdfUploads() {
        given()
                .multiPart("file", "not-a-pdf.txt", "hello world".getBytes())
                .when().post("/api/pdf/analyze")
                .then().statusCode(400);
    }

    @Test
    void returns404ForUnknownDocument() {
        given().get("/api/pdf/does-not-exist")
                .then().statusCode(404);
    }
}
