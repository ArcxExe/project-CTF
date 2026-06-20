package com.arcx.ctfplatform.common.config;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public interface IMapping<F, T> {

    T mapping(F from);

    default List<T> mappingList(List<F> from) {
        if (from == null) {
            return Collections.emptyList();
        }

        return from.stream()
                .map(this::mapping)
                .collect(Collectors.toList());

    }

    default Optional<T> mappingOptional(Optional<F> fromOptional) {
        if (fromOptional == null || fromOptional.isEmpty()) {
            return Optional.empty();
        }
        return fromOptional.map(this::mapping);
    }

}
