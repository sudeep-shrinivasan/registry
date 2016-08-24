/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hortonworks.registries.schemaregistry.webservice;

import com.codahale.metrics.annotation.Timed;
import com.hortonworks.iotas.common.util.WSUtils;
import com.hortonworks.registries.schemaregistry.ISchemaRegistry;
import com.hortonworks.registries.schemaregistry.SchemaInfo;
import com.hortonworks.registries.schemaregistry.SchemaKey;
import com.hortonworks.registries.schemaregistry.SchemaMetadata;
import com.hortonworks.registries.schemaregistry.SchemaMetadataKey;
import com.hortonworks.registries.schemaregistry.SchemaNotFoundException;
import com.hortonworks.registries.schemaregistry.SerDesInfo;
import com.hortonworks.registries.schemaregistry.VersionedSchema;
import com.hortonworks.registries.schemaregistry.client.SchemaDetails;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Collection;

import static com.hortonworks.iotas.common.catalog.CatalogResponse.ResponseMessage.ENTITY_NOT_FOUND;
import static com.hortonworks.iotas.common.catalog.CatalogResponse.ResponseMessage.EXCEPTION;
import static com.hortonworks.iotas.common.catalog.CatalogResponse.ResponseMessage.SUCCESS;
import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.OK;

/**
 *
 */
@Path("/api/v1/catalog/schemaregistry")
@Produces(MediaType.APPLICATION_JSON)
public class SchemaRegistryCatalog {
    private static final Logger LOG = LoggerFactory.getLogger(SchemaRegistryCatalog.class);

    private final ISchemaRegistry schemaRegistry;

    public SchemaRegistryCatalog(ISchemaRegistry schemaRegistry) {
        this.schemaRegistry = schemaRegistry;
    }

    @GET
    @Path("/schemas")
    @Timed
    public Response listSchemas(@Context UriInfo uriInfo) {
        try {
            return WSUtils.respond(OK, SUCCESS, schemaRegistry.listAll());
        } catch (Exception ex) {
            return WSUtils.respond(INTERNAL_SERVER_ERROR, EXCEPTION, ex.getMessage());
        }
    }

    @POST
    @Path("/schemas")
    @Timed
    public Response addSchema(SchemaMetadata schemaMetadata) {
        Response response;
        try {
            schemaRegistry.addSchemaMetadata(schemaMetadata);
            response = WSUtils.respond(CREATED, SUCCESS, true);
        } catch (Exception ex) {
            LOG.error("Error encountered while adding schema", ex);
            response = WSUtils.respond(INTERNAL_SERVER_ERROR, EXCEPTION, ex.getMessage());
        }

        return response;
    }

    @POST
    @Path("/schemas/types/{type}/groups/{group}/names/{name}")
    @Timed
    public Response addSchema(@PathParam("type") String type,
                              @PathParam("group") String group,
                              @PathParam("name") String name,
                              SchemaDetails schemaDetails) {
        SchemaMetadataKey schemaMetadataKey = new SchemaMetadataKey(type, group, name);
        SchemaMetadata schemaMetadata = new SchemaMetadata(schemaMetadataKey,
                schemaDetails.getSchemaMetadataDescription(),
                schemaDetails.getCompatibility());
        Response response;
        try {
            Integer version = schemaRegistry.addSchema(schemaMetadata, schemaDetails.getVersionedSchema());
            response = WSUtils.respond(CREATED, SUCCESS, version);
        } catch (Exception ex) {
            LOG.error("Error encountered while adding schema", ex);
            response = WSUtils.respond(INTERNAL_SERVER_ERROR, EXCEPTION, ex.getMessage());
        }

        return response;
    }


    @GET
    @Path("/schemas/types/{type}/groups/{group}/names/{name}/versions/latest")
    @Timed
    public Response getSchemaInstance(@PathParam("type") String type,
                                      @PathParam("group") String group,
                                      @PathParam("name") String name) {
        SchemaMetadataKey schemaMetadataKey = new SchemaMetadataKey(type, group, name);

        Response response;
        try {
            SchemaMetadata schemaMetadataStorable = schemaRegistry.getSchemaMetadata(schemaMetadataKey);
            SchemaInfo schemaInfo = schemaRegistry.getLatestSchemaInfo(schemaMetadataStorable.getId());
            if (schemaInfo != null) {
                response = WSUtils.respond(OK, SUCCESS, schemaInfo);
            } else {
                response = WSUtils.respond(NOT_FOUND, ENTITY_NOT_FOUND, schemaMetadataKey.toString());
            }
        } catch (Exception ex) {
            response = WSUtils.respond(INTERNAL_SERVER_ERROR, EXCEPTION, ex.getMessage());
        }

        return response;

    }

    @GET
    @Path("/schemas/types/{type}/groups/{group}/names/{name}/versions")
    @Timed
    public Response getSchemaInstances(@PathParam("type") String type,
                                       @PathParam("group") String group,
                                       @PathParam("name") String name) {
        SchemaMetadataKey schemaMetadataKey = new SchemaMetadataKey(type, group, name);

        Response response;
        try {
            SchemaMetadata schemaMetadataStorable = schemaRegistry.getSchemaMetadata(schemaMetadataKey);
            Collection<SchemaInfo> schemaInfos = schemaRegistry.findAllVersions(schemaMetadataStorable.getId());
            if (schemaInfos != null) {
                response = WSUtils.respond(OK, SUCCESS, schemaInfos);
            } else {
                response = WSUtils.respond(NOT_FOUND, ENTITY_NOT_FOUND, schemaMetadataKey.toString());
            }
        } catch (Exception ex) {
            response = WSUtils.respond(INTERNAL_SERVER_ERROR, EXCEPTION, ex.getMessage());
        }

        return response;
    }

    @GET
    @Path("/schemas/types/{type}/groups/{group}/names/{name}/versions/{version}")
    @Timed
    public Response getSchemaInstance(@PathParam("type") String type,
                                      @PathParam("group") String group,
                                      @PathParam("name") String name,
                                      @PathParam("version") Integer version) {
        SchemaMetadataKey schemaMetadataKey = new SchemaMetadataKey(type, group, name);
        SchemaKey schemaKey = new SchemaKey(schemaMetadataKey, version);

        Response response;
        try {
            SchemaMetadata schemaMetadataStorable = schemaRegistry.getSchemaMetadata(schemaMetadataKey);
            SchemaInfo schemaInfo = schemaRegistry.getSchemaInfo(schemaMetadataStorable.getId(), version);
            response = WSUtils.respond(OK, SUCCESS, schemaInfo);
        } catch (SchemaNotFoundException e) {
            response = WSUtils.respond(NOT_FOUND, ENTITY_NOT_FOUND, schemaKey.toString());
        } catch (Exception ex) {
            response = WSUtils.respond(INTERNAL_SERVER_ERROR, EXCEPTION, ex.getMessage());
        }

        return response;
    }

    @GET
    @Path("/schemas/types/{type}/groups/{group}/names/{name}/compatibility")
    @Timed
    public Response isCompatibleWithSchema(@PathParam("type") String type,
                                           @PathParam("group") String group,
                                           @PathParam("name") String name,
                                           String schema) {
        SchemaMetadataKey schemaMetadataKey = new SchemaMetadataKey(type, group, name);
        Response response;
        try {
            SchemaMetadata schemaMetadataStorable = schemaRegistry.getSchemaMetadata(schemaMetadataKey);
            boolean compatible = schemaRegistry.isCompatible(schemaMetadataStorable.getId(), schema);
            response = WSUtils.respond(OK, SUCCESS, compatible);
        } catch (SchemaNotFoundException e) {
            response = WSUtils.respond(NOT_FOUND, ENTITY_NOT_FOUND, schemaMetadataKey.toString());
        } catch (Exception ex) {
            response = WSUtils.respond(INTERNAL_SERVER_ERROR, EXCEPTION, ex.getMessage());
        }

        return response;
    }

    @GET
    @Path("/schemas/types/{type}/groups/{group}/names/{name}/serializers")
    @Timed
    public Response getSerializers(@PathParam("type") String type,
                                   @PathParam("group") String group,
                                   @PathParam("name") String name,
                                   String schema) {
        SchemaMetadataKey schemaMetadataKey = new SchemaMetadataKey(type, group, name);
        Response response;
        try {
            SchemaMetadata schemaMetadataStorable = schemaRegistry.getSchemaMetadata(schemaMetadataKey);
            if (schemaMetadataStorable != null) {
                Collection<SerDesInfo> schemaSerializers = schemaRegistry.getSchemaSerializers(schemaMetadataStorable.getId());
                response = WSUtils.respond(OK, SUCCESS, schemaSerializers);
            } else {
                response = WSUtils.respond(NOT_FOUND, ENTITY_NOT_FOUND, schemaMetadataKey.toString());
            }
        } catch (Exception ex) {
            response = WSUtils.respond(INTERNAL_SERVER_ERROR, EXCEPTION, ex.getMessage());
        }

        return response;
    }

    @POST
    @Path("/schemas/{id}")
    @Timed
    public Response addVersionedSchema(@PathParam("id") Long schemaMetadataId, VersionedSchema versionedSchema) {
        Response response = null;
        try {
            SchemaMetadata schemaMetadataStorable = schemaRegistry.getSchemaMetadata(schemaMetadataId);
            if (schemaMetadataStorable != null) {
                SchemaMetadataKey schemaMetadataKey = schemaMetadataStorable.getSchemaMetadataKey();
                Integer version = schemaRegistry.addSchema(schemaMetadataKey, versionedSchema);

                response = WSUtils.respond(CREATED, SUCCESS, new SchemaKey(schemaMetadataKey, version));
            } else {
                response = WSUtils.respond(NOT_FOUND, ENTITY_NOT_FOUND, schemaMetadataId);
            }
        } catch (Exception ex) {
            LOG.error("Error encountered while adding schema", ex);
            response = WSUtils.respond(INTERNAL_SERVER_ERROR, EXCEPTION, ex.getMessage());
        }

        return response;
    }

    @GET
    @Path("/schemas/{id}/versions/latest")
    @Timed
    public Response getLatestSchema(@PathParam("id") Long schemaMetadataId) {
        Response response;
        try {
            SchemaInfo schemaInfoStorable = schemaRegistry.getLatestSchemaInfo(schemaMetadataId);
            if (schemaInfoStorable != null) {
                response = WSUtils.respond(OK, SUCCESS, schemaInfoStorable);
            } else {
                response = WSUtils.respond(NOT_FOUND, ENTITY_NOT_FOUND, schemaMetadataId.toString());
            }
        } catch (SchemaNotFoundException e) {
            response = WSUtils.respond(NOT_FOUND, ENTITY_NOT_FOUND, schemaMetadataId.toString());
        } catch (Exception ex) {
            response = WSUtils.respond(INTERNAL_SERVER_ERROR, EXCEPTION, ex.getMessage());
        }

        return response;
    }

    @GET
    @Path("/schemas/{id}/versions/{version}")
    @Timed
    public Response getVersionedSchema(@PathParam("id") Long schemaMetadataId,
                                       @PathParam("version") Integer version) {
        Response response;
        try {
            SchemaInfo schemaInfo = schemaRegistry.getSchemaInfo(schemaMetadataId, version);
            response = WSUtils.respond(OK, SUCCESS, schemaInfo);
        } catch (SchemaNotFoundException ex) {
            response = WSUtils.respond(NOT_FOUND, ENTITY_NOT_FOUND, schemaMetadataId.toString());
        } catch (Exception ex) {
            response = WSUtils.respond(INTERNAL_SERVER_ERROR, EXCEPTION, ex.getMessage());
        }

        return response;
    }

    @POST
    @Path("/schemas/{id}/compatible/versions/{version}")
    @Timed
    public Response isCompatible(String schema,
                                 @PathParam("id") Long schemaMetadataId,
                                 @PathParam("version") Integer version) {
        try {
            boolean compatible = schemaRegistry.isCompatible(schemaMetadataId, version, schema);
            return WSUtils.respond(OK, SUCCESS, compatible);
        } catch (SchemaNotFoundException e) {
            return WSUtils.respond(NOT_FOUND, ENTITY_NOT_FOUND, schemaMetadataId.toString());
        } catch (Exception ex) {
            return WSUtils.respond(INTERNAL_SERVER_ERROR, EXCEPTION, ex.getMessage());
        }
    }


    @POST
    @Path("/schemas/{id}/compatible/versions")
    @Timed
    public Response isCompatible(@PathParam("id") Long schemaMetadataId, String schema) {
        Response response;
        try {
            boolean compatible = schemaRegistry.isCompatible(schemaMetadataId, schema);
            response = WSUtils.respond(OK, SUCCESS, compatible);
        } catch (SchemaNotFoundException e) {
            response = WSUtils.respond(NOT_FOUND, ENTITY_NOT_FOUND, schemaMetadataId.toString());
        } catch (Exception ex) {
            response = WSUtils.respond(INTERNAL_SERVER_ERROR, EXCEPTION, ex.getMessage());
        }

        return response;
    }

    @GET
    @Produces({"application/octet-stream", "application/json"})
    @Path("/schemas/{id}/serializers/{serializerId}")
    @Timed
    public Response downloadSerializer(@PathParam("id") Long schemaMetadataId, @PathParam("serializerId") Long serializerId) {
        Response response;
        try {
            InputStream inputStream = schemaRegistry.downloadJar(serializerId);
            if (inputStream != null) {
                StreamingOutput streamOutput = WSUtils.wrapWithStreamingOutput(inputStream);
                response = Response.ok(streamOutput).build();
            } else {
                response = WSUtils.respond(NOT_FOUND, ENTITY_NOT_FOUND, schemaMetadataId.toString());
            }
        } catch (Exception ex) {
            response = WSUtils.respond(INTERNAL_SERVER_ERROR, EXCEPTION, ex.getMessage());
        }

        return response;
    }

    @Timed
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Path("/files")
    public Response uploadFile(@FormDataParam("file") final InputStream inputStream,
                               @FormDataParam("file") final FormDataContentDisposition contentDispositionHeader) {
        // todo we should be using file upload resources in iotas.
        Response response;
        try {
            LOG.info("Received contentDispositionHeader: [{}]", contentDispositionHeader);
            String uploadedFileId = schemaRegistry.uploadFile(inputStream);

            response = WSUtils.respond(OK, SUCCESS, uploadedFileId, null);
        } catch (Exception ex) {
            response = WSUtils.respond(INTERNAL_SERVER_ERROR, EXCEPTION, ex.getMessage());
        }

        return response;
    }

    @Timed
    @GET
    @Produces({"application/octet-stream", "application/json"})
    @Path("/files/download/{fileId}")
    public Response downloadFile(@PathParam("fileId") String fileId) {
        Response response;
        try {
            StreamingOutput streamOutput = WSUtils.wrapWithStreamingOutput(schemaRegistry.downloadFile(fileId));
            response = Response.ok(streamOutput).build();
            return response;
        } catch (FileNotFoundException e) {
            response = WSUtils.respond(NOT_FOUND, ENTITY_NOT_FOUND, fileId);
        } catch (Exception ex) {
            response = WSUtils.respond(INTERNAL_SERVER_ERROR, EXCEPTION, ex.getMessage());
        }

        return response;
    }

    @POST
    @Path("/serializers")
    @Timed
    public Response addSerializer(SerDesInfo schemaSerDesInfo) {
        Response response;
        try {
            Long serializerId = schemaRegistry.addSerDesInfo(schemaSerDesInfo);
            response = WSUtils.respond(OK, SUCCESS, serializerId);
        } catch (Exception ex) {
            response = WSUtils.respond(INTERNAL_SERVER_ERROR, EXCEPTION, ex.getMessage());
        }
        return response;
    }

    @GET
    @Path("/serializers/{id}")
    @Timed
    public Response getSerializer(@PathParam("id") Long serializerId) {
        Response response;
        try {
            SerDesInfo serializerInfo = schemaRegistry.getSerDesInfo(serializerId);
            response = WSUtils.respond(OK, SUCCESS, serializerInfo);
        } catch (Exception ex) {
            response = WSUtils.respond(INTERNAL_SERVER_ERROR, EXCEPTION, ex.getMessage());
        }
        return response;
    }

    @POST
    @Path("/deserializers")
    @Timed
    public Response addDeserializer(SerDesInfo schemaSerDesInfo) {
        Response response;
        try {
            Long serializerId = schemaRegistry.addSerDesInfo(schemaSerDesInfo);
            response = WSUtils.respond(OK, SUCCESS, serializerId);
        } catch (Exception ex) {
            response = WSUtils.respond(INTERNAL_SERVER_ERROR, EXCEPTION, ex.getMessage());
        }
        return response;
    }

    @GET
    @Path("/deserializers/{id}")
    @Timed
    public Response getDeserializer(@PathParam("id") Long deserializerId) {
        Response response;
        try {
            SerDesInfo serializerInfo = schemaRegistry.getSerDesInfo(deserializerId);
            response = WSUtils.respond(OK, SUCCESS, serializerInfo);
        } catch (Exception ex) {
            response = WSUtils.respond(INTERNAL_SERVER_ERROR, EXCEPTION, ex.getMessage());
        }
        return response;
    }

    @POST
    @Path("/schemas/mapping/{schemaMetadataId}/{serDesId}")
    @Timed
    public Response mapSerDes(@PathParam("schemaMetadataId") Long schemaMetadataId, @PathParam("serDesId") Long serDesId) {
        try {
            schemaRegistry.mapSerDesWithSchema(schemaMetadataId, serDesId);
            return WSUtils.respond(OK, SUCCESS, true);
        } catch (Exception ex) {
            return WSUtils.respond(INTERNAL_SERVER_ERROR, EXCEPTION, ex.getMessage());
        }
    }

    @POST
    @Path("/schemas/types/{type}/groups/{group}/names/{name}/mapping/{serDesId}")
    @Timed
    public Response mapSerDes(@PathParam("type") String type,
                              @PathParam("group") String group,
                              @PathParam("name") String name,
                              @PathParam("serDesId") Long serDesId) {
        SchemaMetadataKey schemaMetadataKey = new SchemaMetadataKey(type, group, name);
        Response response;
        try {
            SchemaMetadata schemaMetadataStorable = schemaRegistry.getSchemaMetadata(schemaMetadataKey);
            schemaRegistry.mapSerDesWithSchema(schemaMetadataStorable.getId(), serDesId);
            response = WSUtils.respond(OK, SUCCESS, true);
        } catch (Exception ex) {
            response = WSUtils.respond(INTERNAL_SERVER_ERROR, EXCEPTION, ex.getMessage());
        }

        return response;
    }


    @GET
    @Path("/schemas/{id}/serializers/")
    @Timed
    public Response getSerializers(@PathParam("id") Long schemaMetadataId) {
        Response response;
        try {
            Collection<SerDesInfo> serializers = schemaRegistry.getSchemaSerializers(schemaMetadataId);

            if (serializers != null) {
                response = WSUtils.respond(OK, SUCCESS, serializers);
            } else {
                response = WSUtils.respond(NOT_FOUND, ENTITY_NOT_FOUND, schemaMetadataId.toString());
            }
        } catch (Exception ex) {
            response = WSUtils.respond(INTERNAL_SERVER_ERROR, EXCEPTION, ex.getMessage());
        }
        return response;
    }

    @GET
    @Path("/schemas/{id}/deserializers/")
    @Timed
    public Response getDeserializers(@PathParam("id") Long schemaMetadataId) {
        Response response;
        try {
            Collection<SerDesInfo> deserializers = schemaRegistry.getSchemaDeserializers(schemaMetadataId);

            if (deserializers != null) {
                response = WSUtils.respond(OK, SUCCESS, deserializers);
            } else {
                response = WSUtils.respond(NOT_FOUND, ENTITY_NOT_FOUND, schemaMetadataId.toString());
            }
        } catch (Exception ex) {
            response = WSUtils.respond(INTERNAL_SERVER_ERROR, EXCEPTION, ex.getMessage());
        }

        return response;
    }

}
