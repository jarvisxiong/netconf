/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.api;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * This service generates swagger (See <a
 * href="https://helloreverb.com/developers/swagger"
 * >https://helloreverb.com/developers/swagger</a>) compliant documentation for
 * RESTCONF APIs. The output of this is used by embedded Swagger UI.
 */
@Path("/")
public interface ApiDocService {

    /**
     * Generates index document for Swagger UI. This document lists out all
     * modules with link to get APIs for each module. The API for each module is
     * served by <code> getDocByModule()</code> method.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRootDoc(@Context javax.ws.rs.core.UriInfo uriInfo);

    /**
     * Generates Swagger compliant document listing APIs for module.
     */
    @GET
    @Path("/{module}({revision})")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDocByModule(@PathParam("module") String module,
            @PathParam("revision") String revision, @Context javax.ws.rs.core.UriInfo uriInfo);

    /**
     * Redirects to embedded swagger ui.
     */
    @GET
    @Path("/ui")
    @Produces(MediaType.TEXT_HTML)
    public Response getApiExplorer(@Context javax.ws.rs.core.UriInfo uriInfo);

    /**
     * Generates index document for Swagger UI. This document lists out all
     * modules with link to get APIs for each module. The API for each module is
     * served by <code> getDocByModule()</code> method.
     */
    @GET
    @Path("/mounts")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getListOfMounts(@Context javax.ws.rs.core.UriInfo uriInfo);

    @GET
    @Path("/mounts/{instance}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMountRootDoc(@PathParam("instance") String instanceNum,
            @Context javax.ws.rs.core.UriInfo uriInfo);

    /**
     * Generates Swagger compliant document listing APIs for module.
     */
    @GET
    @Path("/mounts/{instance}/{module}({revision})")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMountDocByModule(@PathParam("instance") String instanceNum,
            @PathParam("module") String module, @PathParam("revision") String revision,
            @Context javax.ws.rs.core.UriInfo uriInfo);

}
