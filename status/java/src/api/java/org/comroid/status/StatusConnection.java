package org.comroid.status;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.comroid.api.ContextualProvider;
import org.comroid.api.Polyfill;
import org.comroid.common.io.FileHandle;
import org.comroid.common.java.JITAssistant;
import org.comroid.mutatio.ref.FutureReference;
import org.comroid.mutatio.ref.Reference;
import org.comroid.mutatio.span.Span;
import org.comroid.restless.REST;
import org.comroid.restless.body.BodyBuilderType;
import org.comroid.status.entity.Service;
import org.comroid.status.rest.Endpoint;
import org.comroid.uniform.SerializationAdapter;
import org.comroid.uniform.cache.ProvidedCache;
import org.comroid.uniform.node.UniObjectNode;
import org.comroid.util.StandardValueType;
import org.jetbrains.annotations.Nullable;

import java.util.NoSuchElementException;
import java.util.concurrent.*;

import static org.comroid.restless.CommonHeaderNames.AUTHORIZATION;

public final class StatusConnection implements ContextualProvider.Underlying {
    private static final Logger logger = LogManager.getLogger();
    private final ContextualProvider context;
    @Nullable
    private final String serviceName;
    private final String token;
    private final ScheduledExecutorService executor;
    private final REST rest;
    private final ProvidedCache<String, Service> serviceCache;
    private final Reference<Service> ownService;
    public int refreshTimeout = 60; // seconds
    public int crashedTimeout = 3600; // seconds
    private boolean polling = false;

    public String getServiceName() {
        return serviceName;
    }

    public String getToken() {
        return token;
    }

    public Service getService() {
        return ownService.assertion();
    }

    public REST getRest() {
        return rest;
    }

    public ProvidedCache<String, Service> getServiceCache() {
        return serviceCache;
    }

    public boolean isPolling() {
        return polling;
    }

    @Override
    public ContextualProvider getUnderlyingContextualProvider() {
        return context;
    }

    public StatusConnection(ContextualProvider context, FileHandle tokenFile) {
        this(context, null, tokenFile);
    }

    public StatusConnection(ContextualProvider context, @Nullable String serviceName, FileHandle tokenFile) {
        this(context, serviceName, tokenFile.getContent(), Executors.newScheduledThreadPool(4));
    }

    public StatusConnection(ContextualProvider context, @Nullable String serviceName, String token, ScheduledExecutorService executor) {
        this.context = context.plus("StatusConnection", this);
        this.serviceName = serviceName;
        this.token = token;
        this.executor = executor;
        this.rest = new REST(this.context, executor);
        this.serviceCache = new ProvidedCache<>(context, 250, ForkJoinPool.commonPool(), this::requestServiceByName);
        this.ownService = new FutureReference<>(requestServiceByName(serviceName));

        JITAssistant.prepareStatic(Service.class);
    }

    @Override
    public String toString() {
        return String.format("StatusConnection{serviceName='%s', service=%s}", serviceName, ownService.get());
    }

    public boolean startPolling() {
        if (polling)
            return false;
        executePoll().join();
        return (polling = true);
    }

    public CompletableFuture<Service> stopPolling(Service.Status newStatus) {
        if (!polling)
            return Polyfill.failedFuture(new RuntimeException("Connection is not polling!"));
        if (serviceName == null)
            throw new NoSuchElementException("No service name defined");
        return rest.request(Service.Bind.Root)
                .method(REST.Method.DELETE)
                .endpoint(Endpoint.POLL.complete(serviceName))
                .addHeader(AUTHORIZATION, token)
                .buildBody(BodyBuilderType.OBJECT, obj ->
                        obj.put(Service.Bind.Status, newStatus.getValue()))
                .execute$autoCache(Service.Bind.Name, serviceCache)
                .thenApply(Span::requireSingle);
    }

    private CompletableFuture<Void> executePoll() {
        logger.debug("Polling Status");
        return sendPoll().thenRun(this::schedulePoll);
    }

    private void schedulePoll() {
        executor.schedule(() -> {
            try {
                executePoll().exceptionally(t -> {
                    logger.error("Error with Poll request", t);
                    return null;
                });
            } catch (Throwable t) {
                logger.error("Error while executing Poll", t);
            }
        }, refreshTimeout, TimeUnit.SECONDS);
    }

    private CompletableFuture<Service> sendPoll() {
        if (serviceName == null)
            throw new NoSuchElementException("No service name defined");
        return rest.request(Service.Bind.Root)
                .method(REST.Method.POST)
                .endpoint(Endpoint.POLL.complete(serviceName))
                .addHeader(AUTHORIZATION, token)
                .buildBody(BodyBuilderType.OBJECT, obj -> {
                    obj.put("status", StandardValueType.INTEGER, Service.Status.ONLINE.getValue());
                    obj.put("expected", StandardValueType.INTEGER, refreshTimeout);
                    obj.put("timeout", StandardValueType.INTEGER, crashedTimeout);
                })
                .execute$autoCache(Service.Bind.Name, serviceCache)
                .thenApply(Span::requireNonNull);
    }

    public CompletableFuture<Service> updateStatus(Service.Status status) {
        if (serviceName == null)
            throw new NoSuchElementException("No service name defined");
        final UniObjectNode data = rest.requireFromContext(SerializationAdapter.class)
                .createObjectNode();

        data.put("status", StandardValueType.INTEGER, status.getValue());

        return rest.request(Service.class)
                .method(REST.Method.POST)
                .endpoint(Endpoint.UPDATE_SERVICE_STATUS.complete(serviceName))
                .addHeader(AUTHORIZATION, token)
                .body(data.toString())
                .execute$autoCache(Service.Bind.Name, serviceCache)
                .thenApply(Span::requireNonNull);
    }

    public CompletableFuture<Service> requestServiceByName(String name) {
        return rest.request(Service.class)
                .method(REST.Method.GET)
                .endpoint(Endpoint.SPECIFIC_SERVICE.complete(name))
                .execute$autoCache(Service.Bind.Name, serviceCache)
                .thenApply(Span::requireNonNull);
    }
}
