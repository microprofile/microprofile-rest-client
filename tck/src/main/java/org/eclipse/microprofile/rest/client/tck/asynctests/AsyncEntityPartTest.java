/*
 * Copyright 2025 Contributors to the Eclipse Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.eclipse.microprofile.rest.client.tck.asynctests;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletionStage;

import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.arquillian.testng.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.testng.Assert;
import org.testng.annotations.Test;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.EntityPart;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;

/**
 * @author <a href="mailto:neena.jacob@ibm.com">Neena Jacob</a>
 */
@RunAsClient
public class AsyncEntityPartTest extends Arquillian {

    @ArquillianResource
    private URI uri;

    @Deployment
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class, EntityPart.class.getSimpleName() + ".war")
                .addClasses(FileUploadResource.class, FileUploadApplication.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @ApplicationPath("/")
    public static class FileUploadApplication extends jakarta.ws.rs.core.Application {
    }

    @Path("/entitypart")
    public static class FileUploadResource {

        @POST
        @Path("upload")
        @Consumes(MediaType.MULTIPART_FORM_DATA)
        @Produces(MediaType.APPLICATION_JSON)
        public Response uploadFile(List<EntityPart> entityParts) throws IOException {
            final JsonArrayBuilder jsonBuilder = Json.createArrayBuilder();
            for (EntityPart part : entityParts) {
                final JsonObjectBuilder jsonPartBuilder = Json.createObjectBuilder();
                jsonPartBuilder.add("name", part.getName());
                if (part.getFileName().isPresent()) {
                    jsonPartBuilder.add("fileName", part.getFileName().get());
                } else {
                    throw new BadRequestException("No file name for entity part " + part);
                }
                jsonPartBuilder.add("content", part.getContent(String.class));
                jsonBuilder.add(jsonPartBuilder);
            }
            return Response.status(201).entity(jsonBuilder.build()).build();
        }
    }

    /**
     * Tests that a single file is upload. The response is a simple JSON response with the file information.
     *
     * @throws Exception
     *             if a test error occurs
     */
    @Test
    public void uploadFileAsync() throws Exception {
        try (AsyncFileManagerClient client = createClient()) {
            final byte[] content;
            try (InputStream in = AsyncEntityPartTest.class.getResourceAsStream("/multipart/test-file1.txt")) {
                Assert.assertNotNull(in, "Could not find /multipart/test-file1.txt");
                content = in.readAllBytes();
            }
            // Send in an InputStream to ensure it works with an InputStream
            final List<EntityPart> files = List.of(EntityPart.withFileName("test-file1.txt")
                    .content(new ByteArrayInputStream(content))
                    .mediaType(MediaType.APPLICATION_OCTET_STREAM_TYPE)
                    .build());

            CompletionStage<Response> futureResponse = client.uploadFileAsync(files);
            Response response = futureResponse.toCompletableFuture().get();

            try {
                Assert.assertEquals(201, response.getStatus());
                final JsonArray jsonArray = response.readEntity(JsonArray.class);
                Assert.assertNotNull(jsonArray);
                Assert.assertEquals(jsonArray.size(), 1);
                final JsonObject json = jsonArray.getJsonObject(0);
                Assert.assertEquals(json.getString("name"), "test-file1.txt");
                Assert.assertEquals(json.getString("fileName"), "test-file1.txt");
                Assert.assertEquals(json.getString("content"), "This is a test file for file 1.\n");
            } finally {
                response.close();
            }
        }
    }

    private AsyncFileManagerClient createClient() {
        return RestClientBuilder.newBuilder()
                .baseUri(UriBuilder.fromUri(uri).path("entitypart").build())
                .build(AsyncFileManagerClient.class);
    }

    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public interface AsyncFileManagerClient extends AutoCloseable {

        @POST
        @Path("upload")
        CompletionStage<Response> uploadFileAsync(List<EntityPart> entityParts);
    }

}
