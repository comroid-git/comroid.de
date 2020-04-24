package org.comroid.server.status;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.comroid.listnr.Event;
import org.comroid.listnr.EventType;
import org.comroid.restless.CommonHeaderNames;
import org.comroid.restless.HTTPStatusCodes;
import org.comroid.restless.REST;
import org.comroid.server.status.entity.StatusServerEntity;
import org.comroid.server.status.entity.message.StatusUpdateMessage;
import org.comroid.server.status.entity.service.Service;
import org.comroid.uniform.node.UniNode;
import org.comroid.uniform.node.UniObjectNode;
import org.comroid.varbind.VarCarrier;
import org.comroid.varbind.VariableCarrier;

import com.sun.net.httpserver.HttpExchange;

import static org.comroid.uniform.adapter.json.fastjson.FastJSONLib.fastJsonLib;

public final class EventContainer {
    public final  EventType<Hello, UniObjectNode> TYPE_HELLO = statusServer.getEventHub()
            .createEventType(Hello.class, node -> new Hello.Impl(statusServer, node));
    private final StatusServer                    statusServer;

    public EventContainer(StatusServer statusServer) {
        this.statusServer = statusServer;

        HELLO         = httpExchange -> {
            httpExchange.getResponseHeaders()
                    .add(CommonHeaderNames.ACCEPTED_CONTENT_TYPE,
                            statusServer.getSerializationLibrary()
                                    .getMimeType()
                    );

            if (validateContentType(httpExchange)) {
                httpExchange.sendResponseHeaders(HTTPStatusCodes.OK, 0);
            } else {
                httpExchange.sendResponseHeaders(HTTPStatusCodes.UNSUPPORTED_MEDIA_TYPE, 0);
            }
        };
        STATUS_UPDATE = httpExchange -> {
            if (!validateContentType(httpExchange)) {
                httpExchange.sendResponseHeaders(HTTPStatusCodes.UNSUPPORTED_MEDIA_TYPE, 0);
                return;
            }

            final String body = new BufferedReader(new InputStreamReader(httpExchange.getRequestBody())).lines()
                    .collect(Collectors.joining());
            final UniNode data = statusServer.getSerializationLibrary()
                    .createUniNode(body);
            final StatusUpdateMessage message = new StatusUpdateMessage(statusServer, data.asObjectNode());

            switch (REST.Method.valueOf(httpExchange.getRequestMethod())) {
                case GET:
                    Service service = statusServer.getEntityCache()
                            .getReference(message.getTargetID(), false)
                            .wrap()
                            .filter(entity -> entity.getType()
                                    .isType(StatusServerEntity.Type.MESSAGE))
                            .map(Service.class::cast)
                            .orElse(null);

                    if (service == null) {
                        break;
                    }
                case POST:
                    break;
                case DELETE:
                    break;
                default:
                    httpExchange.sendResponseHeaders(HTTPStatusCodes.METHOD_NOT_ALLOWED, 0);
                    return;
            }
        };
    }

    private boolean validateContentType(HttpExchange exchange) {
        Stream.of(CommonHeaderNames.ACCEPTED_CONTENT_TYPE, CommonHeaderNames.REQUEST_CONTENT_TYPE)
                .forEachOrdered(type -> exchange.getResponseHeaders()
                        .add(
                                type,
                                statusServer.getSerializationLibrary()
                                        .getMimeType()
                        ));

        return exchange.getRequestHeaders()
                .getFirst(CommonHeaderNames.REQUEST_CONTENT_TYPE)
                .equals(fastJsonLib.getMimeType());
    }

    interface Hello extends Event<Hello> {
        final class Impl extends Event.Support.Abstract<Hello> implements Hello, VarCarrier.Underlying<StatusServer> {
            private final VarCarrier<StatusServer> underlyingVarCarrier;

            public Impl(StatusServer server, UniObjectNode node) {
                this.underlyingVarCarrier = new VariableCarrier<>(server.getSerializationLibrary(), node, server);
            }

            @Override
            public VarCarrier<StatusServer> getUnderlyingVarCarrier() {
                return underlyingVarCarrier;
            }
        }
    }

    interface StatusUpdate extends Event<StatusUpdate> {
        final class Impl extends Event.Support.Abstract<StatusUpdate>
                implements StatusUpdate, VarCarrier.Underlying<StatusServer> {
            private final VarCarrier<StatusServer> underlyingVarCarrier;

            public Impl(StatusServer server, UniObjectNode node) {
                underlyingVarCarrier = new VariableCarrier<>(server.getSerializationLibrary(), node, server);
            }

            @Override
            public VarCarrier<StatusServer> getUnderlyingVarCarrier() {
                return underlyingVarCarrier;
            }
        }
    }
}