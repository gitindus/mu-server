package io.muserver.rest;

import io.muserver.*;
import io.muserver.openapi.*;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static io.muserver.Mutils.notNull;
import static io.muserver.openapi.PathItemObjectBuilder.pathItemObject;
import static io.muserver.openapi.PathsObjectBuilder.pathsObject;
import static io.muserver.openapi.RequestBodyObjectBuilder.requestBodyObject;
import static io.muserver.openapi.ResponsesObjectBuilder.mergeResponses;
import static io.muserver.openapi.ServerObjectBuilder.serverObject;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

class OpenApiDocumentor implements MuHandler {
    private final Set<ResourceClass> roots;
    private final String openApiJsonUrl;
    private final OpenAPIObject openAPIObject;
    private final String openApiHtmlUrl;
    private final String openApiHtmlCss;
    private final CORSConfig corsConfig;

    OpenApiDocumentor(Set<ResourceClass> roots, String openApiJsonUrl, String openApiHtmlUrl, OpenAPIObject openAPIObject, String openApiHtmlCss, CORSConfig corsConfig) {
        notNull("openAPIObject", openAPIObject);
        this.corsConfig = corsConfig;
        this.roots = roots;
        this.openApiJsonUrl = openApiJsonUrl == null ? null : Mutils.trim(openApiJsonUrl, "/");
        this.openApiHtmlUrl = openApiHtmlUrl == null ? null : Mutils.trim(openApiHtmlUrl, "/");
        this.openAPIObject = openAPIObject;
        this.openApiHtmlCss = openApiHtmlCss;
    }

    @Override
    public boolean handle(MuRequest request, MuResponse response) throws Exception {
        String relativePath = Mutils.trim(request.relativePath(), "/");

        if (request.method() != Method.GET || (!relativePath.equals(openApiJsonUrl) && !relativePath.equals(openApiHtmlUrl))) {
            return false;
        }

        List<TagObject> tags = new ArrayList<>();

        Map<String, PathItemObject> pathItems = new HashMap<>();
        for (ResourceClass root : roots) {

            tags.add(root.tag);


            for (ResourceMethod method : root.resourceMethods) {
                String path = getPathWithoutRegex(root, method);

                Map<String, OperationObject> operations;
                if (pathItems.containsKey(path)) {
                    operations = pathItems.get(path).operations;
                } else {
                    operations = new HashMap<>();
                    PathItemObject pathItem = pathItemObject()
                        .withOperations(operations)
                        .build();
                    pathItems.put(path, pathItem);
                }
                List<ParameterObject> parameters = method.params.stream()
                    .filter(p -> p.source.openAPIIn != null && p instanceof ResourceMethodParam.RequestBasedParam)
                    .map(ResourceMethodParam.RequestBasedParam.class::cast)
                    .map(p -> p.createDocumentationBuilder().build())
                    .collect(toList());

                String opIdPath = getPathWithoutRegex(root, method).replace("{", "_").replace("}", "_");
                String opPath = Mutils.trim(opIdPath, "/").replace("/", "_");
                String opKey = method.httpMethod.name().toLowerCase();
                OperationObject existing = operations.get(opKey);
                if (existing == null) {
                    existing = method.createOperationBuilder()
                        .withOperationId(method.httpMethod.name() + "_" + opPath)
                        .withTags(singletonList(root.tag.name))
                        .withParameters(parameters)
                        .build();
                } else {
                    OperationObject curOO = method.createOperationBuilder().build();
                    List<ParameterObject> combinedParams = new ArrayList<>(existing.parameters);
                    combinedParams.addAll(parameters);

                    Map<String, MediaTypeObject> mergedContent = new HashMap<>();
                    if (existing.requestBody != null && existing.requestBody.content != null) {
                        mergedContent.putAll(existing.requestBody.content);
                    }
                    if (curOO.requestBody != null) {
                        mergedContent.putAll(curOO.requestBody.content);
                    }
                    OperationObjectBuilder operationObjectBuilder = OperationObjectBuilder.builderFrom(existing)
                        .withParameters(combinedParams)
                        .withResponses(mergeResponses(existing.responses, curOO.responses).build())
                        .withRequestBody(requestBodyObject()
                            .withRequired(existing.requestBody != null && existing.requestBody.required &&
                                curOO.requestBody != null && curOO.requestBody.required)
                            .withContent(mergedContent)
                            .build());
                    if (existing.summary == null && existing.description == null) {
                        operationObjectBuilder
                            .withSummary(curOO.summary)
                            .withDescription(curOO.description);
                    }
                    existing = operationObjectBuilder
                        .build();
                }
                operations.put(opKey, existing);
            }
        }


        OpenAPIObjectBuilder api = OpenAPIObjectBuilder.openAPIObject()
            .withInfo(openAPIObject.info)
            .withExternalDocs(openAPIObject.externalDocs)
            .withSecurity(openAPIObject.security)
            .withServers(
                singletonList(
                    serverObject()
                        .withUrl(Mutils.trim(request.uri().resolve(request.contextPath()).toString(), "/"))
                        .build())
            )
            .withPaths(pathsObject().withPathItemObjects(pathItems).build())
            .withTags(tags);

        OpenAPIObject builtApi = api.build();


        if (relativePath.equals(openApiJsonUrl)) {
            response.contentType(ContentTypes.APPLICATION_JSON);
            corsConfig.writeHeadersInternal(request, response, emptySet());
            response.headers().set("Access-Control-Allow-Methods", "GET");

            try (OutputStreamWriter osw = new OutputStreamWriter(response.outputStream(), StandardCharsets.UTF_8);
                 BufferedWriter writer = new BufferedWriter(osw, 8192)) {
                builtApi.writeJson(writer);
            }
        } else {
            response.contentType(ContentTypes.TEXT_HTML_UTF8);
            response.headers().set("X-UA-Compatible", "IE=edge");

            try (OutputStreamWriter osw = new OutputStreamWriter(response.outputStream(), StandardCharsets.UTF_8);
                 BufferedWriter writer = new BufferedWriter(osw, 8192)) {
                new HtmlDocumentor(writer, builtApi, openApiHtmlCss).writeHtml();
            }
        }

        return true;
    }

    static String getPathWithoutRegex(ResourceClass rc, ResourceMethod rm) {
        return "/" + Mutils.trim(
            Mutils.join(rc.pathPattern == null ? null : rc.pathPattern.pathWithoutRegex,
                "/", rm.pathPattern == null ? null : rm.pathPattern.pathWithoutRegex), "/");
    }

}
