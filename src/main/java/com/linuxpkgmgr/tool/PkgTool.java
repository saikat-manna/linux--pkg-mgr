package com.linuxpkgmgr.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Drop-in replacement for {@link Tool} that enriches each tool method with an
 * explicit canonical {@link #name()} (stable across refactors) and an
 * {@link IntentRole} used by the context-management pipeline to detect intent
 * boundaries in the conversation history.
 *
 * <p>The annotation is meta-annotated with {@link Tool} so Spring AI discovers
 * it via {@code AnnotatedElementUtils}. The {@code name} and {@code description}
 * fields are bridged through {@code @AliasFor}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Tool
public @interface PkgTool {

    /** Canonical tool name — stable identifier used by the intent registry. */
    @AliasFor(annotation = Tool.class, attribute = "name")
    String name();

    /** Human-readable description forwarded to the LLM as tool documentation. */
    @AliasFor(annotation = Tool.class, attribute = "description")
    String description() default "";

    @AliasFor(annotation = Tool.class, attribute = "returnDirect")
    boolean returnDirect() default false;

    /** Role of this tool in the intent lifecycle. */
    IntentRole role() default IntentRole.NEUTRAL;

    /** If true, always included in every LLM call regardless of similarity score. */
    boolean anchor() default false;
}
