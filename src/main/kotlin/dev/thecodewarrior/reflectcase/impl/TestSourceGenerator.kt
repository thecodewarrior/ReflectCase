package dev.thecodewarrior.reflectcase.impl

public class TestSourceGenerator {
    private val javaSources = mutableMapOf<String, String>()

    /**
     * Imports to be added to all files. Set these _before_ adding the files using [add]. Defaults to a set of generally
     * useful or commonly used types.
     */
    public val globalImports: MutableList<String> = mutableListOf(
        "java.util.*",
        "java.lang.annotation.ElementType",
        "java.lang.annotation.Target",
        "java.lang.annotation.Retention",
        "java.lang.annotation.RetentionPolicy",
        "dev.thecodewarrior.reflectcase.TestSourceNopException"
    )
}