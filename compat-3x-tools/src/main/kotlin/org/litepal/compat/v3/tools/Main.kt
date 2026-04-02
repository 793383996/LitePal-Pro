package org.litepal.compat.v3.tools

private val mapping = mapOf(
    "LitePal.countAsync" to "LitePal.count + coroutine/executor",
    "LitePal.averageAsync" to "LitePal.average + coroutine/executor",
    "LitePal.maxAsync" to "LitePal.max + coroutine/executor",
    "LitePal.minAsync" to "LitePal.min + coroutine/executor",
    "LitePal.sumAsync" to "LitePal.sum + coroutine/executor",
    "LitePal.findAsync" to "LitePal.find + coroutine/executor",
    "LitePal.findFirstAsync" to "LitePal.findFirst + coroutine/executor",
    "LitePal.findLastAsync" to "LitePal.findLast + coroutine/executor",
    "LitePal.findAllAsync" to "LitePal.findAll + coroutine/executor",
    "LitePal.deleteAsync" to "LitePal.delete + coroutine/executor",
    "LitePal.deleteAllAsync" to "LitePal.deleteAll + coroutine/executor",
    "LitePal.updateAsync" to "LitePal.update + coroutine/executor",
    "LitePal.updateAllAsync" to "LitePal.updateAll + coroutine/executor",
    "LitePal.saveAllAsync" to "LitePal.saveAll + coroutine/executor",
    "LitePalSupport.saveAsync" to "LitePalSupport.save + coroutine/executor",
    "LitePalSupport.saveOrUpdateAsync" to "LitePalSupport.saveOrUpdate + coroutine/executor",
    "LitePalSupport.deleteAsync" to "LitePalSupport.delete + coroutine/executor",
    "LitePalSupport.updateAsync" to "LitePalSupport.update + coroutine/executor",
    "LitePalSupport.updateAllAsync" to "LitePalSupport.updateAll + coroutine/executor"
)

@Deprecated(
    message = "compat-3x-tools is in maintenance mode and intended for legacy migration assistance only."
)
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("LitePal 3.x async API migration map")
        mapping.forEach { (from, to) ->
            println("$from -> $to")
        }
        return
    }
    args.forEach { symbol ->
        val replacement = mapping[symbol]
        if (replacement == null) {
            println("$symbol -> (no predefined mapping)")
        } else {
            println("$symbol -> $replacement")
        }
    }
}
