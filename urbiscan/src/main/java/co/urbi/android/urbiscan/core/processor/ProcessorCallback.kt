package co.urbi.android.urbiscan.core.processor

interface ProcessorCallback<T> {
    fun scannedResults(result: T)
}
