package io.axoniq.demo.giftcard;

import io.axoniq.axonserver.connector.AxonServerConnectionFactory;
import io.axoniq.axonserver.connector.impl.AxonConnectorThreadFactory;
import io.axoniq.axonserver.connector.impl.ServerAddress;
import io.axoniq.axonserver.grpc.control.NodeInfo;
import io.grpc.netty.GrpcSslContexts;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.config.TagsConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.ReflectionUtils;

import javax.net.ssl.SSLException;
import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
public class GcApp {

    @Configuration
    public static class GcpAxonServerConfig {

        @Bean
        @Primary
        public AxonServerConnectionFactory axonServerConnectionFactory(
                AxonServerConfiguration axonServerConfiguration,
                TagsConfiguration tagsConfiguration) throws IllegalAccessException {
            AxonServerConnectionFactory.Builder builder = AxonServerConnectionFactory.forClient(
                    axonServerConfiguration.getComponentName(), axonServerConfiguration.getClientId()
            );

            // taken over from AxonServerConnectionManager.Builder.build()
            List<NodeInfo> routingServers = axonServerConfiguration.routingServers();
            if (!routingServers.isEmpty()) {
                ServerAddress[] addresses = new ServerAddress[routingServers.size()];
                for (int i = 0; i < addresses.length; i++) {
                    NodeInfo routingServer = routingServers.get(i);
                    addresses[i] = new ServerAddress(routingServer.getHostName(), routingServer.getGrpcPort());
                }
                builder.routingServers(addresses);
            }

            if (axonServerConfiguration.isSslEnabled()) {
                if (axonServerConfiguration.getCertFile() != null) {
                    try {
                        File certificateFile = new File(axonServerConfiguration.getCertFile());
                        builder.useTransportSecurity(GrpcSslContexts.forClient()
                                .trustManager(certificateFile)
                                .build());
                    } catch (SSLException e) {
                        throw new AxonConfigurationException("Exception configuring Transport Security", e);
                    }
                } else {
                    builder.useTransportSecurity();
                }
            }

            builder.connectTimeout(axonServerConfiguration.getConnectTimeout(), TimeUnit.MILLISECONDS);

            if (axonServerConfiguration.getToken() != null) {
                builder.token(axonServerConfiguration.getToken());
            }

            tagsConfiguration.getTags().forEach(builder::clientTag);

            if (axonServerConfiguration.getMaxMessageSize() > 0) {
                builder.maxInboundMessageSize(axonServerConfiguration.getMaxMessageSize());
            }

            if (axonServerConfiguration.getKeepAliveTime() > 0) {
                builder.usingKeepAlive(axonServerConfiguration.getKeepAliveTime(),
                        axonServerConfiguration.getKeepAliveTimeout(),
                        TimeUnit.MILLISECONDS,
                        true);
            }

            if (axonServerConfiguration.getProcessorsNotificationRate() > 0) {
                builder.processorInfoUpdateFrequency(axonServerConfiguration.getProcessorsNotificationRate(),
                        TimeUnit.MILLISECONDS);
            }

            // from AxonServerConnectionFactory.Builder.validate()
            /*if (builder.routingServers == null) {
                routingServers = Collections.singletonList(new ServerAddress());
            }*/

            /*if (executorService == null) {
                executorService = new ScheduledThreadPoolExecutor(
                        executorPoolSize, AxonConnectorThreadFactory.forInstanceId(clientInstanceId)
                );
            }*/

            // FIXME inject private field
            Field executorServiceField = ReflectionUtils.findField(builder.getClass(), "executorService");
            ReflectionUtils.makeAccessible(executorServiceField);
            executorServiceField.set(builder,
                    new ScheduledThreadPoolExecutor(
                            2,
                            AxonConnectorThreadFactory.forInstanceId(axonServerConfiguration.getClientId())
                    )
            );

            return new GcpAxonServerConnectionFactory(builder);
        }

        @Bean
        @Primary
        public AxonServerConnectionManager axonServerConnectionManager(
                AxonServerConnectionFactory connectionFactory,
                AxonServerConfiguration axonServerConfiguration,
                TagsConfiguration tagsConfiguration
        ) {
            AxonServerConnectionManager.Builder builder = AxonServerConnectionManager.builder()
                    .axonServerConfiguration(axonServerConfiguration)
                    .tagsConfiguration(tagsConfiguration);

            return new GcpAxonServerConnectionManager(builder, connectionFactory);
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(GcApp.class, args);
    }
}
