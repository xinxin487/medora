package com.medora.knowledge.service;

/** Parent context supplements, but never replaces, the child evidence citation. */
public class ParentContextExpander {
    public record Evidence(Long citedChildId, String context) { }
    public Evidence expand(Long childId, String childContent, String parentContent, int maxCharacters) {
        int budget = Math.max(0, maxCharacters); String combined = (childContent == null ? "" : childContent) + "\n" + (parentContent == null ? "" : parentContent);
        return new Evidence(childId, combined.length() <= budget ? combined : combined.substring(0, budget));
    }
}
