package org.litepal.compat.v3.api

/**
 * API migration hints from LitePal 3.x to 4.x.
 */
@Deprecated(
    message = "compat-3x-api is in maintenance mode. Migrate to LitePal 4.x core APIs directly.",
    replaceWith = ReplaceWith("LitePal")
)
object Compat3xApiMapping {

    private val symbolMapping: Map<String, String> = mapOf(
        "LitePal.countAsync" to "LitePal.count + your coroutine/dispatcher",
        "LitePal.averageAsync" to "LitePal.average + your coroutine/dispatcher",
        "LitePal.maxAsync" to "LitePal.max + your coroutine/dispatcher",
        "LitePal.minAsync" to "LitePal.min + your coroutine/dispatcher",
        "LitePal.sumAsync" to "LitePal.sum + your coroutine/dispatcher",
        "LitePal.findAsync" to "LitePal.find + your coroutine/dispatcher",
        "LitePal.findFirstAsync" to "LitePal.findFirst + your coroutine/dispatcher",
        "LitePal.findLastAsync" to "LitePal.findLast + your coroutine/dispatcher",
        "LitePal.findAllAsync" to "LitePal.findAll + your coroutine/dispatcher",
        "LitePal.deleteAsync" to "LitePal.delete + your coroutine/dispatcher",
        "LitePal.deleteAllAsync" to "LitePal.deleteAll + your coroutine/dispatcher",
        "LitePal.updateAsync" to "LitePal.update + your coroutine/dispatcher",
        "LitePal.updateAllAsync" to "LitePal.updateAll + your coroutine/dispatcher",
        "LitePal.saveAllAsync" to "LitePal.saveAll + your coroutine/dispatcher",
        "LitePalSupport.saveAsync" to "LitePalSupport.save + your coroutine/dispatcher",
        "LitePalSupport.saveOrUpdateAsync" to "LitePalSupport.saveOrUpdate + your coroutine/dispatcher",
        "LitePalSupport.deleteAsync" to "LitePalSupport.delete + your coroutine/dispatcher",
        "LitePalSupport.updateAsync" to "LitePalSupport.update + your coroutine/dispatcher",
        "LitePalSupport.updateAllAsync" to "LitePalSupport.updateAll + your coroutine/dispatcher"
    )

    @JvmStatic
    fun replacementOf(symbol: String): String? = symbolMapping[symbol]

    @JvmStatic
    fun allMappings(): Map<String, String> = symbolMapping
}
