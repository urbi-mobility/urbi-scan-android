package co.urbi.android.urbiscan.nfc

import android.app.Application
import org.spongycastle.jce.provider.BouncyCastleProvider
import java.security.Security

open class NFCApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }
}
