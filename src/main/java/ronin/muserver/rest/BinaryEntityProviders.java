package ronin.muserver.rest;

import ronin.muserver.Mutils;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.List;

import static java.util.Arrays.asList;
import static ronin.muserver.rest.EntityProviders.requestHasContent;

class BinaryEntityProviders {

    static final List<MessageBodyReader> binaryEntityReaders = asList(
        new InputStreamReader(),
        new ByteArrayReaderWriter(),
        new FileReaderWriter()
    );
    static final List<MessageBodyWriter> binaryEntityWriters = asList(
        new StreamingOutputWriter(),
        new ByteArrayReaderWriter(),
        new FileReaderWriter()
    );

    @Produces("*/*")
    @Consumes("*/*")
    private static class ByteArrayReaderWriter implements MessageBodyReader<byte[]>, MessageBodyWriter<byte[]> {
        public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
            return type.isArray() && type.getComponentType().equals(byte.class);
        }
        public byte[] readFrom(Class<byte[]> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException, WebApplicationException {
            if (!requestHasContent(httpHeaders)) {
                return new byte[0];
            }
            return Mutils.toByteArray(entityStream, 2048);
        }
        public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
            return type.isArray() && type.getComponentType().equals(byte.class);
        }
        public long getSize(byte[] bytes, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
            return bytes.length;
        }
        public void writeTo(byte[] bytes, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
            entityStream.write(bytes);
        }
    }

    @Produces("*/*")
    private static class StreamingOutputWriter implements MessageBodyWriter<StreamingOutput> {
        public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
            return StreamingOutput.class.isAssignableFrom(type);
        }
        public void writeTo(StreamingOutput streamingOutput, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
            streamingOutput.write(entityStream);
        }
    }


    @Consumes("*/*")
    private static class InputStreamReader implements MessageBodyReader<InputStream> {
        public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
            return InputStream.class.isAssignableFrom(type);
        }
        public InputStream readFrom(Class<InputStream> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException, WebApplicationException {
            return entityStream;
        }
    }

    @Produces("*/*")
    @Consumes("*/*")
    private static class FileReaderWriter implements MessageBodyReader<File>, MessageBodyWriter<File> {

        @Override
        public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
            return File.class.isAssignableFrom(type);
        }

        @Override
        public File readFrom(Class<File> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException, WebApplicationException {
            File temp = Files.createTempFile("MuServer", "tmp").toFile();
            temp.deleteOnExit();
            try (FileOutputStream fileWriter = new FileOutputStream(temp)) {
                Mutils.copy(entityStream, fileWriter, 8192);
            }
            return temp;
        }

        @Override
        public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
            return File.class.isAssignableFrom(type);
        }

        @Override
        public long getSize(File file, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
            return file.length();
        }

        @Override
        public void writeTo(File file, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
            try (FileInputStream fileInputStream = new FileInputStream(file)) {
                Mutils.copy(fileInputStream, entityStream, 8192);
            }
        }
    }

}
