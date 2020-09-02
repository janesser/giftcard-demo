package io.axoniq.demo.giftcard;

import com.google.api.client.util.Throwables;
import com.google.auth.oauth2.GoogleCredentials;
import io.axoniq.axonserver.connector.AxonServerConnectionFactory;
import io.grpc.*;
import io.grpc.auth.MoreCallCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

// via AxonServerConnectionManager
public class GcpAxonServerConnectionFactory extends AxonServerConnectionFactory {

    private static class WorkloadIdentityAuthInterceptor implements ClientInterceptor {
        private static final Logger LOG = LoggerFactory.getLogger(WorkloadIdentityAuthInterceptor.class);


        private final CallCredentials creds;

        public WorkloadIdentityAuthInterceptor() throws IOException {
            GoogleCredentials googleCredentials = GoogleCredentials.getApplicationDefault();
            creds = MoreCallCredentials.from(googleCredentials);

            // FIXME hide access token from logs
            LOG.info("Using credentials " + googleCredentials.toString());
        }

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> methodDescriptor, CallOptions callOptions, Channel channel) {
            callOptions.withCallCredentials(creds);

            return channel.newCall(methodDescriptor, callOptions);
        }
    }

    protected GcpAxonServerConnectionFactory(Builder builder) {
        super(
                builder.customize(managedChannelBuilder -> {
                    try {
                        return managedChannelBuilder.intercept(new WorkloadIdentityAuthInterceptor());
                    } catch (IOException e) {
                        throw Throwables.propagate(e);
                    }
                })
        );
    }
}
