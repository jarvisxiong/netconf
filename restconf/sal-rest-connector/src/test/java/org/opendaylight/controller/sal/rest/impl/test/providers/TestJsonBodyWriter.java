/**
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.sal.rest.impl.test.providers;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;

import javax.ws.rs.core.MediaType;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.rest.impl.JsonNormalizedNodeBodyReader;
import org.opendaylight.netconf.sal.rest.impl.NormalizedNodeJsonBodyWriter;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * sal-rest-connector org.opendaylight.controller.sal.rest.impl.test.providers
 *
 *
 *
 * @author <a href="mailto:vdemcak@cisco.com">Vaclav Demcak</a>
 *
 *         Created: Mar 12, 2015
 */
public class TestJsonBodyWriter extends AbstractBodyReaderTest {

    private final JsonNormalizedNodeBodyReader jsonBodyReader;
    private final NormalizedNodeJsonBodyWriter jsonBodyWriter;
    private static SchemaContext schemaContext;

    public TestJsonBodyWriter() throws NoSuchFieldException, SecurityException {
        super();
        jsonBodyWriter = new NormalizedNodeJsonBodyWriter();
        jsonBodyReader = new JsonNormalizedNodeBodyReader();
    }

    @Override
    protected MediaType getMediaType() {
        return new MediaType(MediaType.APPLICATION_XML, null);
    }

    @BeforeClass
    public static void initialization() throws Exception {
        Collection<File> testFiles = TestRestconfUtils.loadFiles("/instanceidentifier/yang");
        testFiles.addAll(TestRestconfUtils.loadFiles("/modules"));
        testFiles.addAll(TestRestconfUtils.loadFiles("/invoke-rpc"));
        schemaContext = TestRestconfUtils.parseYangSources(testFiles);
        controllerContext.setSchemas(schemaContext);
    }

    @Test
    public void rpcModuleInputTest() throws Exception {
        final String uri = "invoke-rpc-module:rpc-test";
        mockBodyReader(uri, jsonBodyReader, true);
        final InputStream inputStream = TestJsonBodyWriter.class
                .getResourceAsStream("/invoke-rpc/json/rpc-output.json");
        final NormalizedNodeContext returnValue = jsonBodyReader.readFrom(null,
                null, null, mediaType, null, inputStream);
        final OutputStream output = new ByteArrayOutputStream();
        jsonBodyWriter.writeTo(returnValue, null, null, null, mediaType, null,
                output);
        assertTrue(output.toString().contains("lf-test"));
    }
}
