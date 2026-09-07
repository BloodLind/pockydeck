package dev.handheld.launcher.core.domain.model

@JvmInline
value class ItemId(val value: String) {
    init {
        require(value.isNotBlank()) { "Item IDs must not be blank" }
    }
}

/** A package/activity pair returned for the Android user running this launcher. */
data class CurrentUserAndroidComponentId(
    val packageName: String,
    val activityClassName: String,
) {
    init {
        require(packageName.isNotBlank() && '/' !in packageName) {
            "Package name must be non-blank and must not contain '/'"
        }
        require(activityClassName.isNotBlank() && '/' !in activityClassName) {
            "Activity class name must be non-blank and must not contain '/'"
        }
        require(!activityClassName.startsWith('.')) {
            "Activity class name must be fully qualified"
        }
    }

    val itemId: ItemId
        get() = ItemId("$ANDROID_ITEM_PREFIX$packageName/$activityClassName")

    companion object {
        private const val ANDROID_ITEM_PREFIX = "android:"

        fun fromItemId(itemId: ItemId): CurrentUserAndroidComponentId? {
            if (!itemId.value.startsWith(ANDROID_ITEM_PREFIX)) return null
            val encoded = itemId.value.removePrefix(ANDROID_ITEM_PREFIX)
            val separator = encoded.indexOf('/')
            if (separator <= 0 || separator == encoded.lastIndex) return null
            if (encoded.indexOf('/', separator + 1) >= 0) return null
            return runCatching {
                CurrentUserAndroidComponentId(
                    packageName = encoded.substring(0, separator),
                    activityClassName = encoded.substring(separator + 1),
                )
            }.getOrNull()
        }
    }
}

@JvmInline
value class CatalogSourceId(val value: String) {
    init {
        require(value.isNotBlank()) { "Catalog source IDs must not be blank" }
    }
}

@JvmInline
value class SystemActionId(val value: String) {
    init {
        require(value.isNotBlank()) { "System action IDs must not be blank" }
    }

    val itemId: ItemId
        get() = ItemId("system-action:$value")
}

@JvmInline
value class UserArtworkReference(val value: String) {
    init {
        require(value.isNotBlank()) { "Artwork references must not be blank" }
    }
}
