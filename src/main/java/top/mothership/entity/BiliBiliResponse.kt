package top.mothership.entity

data class BiliBiliResponse<T>(
    val code: Int = 0,
    val msg: String? = null,
    val data: T? = null
)
