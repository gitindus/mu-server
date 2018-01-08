package io.muserver;

import okhttp3.Response;
import org.junit.After;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static scaffolding.ClientUtils.call;
import static scaffolding.ClientUtils.request;

public class HttpsTest {

    private MuServer server;

    @Test public void canCreate() throws Exception {
        server = MuServerBuilder.httpsServer()
            .withHttpsConnection(8443, SSLContextBuilder.unsignedLocalhostCert())
            .addHandler((request, response) -> {
                response.contentType(ContentTypes.TEXT_PLAIN);
                response.write("This is encrypted and the URL is " + request.uri());
                return true;
            })
            .start();

        Response resp = call(request().url(server.httpsUrl()));
        assertThat(resp.body().string(), equalTo("This is encrypted and the URL is https://localhost:8443/"));
    }

    @Test public void httpCanBeDisabled() {
        server = MuServerBuilder.httpsServer()
            .withHttpDisabled()
            .addHandler((request, response) -> {
                response.write("This is encrypted");
                return true;
            })
            .start();

        assertThat(server.httpUri(), is(nullValue()));
    }

    @After public void stopIt() {
        server.stop();
    }
}
