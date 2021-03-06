package io.muserver;

import io.netty.util.HashedWheelTimer;
import okhttp3.Response;
import org.junit.Test;
import scaffolding.ServerUtils;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static io.muserver.RateLimitBuilder.rateLimit;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static scaffolding.ClientUtils.call;
import static scaffolding.ClientUtils.request;
import static scaffolding.MuAssert.assertEventually;

public class RateLimiterTest {

    @Test
    public void returnsFalseIfLimitExceeded() throws InterruptedException {
        RateLimiter limiter = new RateLimiter(
            request -> rateLimit().withBucket("blah").withRate(3).withWindow(100, TimeUnit.MILLISECONDS).build(),
            new HashedWheelTimer());
        assertThat(limiter.record(null), is(true));
        assertThat(limiter.record(null), is(true));
        assertThat(limiter.record(null), is(true));
        assertThat(limiter.record(null), is(false));
        Thread.sleep(250);
        assertThat(limiter.record(null), is(true));
        assertEventually(() -> limiter.snapshot().keySet(), is(empty()));
    }

    @Test
    public void returningNullMeansAlwaysAllow() throws InterruptedException {
        RateLimiter limiter = new RateLimiter(request -> null, new HashedWheelTimer());
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.record(null), is(true));
        }
        assertThat(limiter.snapshot().keySet(), is(empty()));
    }

    @Test
    public void ignoreActionDoesNotBlock() throws InterruptedException {
        RateLimiter limiter = new RateLimiter(request -> rateLimit().withBucket("blah")
            .withRate(1).withRejectionAction(RateLimitRejectionAction.IGNORE)
            .build(), new HashedWheelTimer());
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.record(null), is(true));
        }
        assertEventually(() -> limiter.snapshot().keySet(), is(empty()));
    }

    @Test
    public void multipleLimitersCanBeAddedToTheServer() throws IOException {
        MuServer server = ServerUtils.httpsServerForTest()
            .withRateLimiter(request -> rateLimit()
                .withBucket(request.remoteAddress())
                .withRate(100000) // this will not have an effect because it allows so many requests
                .withWindow(1, TimeUnit.MILLISECONDS)
                .build())
            .withRateLimiter(request -> RateLimit.builder()
                .withBucket(request.remoteAddress())
                .withRate(2) // this will just allow 2 through for this test before returning 429s
                .withWindow(1, TimeUnit.MINUTES)
                .build())
            .withRateLimiter(request -> rateLimit()
                .withBucket(request.remoteAddress())
                .withRate(1) // this will have no effect because although the rate will trip, the action is ignore
                .withWindow(1, TimeUnit.MINUTES)
                .withRejectionAction(RateLimitRejectionAction.IGNORE)
                .build())
            .addHandler(Method.GET, "/", (request, response, pathParams) -> response.write("hi"))
            .start();
        for (int i = 0; i < 2; i++) {
            try (Response resp = call(request(server.uri()))) {
                assertThat("req " + i, resp.code(), is(200));
                assertThat("req " + i, resp.body().string(), is("hi"));
            }
        }
        for (int i = 0; i < 3; i++) {
            try (Response resp = call(request(server.uri()))) {
                assertThat(resp.code(), is(429));
                assertThat(resp.body().string(), is("429 Too Many Requests"));
            }
        }
        assertThat(server.stats().rejectedDueToOverload(), is(3L));
    }

}