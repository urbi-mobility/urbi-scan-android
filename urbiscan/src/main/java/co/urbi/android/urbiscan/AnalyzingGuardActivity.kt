package co.urbi.android.urbiscan

import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.atomic.AtomicBoolean

open class AnalyzingGuardActivity : AppCompatActivity() {

    private var isAnalyzing = AtomicBoolean(false)

    fun getAnalysisGuard(): Boolean {
        return isAnalyzing.get()
    }

    fun setAnalysisGuard(value: Boolean) {
        isAnalyzing.set(value)
    }
}
