# Skill: Hubitat Hot-Path Copy Hygiene

**Confidence:** high
**Source:** gemstone-lights.groovy v0.4.15

## Problem

A convenient deep-copy helper like `JsonSlurper().parseText(JsonOutput.toJson(source))` is easy to write, but on Hubitat it is expensive on hot paths. If the driver calls it for every queued request, refresh payload, or cached pattern mutation, the hub spends CPU and GC time serializing data it already has in memory.

## Rule

If the real shape is a tree of `Map` / `List` containers holding scalar values, clone the containers directly and reuse the scalars.

- `Map` → new map, recursively clone each value
- `List` → new list, recursively clone each item
- Strings / numbers / booleans / null → reuse as-is

Reserve JSON round-trips for actual wire-format work, not internal copy hygiene.

```groovy
private Map cloneMap(Map source) {
    if (!source) {
        return [:]
    }

    Map copy = [:]
    source.each { key, value ->
        copy[key] = cloneValue(value)
    }
    return copy
}

private Object cloneValue(Object value) {
    if (value instanceof Map) {
        return cloneMap(value as Map)
    }
    if (value instanceof List) {
        return (value as List).collect { item -> cloneValue(item) }
    }
    return value
}
```

## When this applies

Use this for Hubitat driver data structures like:
- cached pattern maps with nested `colors` lists
- queued HTTP request maps with nested `body`, `query`, or `followUp` maps
- callback data copied before async dispatch

## Why it is safe

The isolation goal is about mutable containers. A fresh `Map` / `List` tree prevents later mutations from leaking back into `state` or queued work items. Reusing scalar leaves is fine because they are not mutated in place.

## Anti-pattern

```groovy
private Map cloneMap(Map source) {
    return new JsonSlurper().parseText(JsonOutput.toJson(source)) as Map
}
```

That pattern pays a serialization tax on every copy and should be treated as a last resort for shapes you truly cannot clone structurally.
