/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.util;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.net.URI;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.notifications.NetconfNotification;
import org.opendaylight.netconf.sal.connect.util.MessageCounter;
import org.opendaylight.netconf.util.NetconfUtil;
import org.opendaylight.netconf.util.messages.NetconfMessageUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.edit.config.input.EditContent;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.CreateSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfCapabilityChange;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcError.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.ModifyAction;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.AnyXmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.impl.schema.SchemaOrderedNormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.NormalizedNodeAttrBuilder;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class NetconfMessageTransformUtil {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfMessageTransformUtil.class);

    public static final String MESSAGE_ID_PREFIX = "m";
    public static final String MESSAGE_ID_ATTR = "message-id";

    public static final QName CREATE_SUBSCRIPTION_RPC_QNAME = QName.create(CreateSubscriptionInput.QNAME, "create-subscription").intern();
    private static final String SUBTREE = "subtree";

    // Blank document used for creation of new DOM nodes
    private static final Document BLANK_DOCUMENT = XmlUtil.newDocument();
    public static final String EVENT_TIME = "eventTime";

    private NetconfMessageTransformUtil() {}

    public static final QName IETF_NETCONF_MONITORING = QName.create(NetconfState.QNAME, "ietf-netconf-monitoring").intern();
    public static final QName GET_DATA_QNAME = QName.create(IETF_NETCONF_MONITORING, "data").intern();
    public static final QName GET_SCHEMA_QNAME = QName.create(IETF_NETCONF_MONITORING, "get-schema").intern();
    public static final QName IETF_NETCONF_MONITORING_SCHEMA_FORMAT = QName.create(IETF_NETCONF_MONITORING, "format").intern();
    public static final QName IETF_NETCONF_MONITORING_SCHEMA_LOCATION = QName.create(IETF_NETCONF_MONITORING, "location").intern();
    public static final QName IETF_NETCONF_MONITORING_SCHEMA_IDENTIFIER = QName.create(IETF_NETCONF_MONITORING, "identifier").intern();
    public static final QName IETF_NETCONF_MONITORING_SCHEMA_VERSION = QName.create(IETF_NETCONF_MONITORING, "version").intern();
    public static final QName IETF_NETCONF_MONITORING_SCHEMA_NAMESPACE = QName.create(IETF_NETCONF_MONITORING, "namespace").intern();

    public static final QName IETF_NETCONF_NOTIFICATIONS = QName.create(NetconfCapabilityChange.QNAME, "ietf-netconf-notifications").intern();

    public static final QName NETCONF_QNAME = QName.create("urn:ietf:params:xml:ns:netconf:base:1.0", "2011-06-01", "netconf").intern();
    public static final URI NETCONF_URI = NETCONF_QNAME.getNamespace();

    public static final QName NETCONF_DATA_QNAME = QName.create(NETCONF_QNAME, "data").intern();
    public static final QName NETCONF_RPC_REPLY_QNAME = QName.create(NETCONF_QNAME, "rpc-reply").intern();
    public static final QName NETCONF_OK_QNAME = QName.create(NETCONF_QNAME, "ok").intern();
    public static final QName NETCONF_ERROR_OPTION_QNAME = QName.create(NETCONF_QNAME, "error-option").intern();
    public static final QName NETCONF_RUNNING_QNAME = QName.create(NETCONF_QNAME, "running").intern();
    public static final QName NETCONF_SOURCE_QNAME = QName.create(NETCONF_QNAME, "source").intern();
    public static final QName NETCONF_CANDIDATE_QNAME = QName.create(NETCONF_QNAME, "candidate").intern();
    public static final QName NETCONF_TARGET_QNAME = QName.create(NETCONF_QNAME, "target").intern();
    public static final QName NETCONF_CONFIG_QNAME = QName.create(NETCONF_QNAME, "config").intern();
    public static final QName NETCONF_COMMIT_QNAME = QName.create(NETCONF_QNAME, "commit").intern();
    public static final QName NETCONF_VALIDATE_QNAME = QName.create(NETCONF_QNAME, "validate").intern();
    public static final QName NETCONF_COPY_CONFIG_QNAME = QName.create(NETCONF_QNAME, "copy-config").intern();
    public static final QName NETCONF_OPERATION_QNAME = QName.create(NETCONF_QNAME, "operation").intern();
    public static final QName NETCONF_DEFAULT_OPERATION_QNAME = QName.create(NETCONF_OPERATION_QNAME, "default-operation").intern();
    public static final QName NETCONF_EDIT_CONFIG_QNAME = QName.create(NETCONF_QNAME, "edit-config").intern();
    public static final QName NETCONF_GET_CONFIG_QNAME = QName.create(NETCONF_QNAME, "get-config");
    public static final QName NETCONF_DISCARD_CHANGES_QNAME = QName.create(NETCONF_QNAME, "discard-changes");
    public static final QName NETCONF_TYPE_QNAME = QName.create(NETCONF_QNAME, "type").intern();
    public static final QName NETCONF_FILTER_QNAME = QName.create(NETCONF_QNAME, "filter").intern();
    public static final QName NETCONF_GET_QNAME = QName.create(NETCONF_QNAME, "get").intern();
    public static final QName NETCONF_RPC_QNAME = QName.create(NETCONF_QNAME, "rpc").intern();

    public static final URI NETCONF_ROLLBACK_ON_ERROR_URI = URI
            .create("urn:ietf:params:netconf:capability:rollback-on-error:1.0");
    public static final String ROLLBACK_ON_ERROR_OPTION = "rollback-on-error";

    public static final URI NETCONF_CANDIDATE_URI = URI
            .create("urn:ietf:params:netconf:capability:candidate:1.0");

    public static final URI NETCONF_NOTIFICATONS_URI = URI
            .create("urn:ietf:params:netconf:capability:notification:1.0");

    public static final URI NETCONF_RUNNING_WRITABLE_URI = URI
            .create("urn:ietf:params:netconf:capability:writable-running:1.0");

    public static final QName NETCONF_LOCK_QNAME = QName.create(NETCONF_QNAME, "lock").intern();
    public static final QName NETCONF_UNLOCK_QNAME = QName.create(NETCONF_QNAME, "unlock").intern();

    // Discard changes message
    public static final ContainerNode DISCARD_CHANGES_RPC_CONTENT =
            Builders.containerBuilder().withNodeIdentifier(new NodeIdentifier(NETCONF_DISCARD_CHANGES_QNAME)).build();

    // Commit changes message
    public static final ContainerNode COMMIT_RPC_CONTENT =
            Builders.containerBuilder().withNodeIdentifier(new NodeIdentifier(NETCONF_COMMIT_QNAME)).build();

    // Get message
    public static final ContainerNode GET_RPC_CONTENT =
            Builders.containerBuilder().withNodeIdentifier(new NodeIdentifier(NETCONF_GET_QNAME)).build();

    // Create-subscription changes message
    public static final ContainerNode CREATE_SUBSCRIPTION_RPC_CONTENT =
            Builders.containerBuilder().withNodeIdentifier(new NodeIdentifier(CREATE_SUBSCRIPTION_RPC_QNAME)).build();

    public static final DataContainerChild<?, ?> EMPTY_FILTER;

    static {
        final NormalizedNodeAttrBuilder<NodeIdentifier, DOMSource, AnyXmlNode> anyXmlBuilder = Builders.anyXmlBuilder().withNodeIdentifier(toId(NETCONF_FILTER_QNAME));
        anyXmlBuilder.withAttributes(Collections.singletonMap(NETCONF_TYPE_QNAME, SUBTREE));

        final Element element = XmlUtil.createElement(BLANK_DOCUMENT, NETCONF_FILTER_QNAME.getLocalName(), Optional.of(NETCONF_FILTER_QNAME.getNamespace().toString()));
        element.setAttributeNS(NETCONF_FILTER_QNAME.getNamespace().toString(), NETCONF_TYPE_QNAME.getLocalName(), "subtree");

        anyXmlBuilder.withValue(new DOMSource(element));

        EMPTY_FILTER = anyXmlBuilder.build();
    }

    public static DataContainerChild<?, ?> toFilterStructure(final YangInstanceIdentifier identifier, final SchemaContext ctx) {
        final NormalizedNodeAttrBuilder<NodeIdentifier, DOMSource, AnyXmlNode> anyXmlBuilder = Builders.anyXmlBuilder().withNodeIdentifier(toId(NETCONF_FILTER_QNAME));
        anyXmlBuilder.withAttributes(Collections.singletonMap(NETCONF_TYPE_QNAME, SUBTREE));

        final NormalizedNode<?, ?> filterContent = ImmutableNodes.fromInstanceId(ctx, identifier);

        final Element element = XmlUtil.createElement(BLANK_DOCUMENT, NETCONF_FILTER_QNAME.getLocalName(), Optional.of(NETCONF_FILTER_QNAME.getNamespace().toString()));
        element.setAttributeNS(NETCONF_FILTER_QNAME.getNamespace().toString(), NETCONF_TYPE_QNAME.getLocalName(), "subtree");

        try {
            NetconfUtil.writeNormalizedNode(filterContent, new DOMResult(element), SchemaPath.ROOT, ctx);
        } catch (IOException | XMLStreamException e) {
            throw new IllegalStateException("Unable to serialize filter element for path " + identifier, e);
        }
        anyXmlBuilder.withValue(new DOMSource(element));

        return anyXmlBuilder.build();
    }

    public static void checkValidReply(final NetconfMessage input, final NetconfMessage output)
            throws NetconfDocumentedException {
        final String inputMsgId = input.getDocument().getDocumentElement().getAttribute(MESSAGE_ID_ATTR);
        final String outputMsgId = output.getDocument().getDocumentElement().getAttribute(MESSAGE_ID_ATTR);

        if(inputMsgId.equals(outputMsgId) == false) {
            final Map<String,String> errorInfo = ImmutableMap.<String,String>builder()
                    .put( "actual-message-id", outputMsgId )
                    .put( "expected-message-id", inputMsgId )
                    .build();

            throw new NetconfDocumentedException( "Response message contained unknown \"message-id\"",
                    null, NetconfDocumentedException.ErrorType.PROTOCOL,
                    NetconfDocumentedException.ErrorTag.BAD_ATTRIBUTE,
                    NetconfDocumentedException.ErrorSeverity.ERROR, errorInfo);
        }
    }

    public static void checkSuccessReply(final NetconfMessage output) throws NetconfDocumentedException {
        if(NetconfMessageUtil.isErrorMessage(output)) {
            throw NetconfDocumentedException.fromXMLDocument(output.getDocument());
        }
    }

    public static RpcError toRpcError( final NetconfDocumentedException ex ) {
        final StringBuilder infoBuilder = new StringBuilder();
        final Map<String, String> errorInfo = ex.getErrorInfo();
        if(errorInfo != null) {
            for( final Entry<String,String> e: errorInfo.entrySet() ) {
                infoBuilder.append( '<' ).append( e.getKey() ).append( '>' ).append( e.getValue() )
                .append( "</" ).append( e.getKey() ).append( '>' );

            }
        }

        final ErrorSeverity severity = toRpcErrorSeverity( ex.getErrorSeverity() );
        return severity == ErrorSeverity.ERROR ?
                RpcResultBuilder.newError(
                        toRpcErrorType( ex.getErrorType() ), ex.getErrorTag().getTagValue(),
                        ex.getLocalizedMessage(), null, infoBuilder.toString(), ex.getCause() ) :
                            RpcResultBuilder.newWarning(
                                    toRpcErrorType( ex.getErrorType() ), ex.getErrorTag().getTagValue(),
                                    ex.getLocalizedMessage(), null, infoBuilder.toString(), ex.getCause() );
    }

    private static ErrorSeverity toRpcErrorSeverity( final NetconfDocumentedException.ErrorSeverity severity ) {
        switch (severity) {
            case WARNING:
                return RpcError.ErrorSeverity.WARNING;
            default:
                return RpcError.ErrorSeverity.ERROR;
        }
    }

    private static RpcError.ErrorType toRpcErrorType(final NetconfDocumentedException.ErrorType type) {
        switch (type) {
            case PROTOCOL:
            return RpcError.ErrorType.PROTOCOL;
            case RPC:
            return RpcError.ErrorType.RPC;
            case TRANSPORT:
            return RpcError.ErrorType.TRANSPORT;
        default:
            return RpcError.ErrorType.APPLICATION;
        }
    }

    public static NodeIdentifier toId(final PathArgument qname) {
        return toId(qname.getNodeType());
    }

    public static NodeIdentifier toId(final QName nodeType) {
        return new NodeIdentifier(nodeType);
    }

    public static Element getDataSubtree(final Document doc) {
        return (Element) doc.getElementsByTagNameNS(NETCONF_URI.toString(), "data").item(0);
    }

    public static boolean isDataRetrievalOperation(final QName rpc) {
        return NETCONF_URI.equals(rpc.getNamespace())
                && (NETCONF_GET_CONFIG_QNAME.getLocalName().equals(rpc.getLocalName())
                || NETCONF_GET_QNAME.getLocalName().equals(rpc.getLocalName()));
    }

    public static ContainerSchemaNode createSchemaForDataRead(final SchemaContext schemaContext) {
        return new NodeContainerProxy(NETCONF_DATA_QNAME, schemaContext.getChildNodes());
    }

    public static ContainerSchemaNode createSchemaForNotification(final NotificationDefinition next) {
        return new NodeContainerProxy(next.getQName(), next.getChildNodes(), next.getAvailableAugmentations());
    }

    public static ContainerNode wrap(final QName name, final DataContainerChild<?, ?>... node) {
        return Builders.containerBuilder().withNodeIdentifier(toId(name)).withValue(ImmutableList.copyOf(node)).build();
    }

    public static AnyXmlNode createEditConfigAnyxml(final SchemaContext ctx, final YangInstanceIdentifier dataPath,
                                                                     final Optional<ModifyAction> operation, final Optional<NormalizedNode<?, ?>> lastChildOverride) {
        final NormalizedNode<?, ?> configContent;

        if (dataPath.isEmpty()) {
            Preconditions.checkArgument(lastChildOverride.isPresent(), "Data has to be present when creating structure for top level element");
            Preconditions.checkArgument(lastChildOverride.get() instanceof DataContainerChild<?, ?>,
                    "Data has to be either container or a list node when creating structure for top level element, but was: %s", lastChildOverride.get());
            configContent = lastChildOverride.get();
        } else {
            final Entry<QName, ModifyAction> modifyOperation =
                    operation.isPresent() ? new AbstractMap.SimpleEntry<>(NETCONF_OPERATION_QNAME, operation.get()) : null;
            configContent = ImmutableNodes.fromInstanceId(ctx, dataPath, lastChildOverride, Optional.fromNullable(modifyOperation));
        }

        final Element element = XmlUtil.createElement(BLANK_DOCUMENT, NETCONF_CONFIG_QNAME.getLocalName(), Optional.of(NETCONF_CONFIG_QNAME.getNamespace().toString()));
        try {
            NetconfUtil.writeNormalizedNode(configContent, new DOMResult(element), SchemaPath.ROOT, ctx);
        } catch (IOException | XMLStreamException e) {
            throw new IllegalStateException("Unable to serialize edit config content element for path " + dataPath, e);
        }
        final DOMSource value = new DOMSource(element);

        return Builders.anyXmlBuilder().withNodeIdentifier(toId(NETCONF_CONFIG_QNAME)).withValue(value).build();
    }

    public static DataContainerChild<?, ?> createEditConfigStructure(final SchemaContext ctx, final YangInstanceIdentifier dataPath,
                                                                     final Optional<ModifyAction> operation, final Optional<NormalizedNode<?, ?>> lastChildOverride) {
        return Builders.choiceBuilder().withNodeIdentifier(toId(EditContent.QNAME))
                .withChild(createEditConfigAnyxml(ctx, dataPath, operation, lastChildOverride)).build();
    }

    public static SchemaPath toPath(final QName rpc) {
        return SchemaPath.create(true, rpc);
    }

    private static final ThreadLocal<SimpleDateFormat> EVENT_TIME_FORMAT = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {

            final SimpleDateFormat withMillis = new SimpleDateFormat(
                    NetconfNotification.RFC3339_DATE_FORMAT_WITH_MILLIS_BLUEPRINT);

            return new SimpleDateFormat(NetconfNotification.RFC3339_DATE_FORMAT_BLUEPRINT) {
                private static final long serialVersionUID = 1L;

                @Override public Date parse(final String source) throws ParseException {
                    try {
                        return super.parse(source);
                    } catch (final ParseException e) {
                        // In case of failure, try to parse with milliseconds
                        return withMillis.parse(source);
                    }
                }
            };
        }

        @Override
        public void set(final SimpleDateFormat value) {
            throw new UnsupportedOperationException();
        }
    };

    public static Map.Entry<Date, XmlElement> stripNotification(final NetconfMessage message) {
        final XmlElement xmlElement = XmlElement.fromDomDocument(message.getDocument());
        final List<XmlElement> childElements = xmlElement.getChildElements();
        Preconditions.checkArgument(childElements.size() == 2, "Unable to parse notification %s, unexpected format.\nExpected 2 childElements," +
                " actual childElements size is %s", message, childElements.size());

        final XmlElement eventTimeElement;
        final XmlElement notificationElement;

        if (childElements.get(0).getName().equals(EVENT_TIME)) {
            eventTimeElement = childElements.get(0);
            notificationElement = childElements.get(1);
        }
        else if(childElements.get(1).getName().equals(EVENT_TIME)) {
            eventTimeElement = childElements.get(1);
            notificationElement = childElements.get(0);
        } else {
            throw new IllegalArgumentException("Notification payload does not contain " + EVENT_TIME + " " + message);
        }

        try {
            return new AbstractMap.SimpleEntry<>(EVENT_TIME_FORMAT.get().parse(eventTimeElement.getTextContent()), notificationElement);
        } catch (final DocumentedException e) {
            throw new IllegalArgumentException("Notification payload does not contain " + EVENT_TIME + " " + message);
        } catch (final ParseException e) {
            LOG.warn("Unable to parse event time from {}. Setting time to {}", eventTimeElement, NetconfNotification.UNKNOWN_EVENT_TIME, e);
            return new AbstractMap.SimpleEntry<>(NetconfNotification.UNKNOWN_EVENT_TIME, notificationElement);
        }
    }

    public static DOMResult prepareDomResultForRpcRequest(final QName rpcQName, final MessageCounter counter) {
        final Document document = XmlUtil.newDocument();
        final Element rpcNS =
                document.createElementNS(NETCONF_RPC_QNAME.getNamespace().toString(), NETCONF_RPC_QNAME.getLocalName());
        // set msg id
        rpcNS.setAttribute(MESSAGE_ID_ATTR, counter.getNewMessageId(MESSAGE_ID_PREFIX));
        final Element elementNS = document.createElementNS(rpcQName.getNamespace().toString(), rpcQName.getLocalName());
        rpcNS.appendChild(elementNS);
        document.appendChild(rpcNS);
        return new DOMResult(elementNS);
    }

    public static void writeNormalizedRpc(final ContainerNode normalized, final DOMResult result,
                                          final SchemaPath schemaPath, final SchemaContext baseNetconfCtx) throws IOException, XMLStreamException {
        final XMLStreamWriter writer = NetconfUtil.XML_FACTORY.createXMLStreamWriter(result);
        try {
            try (final NormalizedNodeStreamWriter normalizedNodeStreamWriter =
                    XMLStreamNormalizedNodeStreamWriter.create(writer, baseNetconfCtx, schemaPath)) {
                try (final SchemaOrderedNormalizedNodeWriter normalizedNodeWriter =
                        new SchemaOrderedNormalizedNodeWriter(normalizedNodeStreamWriter, baseNetconfCtx, schemaPath)) {
                    final Collection<DataContainerChild<?, ?>> value = normalized.getValue();
                    normalizedNodeWriter.write(value);
                    normalizedNodeWriter.flush();
                }
            }
        } finally {
            try {
                writer.close();
            } catch (final Exception e) {
               LOG.warn("Unable to close resource properly", e);
            }
        }
    }
}
