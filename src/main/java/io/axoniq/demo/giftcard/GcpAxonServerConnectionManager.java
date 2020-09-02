package io.axoniq.demo.giftcard;

import io.axoniq.axonserver.connector.AxonServerConnectionFactory;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;

public class GcpAxonServerConnectionManager extends AxonServerConnectionManager {

    public GcpAxonServerConnectionManager(Builder builder, AxonServerConnectionFactory connectionFactory) {
        super(builder, connectionFactory);
    }
}
