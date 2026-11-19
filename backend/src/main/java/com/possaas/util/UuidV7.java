package com.possaas.util;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;

import java.util.UUID;

/** Generador centralizado de UUID v7 (ordenables por tiempo, ideales para índices B-Tree). */
public final class UuidV7 {

    private static final TimeBasedEpochGenerator GENERATOR = Generators.timeBasedEpochGenerator();

    private UuidV7() {
    }

    public static UUID generate() {
        return GENERATOR.generate();
    }
}
