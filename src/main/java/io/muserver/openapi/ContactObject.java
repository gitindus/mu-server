package io.muserver.openapi;

import java.io.IOException;
import java.io.Writer;
import java.net.URI;

import static io.muserver.openapi.Jsonizer.append;

public class ContactObject implements JsonWriter {
    public final String name;
    public final URI url;
    public final String email;

    public ContactObject(String name, URI url, String email) {
        this.name = name;
        this.url = url;
        this.email = email;
    }

    @Override
    public void writeJson(Writer writer) throws IOException {
        writer.write("{");
        boolean isFirst = true;
        isFirst = !append(writer, "name", name, isFirst);
        isFirst = !append(writer, "url", url, isFirst);
        isFirst = !append(writer, "email", email, isFirst);
        writer.write("}");
    }
}