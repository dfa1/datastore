package io.github.dfa1.datastore;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public record Symbol(String value) {

    @JsonCreator
    public Symbol {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("symbol must not be blank");
        if (value.length() > 10)
            throw new IllegalArgumentException("symbol too long: " + value);
        value = value.strip().toUpperCase();
    }

    @JsonValue
    public String value() { return value; }

    @Override
    public String toString() { return value; }
}
