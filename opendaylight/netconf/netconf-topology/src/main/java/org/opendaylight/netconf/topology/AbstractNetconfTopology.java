/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.netty.util.concurrent.EventExecutor;
import java.io.File;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.controller.sal.core.api.Broker;
import org.opendaylight.controller.sal.core.api.Broker.ProviderSession;
import org.opendaylight.controller.sal.core.api.Provider;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfReconnectingClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfReconnectingClientConfigurationBuilder;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.LoginPassword;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.NetconfDevice;
import org.opendaylight.netconf.sal.connect.netconf.NetconfStateSchemas;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCapabilities;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCommunicator;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.KeepaliveSalFacade;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.pipeline.TopologyMountPointFacade.ConnectionStatusListenerRegistration;
import org.opendaylight.protocol.framework.ReconnectStrategy;
import org.opendaylight.protocol.framework.ReconnectStrategyFactory;
import org.opendaylight.protocol.framework.TimedReconnectStrategy;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.Credentials;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.Identifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaContextFactory;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceFilter;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;
import org.opendaylight.yangtools.yang.model.repo.util.FilesystemSchemaSourceCache;
import org.opendaylight.yangtools.yang.parser.repo.SharedSchemaRepository;
import org.opendaylight.yangtools.yang.parser.util.TextToASTTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractNetconfTopology implements NetconfTopology, BindingAwareProvider, Provider {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractNetconfTopology.class);

    protected static final long DEFAULT_REQUEST_TIMEOUT_MILLIS = 60000L;
    protected static final int DEFAULT_KEEPALIVE_DELAY = 0;
    protected static final boolean DEFAULT_RECONNECT_ON_CHANGED_SCHEMA = false;
    private static final int DEFAULT_MAX_CONNECTION_ATTEMPTS = 0;
    private static final int DEFAULT_BETWEEN_ATTEMPTS_TIMEOUT_MILLIS = 2000;
    private static final long DEFAULT_CONNECTION_TIMEOUT_MILLIS = 20000L;
    private static final BigDecimal DEFAULT_SLEEP_FACTOR = new BigDecimal(1.5);

    // constants related to Schema Cache(s)
    /**
     * Filesystem based caches are stored relative to the cache directory.
     */
    private static final String CACHE_DIRECTORY = "cache";

    /**
     * The default cache directory relative to <code>CACHE_DIRECTORY</code>
     */
    private static final String DEFAULT_CACHE_DIRECTORY = "schema";

    /**
     * The qualified schema cache directory <code>cache/schema</code>
     */
    private static final String QUALIFIED_DEFAULT_CACHE_DIRECTORY = CACHE_DIRECTORY + File.separator+ DEFAULT_CACHE_DIRECTORY;

    /**
     * The name for the default schema repository
     */
    private static final String DEFAULT_SCHEMA_REPOSITORY_NAME = "sal-netconf-connector";

    /**
     * The default schema repository in the case that one is not specified.
     */
    private static final SharedSchemaRepository DEFAULT_SCHEMA_REPOSITORY =
            new SharedSchemaRepository(DEFAULT_SCHEMA_REPOSITORY_NAME);

    /**
     * The default <code>FilesystemSchemaSourceCache</code>, which stores cached files in <code>cache/schema</code>.
     */
    private static final FilesystemSchemaSourceCache<YangTextSchemaSource> DEFAULT_CACHE =
            new FilesystemSchemaSourceCache<>(DEFAULT_SCHEMA_REPOSITORY, YangTextSchemaSource.class,
                    new File(QUALIFIED_DEFAULT_CACHE_DIRECTORY));

    /**
     * The default factory for creating <code>SchemaContext</code> instances.
     */
    private static final SchemaContextFactory DEFAULT_SCHEMA_CONTEXT_FACTORY =
            DEFAULT_SCHEMA_REPOSITORY.createSchemaContextFactory(SchemaSourceFilter.ALWAYS_ACCEPT);

    /**
     * Keeps track of initialized Schema resources.  A Map is maintained in which the key represents the name
     * of the schema cache directory, and the value is a corresponding <code>SchemaResourcesDTO</code>.  The
     * <code>SchemaResourcesDTO</code> is essentially a container that allows for the extraction of the
     * <code>SchemaRegistry</code> and <code>SchemaContextFactory</code> which should be used for a particular
     * Netconf mount.  Access to <code>schemaResourcesDTOs</code> should be surrounded by appropriate
     * synchronization locks.
     */
    private static volatile Map<String, NetconfDevice.SchemaResourcesDTO> schemaResourcesDTOs = new HashMap<>();

    // Initializes default constant instances for the case when the default schema repository
    // directory cache/schema is used.
    static {
        schemaResourcesDTOs.put(DEFAULT_CACHE_DIRECTORY,
                new NetconfDevice.SchemaResourcesDTO(DEFAULT_SCHEMA_REPOSITORY,
                        DEFAULT_SCHEMA_CONTEXT_FACTORY,
                        new NetconfStateSchemas.NetconfStateSchemasResolverImpl()));
        DEFAULT_SCHEMA_REPOSITORY.registerSchemaSourceListener(DEFAULT_CACHE);
        DEFAULT_SCHEMA_REPOSITORY.registerSchemaSourceListener(
                TextToASTTransformer.create(DEFAULT_SCHEMA_REPOSITORY, DEFAULT_SCHEMA_REPOSITORY));
    }

    protected final String topologyId;
    private final NetconfClientDispatcher clientDispatcher;
    protected final BindingAwareBroker bindingAwareBroker;
    protected final Broker domBroker;
    private final EventExecutor eventExecutor;
    protected final ScheduledThreadPool keepaliveExecutor;
    protected final ThreadPool processingExecutor;
    protected final SharedSchemaRepository sharedSchemaRepository;

    protected SchemaSourceRegistry schemaRegistry = DEFAULT_SCHEMA_REPOSITORY;
    protected SchemaContextFactory schemaContextFactory = DEFAULT_SCHEMA_CONTEXT_FACTORY;

    protected DOMMountPointService mountPointService = null;
    protected DataBroker dataBroker = null;
    protected final HashMap<NodeId, NetconfConnectorDTO> activeConnectors = new HashMap<>();

    protected AbstractNetconfTopology(final String topologyId, final NetconfClientDispatcher clientDispatcher,
                                      final BindingAwareBroker bindingAwareBroker, final Broker domBroker,
                                      final EventExecutor eventExecutor, final ScheduledThreadPool keepaliveExecutor,
                                      final ThreadPool processingExecutor, final SchemaRepositoryProvider schemaRepositoryProvider) {
        this.topologyId = topologyId;
        this.clientDispatcher = clientDispatcher;
        this.bindingAwareBroker = bindingAwareBroker;
        this.domBroker = domBroker;
        this.eventExecutor = eventExecutor;
        this.keepaliveExecutor = keepaliveExecutor;
        this.processingExecutor = processingExecutor;
        this.sharedSchemaRepository = schemaRepositoryProvider.getSharedSchemaRepository();
    }

    protected void registerToSal(BindingAwareProvider baProvider, Provider provider) {
        domBroker.registerProvider(provider);
        bindingAwareBroker.registerProvider(baProvider);
    }

    public void setSchemaRegistry(final SchemaSourceRegistry schemaRegistry) {
        this.schemaRegistry = schemaRegistry;
    }

    public void setSchemaContextFactory(final SchemaContextFactory schemaContextFactory) {
        this.schemaContextFactory = schemaContextFactory;
    }

    @Override
    public abstract void onSessionInitiated(ProviderContext session);

    @Override
    public String getTopologyId() {
        return topologyId;
    }

    @Override
    public DataBroker getDataBroker() {
        return dataBroker;
    }

    @Override
    public ListenableFuture<NetconfDeviceCapabilities> connectNode(NodeId nodeId, Node configNode) {
        LOG.info("Connecting RemoteDevice{{}} , with config {}", nodeId, configNode);
        return setupConnection(nodeId, configNode);
    }

    @Override
    public ListenableFuture<Void> disconnectNode(NodeId nodeId) {
        LOG.debug("Disconnecting RemoteDevice{{}}", nodeId.getValue());
        if (!activeConnectors.containsKey(nodeId)) {
            return Futures.immediateFailedFuture(new IllegalStateException("Unable to disconnect device that is not connected"));
        }

        // retrieve connection, and disconnect it
        final NetconfConnectorDTO connectorDTO = activeConnectors.remove(nodeId);
        connectorDTO.getCommunicator().close();
        connectorDTO.getFacade().close();
        return Futures.immediateFuture(null);
    }

    protected ListenableFuture<NetconfDeviceCapabilities> setupConnection(final NodeId nodeId,
                                                                        final Node configNode) {
        final NetconfNode netconfNode = configNode.getAugmentation(NetconfNode.class);

        Preconditions.checkNotNull(netconfNode.getHost());
        Preconditions.checkNotNull(netconfNode.getPort());
        Preconditions.checkNotNull(netconfNode.isTcpOnly());

        final NetconfConnectorDTO deviceCommunicatorDTO = createDeviceCommunicator(nodeId, netconfNode);
        final NetconfDeviceCommunicator deviceCommunicator = deviceCommunicatorDTO.getCommunicator();
        final NetconfClientSessionListener netconfClientSessionListener = deviceCommunicatorDTO.getSessionListener();
        final NetconfReconnectingClientConfiguration clientConfig = getClientConfig(netconfClientSessionListener, netconfNode);
        final ListenableFuture<NetconfDeviceCapabilities> future = deviceCommunicator.initializeRemoteConnection(clientDispatcher, clientConfig);

        activeConnectors.put(nodeId, deviceCommunicatorDTO);

        Futures.addCallback(future, new FutureCallback<NetconfDeviceCapabilities>() {
            @Override
            public void onSuccess(NetconfDeviceCapabilities result) {
                LOG.debug("Connector for : " + nodeId.getValue() + " started succesfully");
            }

            @Override
            public void onFailure(Throwable t) {
                LOG.error("Connector for : " + nodeId.getValue() + " failed");
                // remove this node from active connectors?
            }
        });

        return future;
    }

    protected NetconfConnectorDTO createDeviceCommunicator(final NodeId nodeId,
                                                         final NetconfNode node) {
        //setup default values since default value is not supported yet in mdsal
        // TODO remove this when mdsal starts supporting default values
        final Long defaultRequestTimeoutMillis = node.getDefaultRequestTimeoutMillis() == null ? DEFAULT_REQUEST_TIMEOUT_MILLIS : node.getDefaultRequestTimeoutMillis();
        final Long keepaliveDelay = node.getKeepaliveDelay() == null ? DEFAULT_KEEPALIVE_DELAY : node.getKeepaliveDelay();
        final Boolean reconnectOnChangedSchema = node.isReconnectOnChangedSchema() == null ? DEFAULT_RECONNECT_ON_CHANGED_SCHEMA : node.isReconnectOnChangedSchema();

        IpAddress ipAddress = node.getHost().getIpAddress();
        InetSocketAddress address = new InetSocketAddress(ipAddress.getIpv4Address() != null ?
                ipAddress.getIpv4Address().getValue() : ipAddress.getIpv6Address().getValue(),
                node.getPort().getValue());
        RemoteDeviceId remoteDeviceId = new RemoteDeviceId(nodeId.getValue(), address);

        RemoteDeviceHandler<NetconfSessionPreferences> salFacade =
                createSalFacade(remoteDeviceId, domBroker, bindingAwareBroker, defaultRequestTimeoutMillis);

        if (keepaliveDelay > 0) {
            LOG.warn("Adding keepalive facade, for device {}", nodeId);
            salFacade = new KeepaliveSalFacade(remoteDeviceId, salFacade, keepaliveExecutor.getExecutor(), keepaliveDelay);
        }

        final NetconfDevice.SchemaResourcesDTO schemaResourcesDTO = setupSchemaCacheDTO(nodeId, node);

        final NetconfDevice device = new NetconfDevice(schemaResourcesDTO, remoteDeviceId, salFacade,
                processingExecutor.getExecutor(), reconnectOnChangedSchema);

        return new NetconfConnectorDTO(new NetconfDeviceCommunicator(remoteDeviceId, device), salFacade);
    }

    protected NetconfDevice.SchemaResourcesDTO setupSchemaCacheDTO(final NodeId nodeId, final NetconfNode node) {
        // Setup information related to the SchemaRegistry, SchemaResourceFactory, etc.
        NetconfDevice.SchemaResourcesDTO schemaResourcesDTO = null;
        final String moduleSchemaCacheDirectory = node.getSchemaCacheDirectory();
        // Only checks to ensure the String is not empty or null;  further checks related to directory accessibility and file permissions
        // are handled during the FilesystemScehamSourceCache initialization.
        if (!Strings.isNullOrEmpty(moduleSchemaCacheDirectory)) {
            // If a custom schema cache directory is specified, create the backing DTO; otherwise, the SchemaRegistry and
            // SchemaContextFactory remain the default values.
            if (!moduleSchemaCacheDirectory.equals(DEFAULT_CACHE_DIRECTORY)) {
                // Multiple modules may be created at once;  synchronize to avoid issues with data consistency among threads.
                synchronized(schemaResourcesDTOs) {
                    // Look for the cached DTO to reuse SchemaRegistry and SchemaContextFactory variables if they already exist
                    final NetconfDevice.SchemaResourcesDTO dto =
                            schemaResourcesDTOs.get(moduleSchemaCacheDirectory);
                    if (dto == null) {
                        schemaResourcesDTO = createSchemaResourcesDTO(moduleSchemaCacheDirectory, nodeId.getValue());
                        schemaRegistry.registerSchemaSourceListener(
                                TextToASTTransformer.create((SchemaRepository) schemaRegistry, schemaRegistry));
                        schemaResourcesDTOs.put(moduleSchemaCacheDirectory, schemaResourcesDTO);
                    } else {
                        setSchemaContextFactory(dto.getSchemaContextFactory());
                        setSchemaRegistry(dto.getSchemaRegistry());
                        schemaResourcesDTO = dto;
                    }
                }
                LOG.info("Netconf connector for device {} will use schema cache directory {} instead of {}",
                        nodeId.getValue(), moduleSchemaCacheDirectory, DEFAULT_CACHE_DIRECTORY);
            }
        } else {
            LOG.warn("schema-cache-directory for {} is null or empty;  using the default {}",
                    nodeId.getValue(), QUALIFIED_DEFAULT_CACHE_DIRECTORY);
        }

        if (schemaResourcesDTO == null) {
            schemaResourcesDTO = new NetconfDevice.SchemaResourcesDTO(schemaRegistry, schemaContextFactory,
                    new NetconfStateSchemas.NetconfStateSchemasResolverImpl());
        }

        return schemaResourcesDTO;
    }

    /**
     * Creates the backing Schema classes for a particular directory.
     *
     * @param moduleSchemaCacheDirectory The string directory relative to "cache"
     * @return A DTO containing the Schema classes for the Netconf mount.
     */
    private NetconfDevice.SchemaResourcesDTO createSchemaResourcesDTO(final String moduleSchemaCacheDirectory,
            final String instanceName) {

        final SharedSchemaRepository repository = new SharedSchemaRepository(instanceName);
        final SchemaContextFactory schemaContextFactory
                = repository.createSchemaContextFactory(SchemaSourceFilter.ALWAYS_ACCEPT);
        setSchemaRegistry(repository);
        setSchemaContextFactory(schemaContextFactory);
        final FilesystemSchemaSourceCache<YangTextSchemaSource> deviceCache =
                createDeviceFilesystemCache(moduleSchemaCacheDirectory);
        repository.registerSchemaSourceListener(deviceCache);
        return new NetconfDevice.SchemaResourcesDTO(repository, schemaContextFactory,
                new NetconfStateSchemas.NetconfStateSchemasResolverImpl());
    }

    /**
     * Creates a <code>FilesystemSchemaSourceCache</code> for the custom schema cache directory.
     *
     * @param schemaCacheDirectory The custom cache directory relative to "cache"
     * @return A <code>FilesystemSchemaSourceCache</code> for the custom schema cache directory
     */
    private FilesystemSchemaSourceCache<YangTextSchemaSource> createDeviceFilesystemCache(final String schemaCacheDirectory) {
        final String relativeSchemaCacheDirectory = CACHE_DIRECTORY + File.separator + schemaCacheDirectory;
        return new FilesystemSchemaSourceCache<>(schemaRegistry, YangTextSchemaSource.class, new File(relativeSchemaCacheDirectory));
    }

    public NetconfReconnectingClientConfiguration getClientConfig(final NetconfClientSessionListener listener, NetconfNode node) {

        //setup default values since default value is not supported yet in mdsal
        // TODO remove this when mdsal starts supporting default values
        final long clientConnectionTimeoutMillis = node.getConnectionTimeoutMillis() == null ? DEFAULT_CONNECTION_TIMEOUT_MILLIS : node.getConnectionTimeoutMillis();
        final long maxConnectionAttempts = node.getMaxConnectionAttempts() == null ? DEFAULT_MAX_CONNECTION_ATTEMPTS : node.getMaxConnectionAttempts();
        final int betweenAttemptsTimeoutMillis = node.getBetweenAttemptsTimeoutMillis() == null ? DEFAULT_BETWEEN_ATTEMPTS_TIMEOUT_MILLIS : node.getBetweenAttemptsTimeoutMillis();
        final BigDecimal sleepFactor = node.getSleepFactor() == null ? DEFAULT_SLEEP_FACTOR : node.getSleepFactor();

        final InetSocketAddress socketAddress = getSocketAddress(node.getHost(), node.getPort().getValue());

        final ReconnectStrategyFactory sf = new TimedReconnectStrategyFactory(eventExecutor,
                maxConnectionAttempts, betweenAttemptsTimeoutMillis, sleepFactor);
        final ReconnectStrategy strategy = sf.createReconnectStrategy();

        final AuthenticationHandler authHandler;
        final Credentials credentials = node.getCredentials();
        if (credentials instanceof org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPassword) {
            authHandler = new LoginPassword(
                    ((org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPassword) credentials).getUsername(),
                    ((org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPassword) credentials).getPassword());
        } else {
            throw new IllegalStateException("Only login/password authentification is supported");
        }

        return NetconfReconnectingClientConfigurationBuilder.create()
                .withAddress(socketAddress)
                .withConnectionTimeoutMillis(clientConnectionTimeoutMillis)
                .withReconnectStrategy(strategy)
                .withAuthHandler(authHandler)
                .withProtocol(node.isTcpOnly() ?
                        NetconfClientConfiguration.NetconfClientProtocol.TCP :
                        NetconfClientConfiguration.NetconfClientProtocol.SSH)
                .withConnectStrategyFactory(sf)
                .withSessionListener(listener)
                .build();
    }

    protected abstract RemoteDeviceHandler<NetconfSessionPreferences> createSalFacade(final RemoteDeviceId id, final Broker domBroker, final BindingAwareBroker bindingBroker, long defaultRequestTimeoutMillis);

    @Override
    public abstract ConnectionStatusListenerRegistration registerConnectionStatusListener(NodeId node, RemoteDeviceHandler<NetconfSessionPreferences> listener);

    @Override
    public void onSessionInitiated(ProviderSession session) {
        mountPointService = session.getService(DOMMountPointService.class);
    }

    @Override
    public Collection<ProviderFunctionality> getProviderFunctionality() {
        return Collections.emptySet();
    }

    //TODO this needs to be an util method, since netconf clustering uses this aswell
    /**
     * Determines the Netconf Node Node ID, given the node's instance
     * identifier.
     *
     * @param pathArgument Node's path arument
     * @return     NodeId for the node
     */
    protected NodeId getNodeId(final PathArgument pathArgument) {
        if (pathArgument instanceof InstanceIdentifier.IdentifiableItem<?, ?>) {

            final Identifier key = ((InstanceIdentifier.IdentifiableItem) pathArgument).getKey();
            if(key instanceof NodeKey) {
                return ((NodeKey) key).getNodeId();
            }
        }
        throw new IllegalStateException("Unable to create NodeId from: " + pathArgument);
    }

    protected static InstanceIdentifier<Topology> createTopologyId(final String topologyId) {
        final InstanceIdentifier<NetworkTopology> networkTopology = InstanceIdentifier.create(NetworkTopology.class);
        return networkTopology.child(Topology.class, new TopologyKey(new TopologyId(topologyId)));
    }

    private InetSocketAddress getSocketAddress(final Host host, int port) {
        if(host.getDomainName() != null) {
            return new InetSocketAddress(host.getDomainName().getValue(), port);
        } else {
            final IpAddress ipAddress = host.getIpAddress();
            final String ip = ipAddress.getIpv4Address() != null ? ipAddress.getIpv4Address().getValue() : ipAddress.getIpv6Address().getValue();
            return new InetSocketAddress(ip, port);
        }
    }

    private static final class TimedReconnectStrategyFactory implements ReconnectStrategyFactory {
        private final Long connectionAttempts;
        private final EventExecutor executor;
        private final double sleepFactor;
        private final int minSleep;

        TimedReconnectStrategyFactory(final EventExecutor executor, final Long maxConnectionAttempts, final int minSleep, final BigDecimal sleepFactor) {
            if (maxConnectionAttempts != null && maxConnectionAttempts > 0) {
                connectionAttempts = maxConnectionAttempts;
            } else {
                connectionAttempts = null;
            }

            this.sleepFactor = sleepFactor.doubleValue();
            this.executor = executor;
            this.minSleep = minSleep;
        }

        @Override
        public ReconnectStrategy createReconnectStrategy() {
            final Long maxSleep = null;
            final Long deadline = null;

            return new TimedReconnectStrategy(executor, minSleep,
                    minSleep, sleepFactor, maxSleep, connectionAttempts, deadline);
        }
    }

    protected static class NetconfConnectorDTO {

        private final NetconfDeviceCommunicator communicator;
        private final RemoteDeviceHandler<NetconfSessionPreferences> facade;

        public NetconfConnectorDTO(final NetconfDeviceCommunicator communicator, final RemoteDeviceHandler<NetconfSessionPreferences> facade) {
            this.communicator = communicator;
            this.facade = facade;
        }

        public NetconfDeviceCommunicator getCommunicator() {
            return communicator;
        }

        public RemoteDeviceHandler<NetconfSessionPreferences> getFacade() {
            return facade;
        }

        public NetconfClientSessionListener getSessionListener() {
            return communicator;
        }
    }

}
