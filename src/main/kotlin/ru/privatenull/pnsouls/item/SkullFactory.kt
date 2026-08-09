package ru.privatenull.pnsouls.item

import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object SkullFactory {
    private val textureUrl = Regex("https?://textures\\.minecraft\\.net/texture/[A-Za-z0-9]+")
    private val reported = ConcurrentHashMap.newKeySet<String>()

    fun create(texture: String): ItemStack {
        val head = ItemStack(Material.PLAYER_HEAD)
        val meta = head.itemMeta as? SkullMeta ?: return head
        if (texture.isBlank()) return head
        try {
            val payload = normalize(texture)
            val url = extractUrl(payload) ?: error("текстура не содержит корректный Minecraft URL")
            if (!applyModern(meta, url, payload)) applyLegacy(meta, payload)
            head.itemMeta = meta
        } catch (exception: Throwable) {
            if (reported.add(texture)) {
                Bukkit.getLogger().warning("pnSouls: не удалось применить Base64-текстуру: ${exception.message}")
            }
        }
        return head
    }

    private fun applyModern(meta: SkullMeta, url: URL, payload: String): Boolean = try {
        val profileType = Class.forName("org.bukkit.profile.PlayerProfile")
        val texturesType = Class.forName("org.bukkit.profile.PlayerTextures")
        val uuid = profileUuid(payload)
        val profile = Bukkit::class.java.getMethod("createPlayerProfile", UUID::class.java, String::class.java)
            .invoke(null, uuid, shortName(uuid))
        val textures = profileType.getMethod("getTextures").invoke(profile)
        texturesType.getMethod("setSkin", URL::class.java).invoke(textures, url)
        profileType.getMethod("setTextures", texturesType).invoke(profile, textures)
        SkullMeta::class.java.getMethod("setOwnerProfile", profileType).invoke(meta, profile)
        true
    } catch (_: Throwable) {
        false
    }

    private fun applyLegacy(meta: SkullMeta, payload: String) {
        val uuid = profileUuid(payload)
        val profile = GameProfile(uuid, shortName(uuid))
        profile.properties.put("textures", Property("textures", payload))
        var type: Class<*>? = meta.javaClass
        while (type != null) {
            try {
                type.getDeclaredField("profile").apply {
                    isAccessible = true
                    set(meta, profile)
                }
                return
            } catch (_: NoSuchFieldException) {
                type = type.superclass
            }
        }
        error("поле профиля головы не найдено")
    }

    private fun normalize(input: String): String {
        var value = input.trim()
        if (value.startsWith("base64:", true) || value.startsWith("base64-", true)) {
            value = value.substring(7).trim()
        }
        val matchedUrl = textureUrl.find(value)?.value
        val hash = value.takeIf { it.matches(Regex("[A-Fa-f0-9]{32,}")) }
        if (matchedUrl != null || hash != null) {
            val url = matchedUrl ?: "https://textures.minecraft.net/texture/$hash"
            val json = "{\"textures\":{\"SKIN\":{\"url\":\"$url\"}}}"
            return Base64.getEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8))
        }
        return value
    }

    private fun extractUrl(payload: String): URL? = runCatching {
        val json = String(Base64.getDecoder().decode(payload), StandardCharsets.UTF_8)
        textureUrl.find(json)?.value?.let(::URL)
    }.getOrNull()

    private fun profileUuid(payload: String) =
        UUID.nameUUIDFromBytes("pnSouls:$payload".toByteArray(StandardCharsets.UTF_8))

    private fun shortName(uuid: UUID) = "pn" + uuid.toString().replace("-", "").take(14)
}
