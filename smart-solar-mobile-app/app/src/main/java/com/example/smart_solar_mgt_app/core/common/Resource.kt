package com.example.smart_solar_mgt_app.core.common

sealed class Resource<out T> {
    data object Loading : Resource<Nothing>()
    data class Success<T>(val data: T) : Resource<T>()
    data class Error(val message: String, val cause: Throwable? = null) : Resource<Nothing>()
}

sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Failure(val error: AppError) : AppResult<Nothing>()
}

sealed class AppError {
    data object NotFound : AppError()
    data object Unauthorized : AppError()
    data object InvalidStatusTransition : AppError()
    data class UniqueConstraintViolation(val field: String) : AppError()
    data class Unknown(val message: String, val cause: Throwable? = null) : AppError()
}
