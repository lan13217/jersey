/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2013 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.jersey.tests.e2e.server;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.client.Entity;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.process.Inflector;
import org.glassfish.jersey.server.ExtendedResourceContext;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.server.model.ModelProcessor;
import org.glassfish.jersey.server.model.Resource;
import org.glassfish.jersey.server.model.ResourceMethod;
import org.glassfish.jersey.server.model.ResourceModel;
import org.glassfish.jersey.test.JerseyTest;

import org.junit.Test;

import com.google.common.collect.Lists;

import junit.framework.Assert;

/**
 * @author Miroslav Fuksa (miroslav.fuksa at oracle.com)
 *
 */
public class ModelProcessorTest extends JerseyTest {


    public static class ModelProcessorFeature implements Feature {

        @Override
        public boolean configure(FeatureContext context) {
            context.register(SimpleModelProcessor.class);
            return true;
        }

        private static class SimpleModelProcessor implements ModelProcessor {

            @Override
            public ResourceModel processResourceModel(ResourceModel resourceModel, Configuration configuration) {
                ResourceModel.Builder modelBuilder = new ResourceModel.Builder();
                final Resource modelResource = Resource.from(ModelResource.class);
                modelBuilder.addResource(modelResource);

                for (final Resource resource : resourceModel.getRootResources()) {
                    Resource newResource = enhanceResource(resource);
                    modelBuilder.addResource(newResource);
                }

                return modelBuilder.build();
            }

            private Resource enhanceResource(final Resource resource) {
                final Resource.Builder resBuilder = Resource.builder(resource);
                boolean optionsFound = false;
                for (ResourceMethod resourceMethod : resource.getResourceMethods()) {
                    if (resourceMethod.getHttpMethod().equals("OPTIONS")) {
                        optionsFound = true;
                    }
                }
                if (!optionsFound) {

                    resBuilder.addMethod("OPTIONS").produces(MediaType.TEXT_PLAIN_TYPE)
                            .handledBy(new Inflector<ContainerRequestContext, String>() {
                                @Override
                                public String apply(ContainerRequestContext containerRequestContext) {
                                    return resource.getPath();
                                }
                            });
                }


                final Inflector<ContainerRequestContext, Object> inflector = new Inflector<ContainerRequestContext, Object>() {

                    @Override
                    public Object apply(ContainerRequestContext requestContext) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("my-resource:");
                        final String path = resource.getPath();
                        sb.append(path == null ? "<no-path>" : path + ",");

                        for (ResourceMethod resourceMethod : sortResourceMethods(resource.getResourceMethods())) {
                            sb.append(resourceMethod.getHttpMethod() + "=" +
                                    "" + resourceMethod.getInvocable().getHandlingMethod().getName() + "|");
                        }
                        return sb.toString();
                    }
                };

                resBuilder.addChildResource("my-resource")
                        .addMethod("GET").produces
                        (MediaType.TEXT_PLAIN_TYPE)
                        .handledBy(inflector).build();

                return resBuilder.build();
            }


            @Override
            public Resource processSubResource(Resource subResource, Configuration configuration) {
                final Resource resource = enhanceResource(subResource);
                return resource;
            }
        }


        @Path("model")
        public static class ModelResource {
            @Context
            ExtendedResourceContext resourceContext;

            @GET
            public String get() {
                final ResourceModel resourceModel = resourceContext.getResourceModel();
                StringBuilder sb = new StringBuilder();
                for (Resource resource : resourceModel.getRootResources()) {
                    final String path = resource.getPath();
                    sb.append(path == null ? "<no-path>" : path).append("|");
                }

                return sb.toString();
            }
        }
    }

    private static List<ResourceMethod> sortResourceMethods(List<ResourceMethod> resourceMethods) {
        List<ResourceMethod> newList = Lists.newArrayList(resourceMethods);
        Collections.sort(newList, new Comparator<ResourceMethod>() {
            @Override
            public int compare(ResourceMethod o1, ResourceMethod o2) {
                return o1.getHttpMethod().compareTo(o2.getHttpMethod());
            }
        });
        return newList;
    }

    @Path("a")
    public static class ResourceA {
        @GET
        public String getFromA() {
            return "a-get";
        }

        @POST
        public String postFromA(String entity) {
            return "a-post";
        }

        @GET
        @Path("child")
        public String getChild() {
            return "a-child-get";
        }

        @Path("locator")
        public SubResource locatorFromA() {
            return new SubResource();
        }
    }

    public static class SubResource {
        @GET
        public String getFromSubResource() {
            return "sub-get";
        }
    }

    @Path("b")
    public static class ResourceB {
        @GET
        public String getFromB() {
            return "b-get";
        }

        @OPTIONS
        public String optionsFromB() {
            return "b-options";
        }

        @Path("locator")
        public SubResource locatorFromB() {
            return new SubResource();
        }
    }


    @Override
    protected Application configure() {
        final ResourceConfig resourceConfig = new ResourceConfig(ResourceA.class, ResourceB.class, ModelProcessorFeature.class);
        resourceConfig.setProperty(ServerProperties.FEATURE_DISABLE_WADL, true);
        return resourceConfig;
    }

    @Test
    public void testResourceAGet() {
        Response response = target("/a").request().get();
        Assert.assertEquals(200, response.getStatus());
        final String entity = response.readEntity(String.class);
        Assert.assertEquals(200, response.getStatus());
        Assert.assertEquals("a-get", entity);
    }

    @Test
    public void testResourceAPost() {
        Response response = target("/a").request().post(Entity.entity("post", MediaType.TEXT_PLAIN_TYPE));
        Assert.assertEquals(200, response.getStatus());
        final String entity = response.readEntity(String.class);
        Assert.assertEquals(200, response.getStatus());
        Assert.assertEquals("a-post", entity);
    }

    @Test
    public void testResourceAOptions() {
        Response response = target("/a").request(MediaType.TEXT_PLAIN_TYPE).options();
        Assert.assertEquals(200, response.getStatus());
        final String entity = response.readEntity(String.class);
        Assert.assertEquals(200, response.getStatus());
        Assert.assertEquals("a", entity);
    }

    @Test
    public void testResourceAChildGet() {
        Response response = target("/a/child").request().get();
        Assert.assertEquals(200, response.getStatus());
        final String entity = response.readEntity(String.class);
        Assert.assertEquals(200, response.getStatus());
        Assert.assertEquals("a-child-get", entity);
    }

    @Test
    public void testResourceALocatorGet() {
        Response response = target("/a/locator").request().get();
        Assert.assertEquals(200, response.getStatus());
        final String entity = response.readEntity(String.class);
        Assert.assertEquals(200, response.getStatus());
        Assert.assertEquals("sub-get", entity);
    }


    @Test
    public void testResourceALocatorOptions() {
        Response response = target("/a/locator").request().options();
        Assert.assertEquals(200, response.getStatus());
        final String entity = response.readEntity(String.class);
        Assert.assertEquals(200, response.getStatus());
        Assert.assertEquals("", entity);
    }


    @Test
    public void testResourceBGet() {
        Response response = target("/b").request().get();
        Assert.assertEquals(200, response.getStatus());
        final String entity = response.readEntity(String.class);
        Assert.assertEquals(200, response.getStatus());
        Assert.assertEquals("b-get", entity);
    }

    @Test
    public void testResourceBOptions() {
        Response response = target("/b").request().options();
        Assert.assertEquals(200, response.getStatus());
        final String entity = response.readEntity(String.class);
        Assert.assertEquals(200, response.getStatus());
        Assert.assertEquals("b-options", entity);
    }

    @Test
    public void testResourceBLocatorGet() {
        Response response = target("/b/locator").request().get();
        Assert.assertEquals(200, response.getStatus());
        final String entity = response.readEntity(String.class);
        Assert.assertEquals(200, response.getStatus());
        Assert.assertEquals("sub-get", entity);
    }


    @Test
    public void testResourceBLocatorOptions() {
        Response response = target("/b/locator").request().options();
        Assert.assertEquals(200, response.getStatus());
        final String entity = response.readEntity(String.class);
        Assert.assertEquals(200, response.getStatus());
        Assert.assertEquals("", entity);
    }

    @Test
    public void testResourceAMyResource() {
        Response response = target("/a/my-resource").request().get();
        Assert.assertEquals(200, response.getStatus());
        final String entity = response.readEntity(String.class);
        Assert.assertEquals(200, response.getStatus());
        Assert.assertEquals("my-resource:a,GET=getFromA|POST=postFromA|", entity);
    }

    @Test
    public void testResourceALocatorMyResource() {
        Response response = target("/a/locator/my-resource").request().get();
        Assert.assertEquals(200, response.getStatus());
        final String entity = response.readEntity(String.class);
        Assert.assertEquals(200, response.getStatus());
        Assert.assertEquals("my-resource:<no-path>GET=getFromSubResource|", entity);
    }

    @Test
    public void testResourceBMyResource() {
        Response response = target("/b/my-resource").request().get();
        Assert.assertEquals(200, response.getStatus());
        final String entity = response.readEntity(String.class);
        Assert.assertEquals(200, response.getStatus());
        Assert.assertEquals("my-resource:b,GET=getFromB|OPTIONS=optionsFromB|", entity);
    }

    @Test
    public void testInfo() {
        Response response = target("/model").request().get();
        Assert.assertEquals(200, response.getStatus());
        final String entity = response.readEntity(String.class);
        Assert.assertEquals(200, response.getStatus());
        Assert.assertEquals("a|b|model|", entity);
    }


}
