package org.comroid.auth.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.comroid.api.ContextualProvider;
import org.comroid.api.Rewrapper;
import org.comroid.auth.server.AuthServer;
import org.comroid.common.io.FileHandle;
import org.comroid.webkit.oauth.resource.ResourceProvider;
import org.comroid.uniform.node.UniObjectNode;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public final class ServiceManager implements ContextualProvider.Underlying, ResourceProvider {
    public static final FileHandle DIR = AuthServer.DATA.createSubDir("services");
    private static final Logger logger = LogManager.getLogger();
    private final Map<UUID, Service> services = new ConcurrentHashMap<>();
    private final ContextualProvider context;

    @Override
    public ContextualProvider getUnderlyingContextualProvider() {
        return context;
    }

    public Collection<Service> getServices() {
        return services.values();
    }

    public ServiceManager(AuthServer server) {
        this.context = server.plus("ServiceManager", this);

        Stream.of(DIR.listFiles())
                .filter(File::isDirectory)
                .filter(dir -> {
                    try {
                        //noinspection ResultOfMethodCallIgnored
                        UUID.fromString(dir.getName());
                        return true;
                    } catch (IllegalArgumentException ignored) {
                        return false;
                    }
                })
                .map(FileHandle::new)
                .map(dir -> new Service(this, dir))
                .forEach(account -> services.put(account.getUUID(), account));
        logger.info("Loading finished; loaded {} services", services.size());
    }

    @Override
    public boolean hasResource(UUID uuid) {
        return services.containsKey(uuid);
    }

    public Service createService(UniObjectNode initialData) {
        Service service = new Service(this, initialData);
        services.put(service.getUUID(), service);
        return service;
    }

    @Override
    public Rewrapper<Service> getResource(final UUID uuid) {
        return () -> services.getOrDefault(uuid, null);
    }
}