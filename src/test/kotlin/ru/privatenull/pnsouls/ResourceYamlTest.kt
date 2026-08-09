package ru.privatenull.pnsouls

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml

class ResourceYamlTest {
    @Test
    fun `plugin resources are valid yaml`() {
        for (name in listOf("plugin.yml", "config.yml", "messages.yml", "souls.yml")) {
            val stream = javaClass.classLoader.getResourceAsStream(name)
            assertNotNull(stream, "$name missing")
            stream!!.reader(Charsets.UTF_8).use { assertTrue(Yaml().load<Any>(it) is Map<*, *>) }
        }
    }

    @Test
    fun `three default souls expose three model items`() {
        val stream = javaClass.classLoader.getResourceAsStream("souls.yml")!!
        val root = stream.reader(Charsets.UTF_8).use { Yaml().load<Map<String, Any>>(it) }
        @Suppress("UNCHECKED_CAST")
        val souls = root["souls"] as Map<String, Map<String, Any>>
        assertEquals(
            setOf("doubler", "experienced", "harvester", "miner", "wanderer", "guardian", "phoenix", "ocean", "alchemist"),
            souls.keys,
        )
        for ((_, soul) in souls) {
            @Suppress("UNCHECKED_CAST")
            val model = soul["model"] as Map<String, Any>
            assertTrue(model.keys.containsAll(listOf("head", "chest", "hand")))
        }
    }
}
