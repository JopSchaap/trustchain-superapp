package nl.tudelft.trustchain.app

import android.content.Context
import android.content.Intent
import nl.tudelft.trustchain.foc.ExecutionActivity

class FOCAppDefinition(
    icon: Int,
    appName: String,
    color: Int,
    val fileName: String,
    disableImageTint: Boolean = false
) : AppDefinition(icon, appName, color, ExecutionActivity::class.java, disableImageTint) {
    override fun getIntent(context: Context): Intent {
        val intent = super.getIntent(context)
        intent.putExtra("fileName", "${context.cacheDir}/${fileName.split("/").last()}")
        return intent
    }
}
