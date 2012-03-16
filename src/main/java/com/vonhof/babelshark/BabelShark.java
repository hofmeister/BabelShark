package com.vonhof.babelshark;

import com.vonhof.babelshark.exception.MappingException;
import com.vonhof.babelshark.impl.DefaultNodeMapper;
import com.vonhof.babelshark.node.SharkNode;
import com.vonhof.babelshark.node.SharkType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Singleton instance of the babelshark engine
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class BabelShark {

    private static final BabelShark instance = new BabelShark();

    private BabelShark() {
    }

    public static BabelShark getInstance() {
        return instance;
    }
    private NodeMapper mapper = new DefaultNodeMapper();
    private Map<String, SharkLanguage> languages = new HashMap<String, SharkLanguage>();
    private Map<String, ObjectReader> readers = new HashMap<String, ObjectReader>();
    private Map<String, ObjectWriter> writers = new HashMap<String, ObjectWriter>();

    public void register(SharkLanguage language) {
        final ObjectReader reader = language.getObjectReader();
        final ObjectWriter writer = language.getObjectWriter();

        for (String contentType : reader.getContentTypes()) {
            contentType = normalizeContentType(contentType);
            readers.put(contentType, reader);
        }
        writers.put(writer.getContentType(), writer);

        //Add language ids as content type - for convenience
        readers.put(language.getId(), reader);
        writers.put(language.getId(), writer);

        languages.put(language.getId(), language);
    }

    private ObjectReader getReader(String contentType) {
        contentType = normalizeContentType(contentType);
        return readers.get(contentType);
    }

    private ObjectWriter getWriter(String contentType) {
        contentType = normalizeContentType(contentType);
        return writers.get(contentType);
    }

    public <T> T read(Input input, SharkType<T, ?> type) throws MappingException, IOException {
        ObjectReader reader = getReader(input.getContentType());
        if (reader == null) {
            throw new MappingException(String.format("Unknown content type:", input.getContentType()));
        }
        SharkNode map = reader.read(input);
        return readAsValue(map, type);
    }

    public <T> T read(Input input, Class<T> clz) throws MappingException, IOException {
        return read(input, SharkType.get(clz));
    }

    public <T> T read(String raw, String type, Class<T> clz) throws MappingException, IOException {
        return read(new Input(raw, type), clz);
    }

    public <T> T readAsValue(SharkNode node, SharkType<T, ?> type) throws MappingException {
        return mapper.readAs(node, type);
    }

    public <T> T readAsValue(SharkNode node, Class<T> clz) throws MappingException {
        return mapper.readAs(node, clz);
    }

    public <T> List<T> readAsList(SharkNode node, SharkType<List, T> type) throws MappingException {
        return mapper.readAs(node, type);
    }

    public <T> List<T> readAsList(SharkNode node, Class<T> clz) throws MappingException {
        return mapper.readAs(node, SharkType.forCollection(List.class, clz));
    }

    public <T> Map<String, T> readAsMap(SharkNode node, SharkType<Map, T> type) throws MappingException {
        return mapper.readAs(node, type);
    }

    public <T> Map<String, T> readAsMap(SharkNode node, Class<T> clz) throws MappingException {
        return mapper.readAs(node, SharkType.forMap(Map.class, clz));
    }

    public void write(Output output, Object value) throws MappingException, IOException {
        ObjectWriter writer = getWriter(output.getContentType());
        if (writer == null) {
            throw new MappingException(String.format("Unknown content type:", output.getContentType()));
        }
        SharkNode map = mapper.toNode(value);
        writer.write(output, map);
    }

    private static String normalizeContentType(String contentType) {
        return contentType.toLowerCase().trim();
    }

    public String writeToString(Object value, String contentType) throws MappingException, IOException {
        return new String(writeToByteArray(value, contentType));
    }

    public byte[] writeToByteArray(Object value, String contentType) throws MappingException, IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        Output output = new Output(stream, contentType);
        write(output, value);
        return stream.toByteArray();
    }
}
