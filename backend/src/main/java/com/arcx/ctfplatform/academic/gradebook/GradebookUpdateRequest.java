package com.arcx.ctfplatform.academic.gradebook;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;
import java.util.Map;

@JsonDeserialize(using = GradebookUpdateRequest.Deserializer.class)
public record GradebookUpdateRequest(
        Integer pairNumber,
        Map<Integer, Double> labScores,
        Map<Integer, Boolean> labAccepted,
        Double kt1,
        Double kt2,
        Double kt3,
        Double kt4,
        String token,
        Integer examScore,
        Boolean clearExamScore
) {

    public GradebookUpdateRequest(
            Integer pairNumber,
            Map<Integer, Double> labScores,
            Map<Integer, Boolean> labAccepted,
            Double kt1,
            Double kt2,
            Double kt3,
            Double kt4,
            String token,
            Integer examScore
    ) {
        this(pairNumber, labScores, labAccepted, kt1, kt2, kt3, kt4, token, examScore, null);
    }

    static final class Deserializer extends JsonDeserializer<GradebookUpdateRequest> {
        @Override
        public GradebookUpdateRequest deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            ObjectCodec codec = p.getCodec();
            JsonNode node = codec.readTree(p);
            if (node == null || !node.isObject()) {
                throw ctxt.wrongTokenException(
                        p,
                        GradebookUpdateRequest.class,
                        JsonToken.START_OBJECT,
                        "Expected JSON object"
                );
            }

            Integer pairNumber = readField(node, "pairNumber", Integer.class, ctxt);
            Map<Integer, Double> labScores = readMapField(node, "labScores", Integer.class, Double.class, ctxt);
            Map<Integer, Boolean> labAccepted = readMapField(node, "labAccepted", Integer.class, Boolean.class, ctxt);
            Double kt1 = readField(node, "kt1", Double.class, ctxt);
            Double kt2 = readField(node, "kt2", Double.class, ctxt);
            Double kt3 = readField(node, "kt3", Double.class, ctxt);
            Double kt4 = readField(node, "kt4", Double.class, ctxt);
            String token = readField(node, "token", String.class, ctxt);
            Integer examScore = readField(node, "examScore", Integer.class, ctxt);
            Boolean clearExamScore = readField(node, "clearExamScore", Boolean.class, ctxt);

            if (clearExamScore == null && node.has("examScore") && node.get("examScore").isNull()) {
                clearExamScore = Boolean.TRUE;
            }

            return new GradebookUpdateRequest(
                    pairNumber,
                    labScores,
                    labAccepted,
                    kt1,
                    kt2,
                    kt3,
                    kt4,
                    token,
                    examScore,
                    clearExamScore
            );
        }

        private <T> T readField(JsonNode node, String fieldName, Class<T> type, DeserializationContext ctxt)
                throws IOException {
            JsonNode field = node.get(fieldName);
            if (field == null || field.isNull()) {
                return null;
            }
            return ctxt.readTreeAsValue(field, type);
        }

        @SuppressWarnings("unchecked")
        private <K, V> Map<K, V> readMapField(
                JsonNode node,
                String fieldName,
                Class<K> keyType,
                Class<V> valueType,
                DeserializationContext ctxt
        ) throws IOException {
            JsonNode field = node.get(fieldName);
            if (field == null || field.isNull()) {
                return null;
            }
            JavaType mapType = ctxt.getTypeFactory().constructMapType(Map.class, keyType, valueType);
            return (Map<K, V>) ctxt.readTreeAsValue(field, mapType);
        }
    }
}
