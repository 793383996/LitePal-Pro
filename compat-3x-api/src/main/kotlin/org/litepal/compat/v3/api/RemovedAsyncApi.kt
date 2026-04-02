package org.litepal.compat.v3.api

/**
 * Explicit exception used by migration layers when 3.x async APIs are invoked.
 */
@Deprecated(
    message = "compat-3x-api is in maintenance mode and will be removed in a future major version."
)
class RemovedAsyncApi(
    symbol: String
) : UnsupportedOperationException(
    "LitePal 4.0 removed $symbol. Use the sync API with your own coroutine/executor."
)
